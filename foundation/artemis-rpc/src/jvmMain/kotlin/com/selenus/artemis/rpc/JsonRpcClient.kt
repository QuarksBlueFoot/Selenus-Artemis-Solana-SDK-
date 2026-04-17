package com.selenus.artemis.rpc

import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * JsonRpcClient
 *
 * - OkHttp by default
 * - optional HttpTransport override for Ktor or custom networking
 * - retry/backoff for mobile reliability
 * - JSON-RPC batch requests (multiple calls in one HTTP round-trip)
 * - optional endpoint pool for multi-endpoint failover
 */
class JsonRpcClient(
  private val endpoint: String,
  private val http: OkHttpClient = OkHttpClient(),
  private val transport: HttpTransport? = null,
  private val config: RpcClientConfig = RpcClientConfig(),
  private val backoff: BackoffStrategy = ExponentialJitterBackoff,
  private val endpointPool: RpcEndpointPool? = null,
  private val router: RpcRouter? = null
) : RpcClient {
  private val id = AtomicLong(1)
  private val mediaType = "application/json".toMediaType()

  /**
   * Create a client backed by an endpoint pool for automatic failover.
   */
  constructor(
    pool: RpcEndpointPool,
    http: OkHttpClient = OkHttpClient(),
    transport: HttpTransport? = null,
    config: RpcClientConfig = RpcClientConfig(),
    backoff: BackoffStrategy = ExponentialJitterBackoff
  ) : this(
    endpoint = pool.endpoints().first(),
    http = http,
    transport = transport,
    config = config,
    backoff = backoff,
    endpointPool = pool
  )

  /**
   * Create a client backed by a smart router for method-based routing.
   */
  constructor(
    router: RpcRouter,
    http: OkHttpClient = OkHttpClient(),
    transport: HttpTransport? = null,
    config: RpcClientConfig = RpcClientConfig(),
    backoff: BackoffStrategy = ExponentialJitterBackoff
  ) : this(
    endpoint = router.fallbackPool().endpoints().first(),
    http = http,
    transport = transport,
    config = config,
    backoff = backoff,
    endpointPool = router.fallbackPool(),
    router = router
  )

  override suspend fun call(method: String, params: JsonElement?): JsonObject {
    val payload = buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", id.getAndIncrement())
      put("method", method)
      if (params != null) put("params", params)
    }

    var attempt = 1
    var lastErr: Throwable? = null

    while (attempt <= config.maxAttempts) {
      val target = router?.selectEndpoint(method) ?: endpointPool?.selectEndpoint() ?: endpoint
      val start = System.currentTimeMillis()
      try {
        val bodyText = withContext(Dispatchers.IO) { doPost(payload.toString(), target) }
        val json = Json.parseToJsonElement(bodyText).jsonObject
        json["error"]?.let { throw RpcException(it.toString()) }
        val latency = System.currentTimeMillis() - start
        if (router != null) router.reportSuccess(method, target, latency)
        else endpointPool?.reportSuccess(target, latency)
        return json
      } catch (t: Throwable) {
        lastErr = t
        if (router != null) router.reportFailure(method, target)
        else endpointPool?.reportFailure(target)
        if (!shouldRetry(t, attempt)) break
        val sleep = backoff.backoffMs(attempt, config)
        delay(sleep)
        attempt += 1
      }
    }

    throw (lastErr ?: RpcException("unknown_rpc_error"))
  }

  /**
   * Send multiple JSON-RPC calls in a single HTTP request (JSON-RPC batch).
   *
   * Each call is specified as a (method, params) pair. Returns a list of responses
   * in the same order as the requests, regardless of server response ordering.
   *
   * This is significantly more efficient than individual calls when you need
   * multiple pieces of data - saves HTTP round-trips and reduces latency on mobile.
   *
   * ```kotlin
   * val responses = client.callBatch(listOf(
   *     "getBalance" to buildJsonArray { add(JsonPrimitive(pubkey)) },
   *     "getSlot"    to null,
   *     "getHealth"  to null
   * ))
   * ```
   */
  override suspend fun callBatch(requests: List<Pair<String, JsonElement?>>): List<JsonObject> {
    if (requests.isEmpty()) return emptyList()

    // Build batch array, tracking id → index mapping
    val startId = id.getAndAdd(requests.size.toLong())
    val idToIndex = mutableMapOf<Long, Int>()

    val batchPayload = buildJsonArray {
      requests.forEachIndexed { index, (method, params) ->
        val reqId = startId + index
        idToIndex[reqId] = index
        add(buildJsonObject {
          put("jsonrpc", "2.0")
          put("id", reqId)
          put("method", method)
          if (params != null) put("params", params)
        })
      }
    }

    var attempt = 1
    var lastErr: Throwable? = null

    while (attempt <= config.maxAttempts) {
      val target = endpointPool?.selectEndpoint() ?: endpoint
      val start = System.currentTimeMillis()
      try {
        val bodyText = withContext(Dispatchers.IO) { doPost(batchPayload.toString(), target) }
        val parsed = Json.parseToJsonElement(bodyText)

        // Server returns a JSON array of responses
        val responseArray = parsed.jsonArray
        val results = arrayOfNulls<JsonObject>(requests.size)

        for (element in responseArray) {
          val obj = element.jsonObject
          val respId = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
          val idx = idToIndex[respId] ?: continue
          results[idx] = obj
        }

        // Verify all responses arrived
        val mapped = results.mapIndexed { i, r ->
          r ?: throw RpcException("Missing response for batch request #$i")
        }
        endpointPool?.reportSuccess(target, System.currentTimeMillis() - start)
        return mapped
      } catch (t: Throwable) {
        lastErr = t
        endpointPool?.reportFailure(target)
        if (!shouldRetry(t, attempt)) break
        val sleep = backoff.backoffMs(attempt, config)
        delay(sleep)
        attempt += 1
      }
    }

    throw (lastErr ?: RpcException("unknown_rpc_error"))
  }

  private fun doPost(body: String, targetUrl: String = endpoint): String {
    if (transport != null) {
      val resp = transport.postJson(
        url = targetUrl,
        body = body,
        headers = mapOf("Content-Type" to "application/json")
      )
      if (resp.code < 200 || resp.code >= 300) throw RpcHttpException(resp.code)
      return resp.body
    }

    val req = Request.Builder()
      .url(targetUrl)
      .post(body.toRequestBody(mediaType))
      .build()

    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw RpcHttpException(resp.code)
      return resp.body?.string() ?: throw RpcException("Empty response body")
    }
  }

  private fun shouldRetry(t: Throwable, attempt: Int): Boolean {
    if (attempt >= config.maxAttempts) return false
    if (t is RpcHttpException) {
      if (t.code == 429) return config.retryOnHttp429
      if (t.code >= 500) return config.retryOnHttp5xx
      return false
    }
    val msg = (t.message ?: "").lowercase()
    if (msg.contains("timeout")) return config.retryOnTimeout
    return false
  }
}
