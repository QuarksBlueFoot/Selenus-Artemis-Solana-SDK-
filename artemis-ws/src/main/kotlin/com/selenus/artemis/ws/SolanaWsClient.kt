package com.selenus.artemis.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

/**
 * Solana WebSocket client with:
 * - auto reconnect with jittered backoff
 * - deterministic resubscribe order
 * - subscription de-dupe by logical key
 * - subscription bundling to reduce request spam during startup
 * - optional HTTP fallback polling while disconnected
 * - backpressure-aware notification routing (sampling for non critical streams)
 * - Kotlin Flow event stream
 *
 * This module intentionally stays transport-focused and does not depend on the RPC module.
 */
class SolanaWsClient(
  private val url: String,
  private val okHttp: OkHttpClient = OkHttpClient(),
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  private val config: WsConfig = WsConfig(),
  private val fallback: WsFallback? = null,
  notificationPolicy: NotificationPolicy = NotificationPolicy()
) : Closeable {

  data class WsConfig(
    val pingIntervalMs: Long = 15_000,
    val minBackoffMs: Long = 500,
    val maxBackoffMs: Long = 15_000,
    val maxReconnectAttempts: Int = Int.MAX_VALUE,
    val eventBuffer: Int = 256,
    val bundleWindowMs: Long = 40
  )

  interface WsFallback {
    suspend fun poll(activeKeys: List<String>, emit: suspend (WsEvent) -> Unit)
  }

  private val idGen = AtomicInteger(1)
  private val sendMutex = Mutex()
  private val subMutex = Mutex()

  private var ws: WebSocket? = null
  private var closed = false
  private var reconnectJob: Job? = null
  private var heartbeatJob: Job? = null
  private var fallbackJob: Job? = null
  private var bundlerJob: Job? = null

  private var policy: NotificationPolicy = notificationPolicy

  private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = config.eventBuffer)
  val events: SharedFlow<WsEvent> = _events

  // request tracking
  private val pendingById = LinkedHashMap<Int, PendingRequest>()

  // deterministic ordering by key
  private val activeSubs: SortedMap<String, ActiveSub> = TreeMap()
  private val subIdToKey = LinkedHashMap<Long, String>()

  // bundling channel
  private val bundleCh = Channel<JsonObject>(capacity = Channel.UNLIMITED)

  // backpressure router
  private val router = NotificationRouter(
    scope = scope,
    emit = { ev -> _events.emit(ev) },
    policy = { policy }
  )

  /**
   * Subscription intent presets for common app patterns.
   */
  enum class IntentPreset {
    WALLET_ACTIVITY,
    PROGRAM_LOGS,
    SIGNATURE_CONFIRMATION,
    ACCOUNT_STREAM
  }

  fun setNotificationPolicy(newPolicy: NotificationPolicy) {
    policy = newPolicy
  }

  fun connect() {
    closed = false
    router.start()
    openSocket()
    startBundler()
    startHeartbeat()
  }

  override fun close() {
    closed = true
    reconnectJob?.cancel()
    heartbeatJob?.cancel()
    fallbackJob?.cancel()
    bundlerJob?.cancel()
    router.stop()
    ws?.close(1000, "client close")
    ws = null
  }

  suspend fun programLogsSubscribe(programId: String, commitment: String? = "confirmed"): SubscriptionHandle {
    val key = "logs:$programId:${commitment ?: "null"}"
    val params = buildJsonObject { put("mentions", programId) }
    val options = buildJsonObject { if (commitment != null) put("commitment", commitment) }
    return subscribeDedup(key, "logsSubscribe", listOf(params, options))
  }

  suspend fun accountSubscribe(pubkey: String, commitment: String? = "confirmed", encoding: String? = "base64"): SubscriptionHandle {
    val key = "acct:$pubkey:${commitment ?: "null"}:${encoding ?: "null"}"
    val opts = buildJsonObject {
      if (commitment != null) put("commitment", commitment)
      if (encoding != null) put("encoding", encoding)
    }
    return subscribeDedup(key, "accountSubscribe", listOf(pubkey, opts))
  }

  suspend fun programSubscribe(programId: String, commitment: String? = "confirmed", encoding: String? = "base64"): SubscriptionHandle {
    val key = "prog:$programId:${commitment ?: "null"}:${encoding ?: "null"}"
    val opts = buildJsonObject {
      if (commitment != null) put("commitment", commitment)
      if (encoding != null) put("encoding", encoding)
    }
    return subscribeDedup(key, "programSubscribe", listOf(programId, opts))
  }

  suspend fun signatureSubscribe(signature: String, commitment: String? = "confirmed"): SubscriptionHandle {
    val key = "sig:$signature:${commitment ?: "null"}"
    val opts = buildJsonObject { if (commitment != null) put("commitment", commitment) }
    return subscribeDedup(key, "signatureSubscribe", listOf(signature, opts))
  }

  suspend fun subscribePreset(preset: IntentPreset, arg: String): List<SubscriptionHandle> {
    return when (preset) {
      IntentPreset.WALLET_ACTIVITY -> listOf(programLogsSubscribe(arg))
      IntentPreset.PROGRAM_LOGS -> listOf(programLogsSubscribe(arg))
      IntentPreset.SIGNATURE_CONFIRMATION -> listOf(signatureSubscribe(arg))
      IntentPreset.ACCOUNT_STREAM -> listOf(accountSubscribe(arg))
    }
  }

  suspend fun unsubscribe(handle: SubscriptionHandle) {
    subMutex.withLock {
      val sub = activeSubs[handle.key] ?: return
      if (sub.serverSubId != null) {
        val reqId = idGen.getAndIncrement()
        val msg = buildRequest(reqId, sub.unsubscribeMethod, listOf(sub.serverSubId!!))
        send(msg)
      }
      activeSubs.remove(handle.key)
    }
  }

  private suspend fun subscribeDedup(key: String, method: String, params: List<Any>): SubscriptionHandle {
    subMutex.withLock {
      val existing = activeSubs[key]
      if (existing != null) return SubscriptionHandle(key) { unsubscribe(it) }

      val unsubscribeMethod = method.replace("Subscribe", "Unsubscribe")
      val reqId = idGen.getAndIncrement()
      val msg = buildRequest(reqId, method, params)

      pendingById[reqId] = PendingRequest(reqId, key, method, unsubscribeMethod, params)
      activeSubs[key] = ActiveSub(key, method, unsubscribeMethod, params, null)

      send(msg)
      return SubscriptionHandle(key) { unsubscribe(it) }
    }
  }

  private fun openSocket() {
    val req = Request.Builder().url(url).build()
    ws = okHttp.newWebSocket(req, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        scope.launch { _events.emit(WsEvent.Connected) }
        scope.launch { stopFallback() }
        scope.launch { resubscribeAllDeterministic() }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        scope.launch { handleMessage(text) }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scope.launch { _events.emit(WsEvent.Disconnected(t.message ?: "failure")) }
        scheduleReconnect()
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch { _events.emit(WsEvent.Disconnected(reason)) }
        scheduleReconnect()
      }
    })
  }

  private fun startHeartbeat() {
    if (heartbeatJob?.isActive == true) return
    heartbeatJob = scope.launch {
      while (!closed) {
        delay(config.pingIntervalMs)
        _events.tryEmit(WsEvent.Heartbeat(System.currentTimeMillis()))
      }
    }
  }

  private fun startBundler() {
    if (bundlerJob?.isActive == true) return
    bundlerJob = scope.launch {
      val queue = ArrayList<JsonObject>()
      while (!closed) {
        val first = bundleCh.receive()
        queue.add(first)
        val started = System.currentTimeMillis()

        while (true) {
          val elapsed = System.currentTimeMillis() - started
          val remain = config.bundleWindowMs - elapsed
          if (remain <= 0) break
          val next = withTimeoutOrNull(remain) { bundleCh.receive() }
          if (next == null) break
          queue.add(next)
        }

        for (msg in queue) sendNow(msg)
        queue.clear()
      }
    }
  }

  private fun scheduleReconnect() {
    if (closed) return
    if (reconnectJob?.isActive == true) return

    startFallback()

    reconnectJob = scope.launch {
      var attempt = 0
      while (!closed && attempt < config.maxReconnectAttempts) {
        attempt++
        val backoff = jitteredBackoff(attempt)
        _events.emit(WsEvent.Reconnecting(attempt, backoff))
        delay(backoff)
        try {
          openSocket()
          return@launch
        } catch (_: Throwable) {
          // continue loop
        }
      }
      if (!closed) _events.emit(WsEvent.GaveUp)
    }
  }

  private fun jitteredBackoff(attempt: Int): Long {
    val exp = config.minBackoffMs * (1L shl min(6, attempt))
    val capped = min(exp, config.maxBackoffMs)
    val jitter = Random.nextLong(0, min(250, capped / 3))
    return capped + jitter
  }

  private suspend fun resubscribeAllDeterministic() {
    subMutex.withLock {
      subIdToKey.clear()
      activeSubs.values.forEach { it.serverSubId = null }

      for (sub in activeSubs.values) {
        val reqId = idGen.getAndIncrement()
        val msg = buildRequest(reqId, sub.subscribeMethod, sub.params)
        pendingById[reqId] = PendingRequest(reqId, sub.key, sub.subscribeMethod, sub.unsubscribeMethod, sub.params)
        send(msg)
      }
    }
  }

  private suspend fun handleMessage(text: String) {
    val el = json.parseToJsonElement(text)
    if (el !is JsonObject) {
      _events.emit(WsEvent.Raw(text))
      return
    }

    val idEl = el["id"]
    if (idEl != null && idEl.jsonPrimitive.isString.not()) {
      val id = idEl.jsonPrimitive.int
      val pending = pendingById.remove(id)
      if (pending != null) {
        val result = el["result"]?.jsonPrimitive?.longOrNull
        if (result != null) {
          subMutex.withLock {
            val sub = activeSubs[pending.key]
            if (sub != null) {
              sub.serverSubId = result
              subIdToKey[result] = pending.key
            }
          }
          _events.emit(WsEvent.Subscribed(pending.key, result))
        } else {
          _events.emit(WsEvent.Error("subscribe failed", el))
        }
        return
      }
    }

    val method = el["method"]?.jsonPrimitive?.content
    if (method != null && method.endsWith("Notification")) {
      val params = el["params"]?.jsonObject
      if (params != null) {
        val subId = params["subscription"]?.jsonPrimitive?.longOrNull
        val result = params["result"]
        val key = if (subId != null) subMutex.withLock { subIdToKey[subId] } else null
        val n = NotificationRouter.RawNotif(
          key = key,
          subscriptionId = subId ?: -1,
          method = method,
          result = result
        )
        val ok = router.tryOffer(n)
        if (!ok) {
          router.noteDrop(key)
          _events.tryEmit(WsEvent.Backpressure(key, 1, policy.backpressureWindowMs))
        }
        return
      }
    }

    _events.emit(WsEvent.Raw(text))
  }

  private suspend fun send(obj: JsonObject) {
    bundleCh.trySend(obj)
  }

  private suspend fun sendNow(obj: JsonObject) {
    val raw = obj.toString()
    sendMutex.withLock {
      val s = ws
      if (s == null) {
        _events.emit(WsEvent.Disconnected("socket not ready"))
        scheduleReconnect()
        return
      }
      val ok = s.send(raw)
      if (!ok) {
        _events.emit(WsEvent.Disconnected("send failed"))
        scheduleReconnect()
      }
    }
  }

  private fun buildRequest(id: Int, method: String, params: List<Any>): JsonObject {
    return buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", id)
      put("method", method)
      put("params", json.encodeToJsonElement(params))
    }
  }

  private fun startFallback() {
    if (fallback == null) return
    if (fallbackJob?.isActive == true) return
    fallbackJob = scope.launch {
      while (!closed) {
        delay(1_200)
        val keys = subMutex.withLock { activeSubs.keys.toList() }
        try {
          fallback.poll(keys) { ev -> _events.emit(ev) }
        } catch (_: Throwable) {
          // ignore fallback errors
        }
      }
    }
  }

  private suspend fun stopFallback() {
    fallbackJob?.cancel()
    fallbackJob = null
  }

  private data class PendingRequest(
    val id: Int,
    val key: String,
    val subscribeMethod: String,
    val unsubscribeMethod: String,
    val params: List<Any>
  )

  private data class ActiveSub(
    val key: String,
    val subscribeMethod: String,
    val unsubscribeMethod: String,
    val params: List<Any>,
    var serverSubId: Long?
  )
}
