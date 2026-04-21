package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MwaSession internal constructor(
  private val transport: MwaTransport,
  private val cipher: Aes128Gcm,
  private val json: Json = Json { ignoreUnknownKeys = true }
) : Closeable {

  // Scope owns the receive loop + any child coroutines the session spawns.
  // Retained so [close] can tear it down deterministically instead of leaking
  // background work past session teardown.
  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private val closed = AtomicBoolean(false)

  // AES-GCM with deterministic counter-based nonces REQUIRES that no counter
  // value is ever reused. The previous implementation incremented plain Ints
  // which could race under coroutine dispatch: two concurrent sendJsonRpc
  // calls could capture the same seq, producing two ciphertexts under the
  // same nonce (catastrophic confidentiality failure). AtomicInteger forces
  // every increment to be a single CAS, so each encrypted frame gets a
  // unique sequence number.
  private val sendSeq = AtomicInteger(1)
  private val recvSeq = AtomicInteger(1)

  private val nextId = AtomicInteger(1)
  private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

  /**
   * Session properties advertised by the wallet (protocol version, etc).
   * Populated by [connectLocal] after HELLO_RSP is decrypted.
   */
  @Volatile
  var sessionProperties: MwaSessionProperties? = null
    private set

  fun sendJsonRpc(method: String, params: JsonElement? = null): CompletableDeferred<JsonObject> {
    val def = CompletableDeferred<JsonObject>()
    if (closed.get()) {
      // Reject cleanly instead of dispatching a request whose reply can
      // never arrive. Callers that awaited() get an immediate exception
      // rather than hanging until their own timeout fires.
      def.completeExceptionally(
        IllegalStateException("MWA session is closed; cannot send $method")
      )
      return def
    }
    val id = nextId.getAndIncrement()
    val req = buildJsonRpcRequest(id, method, params)
    val bytes = json.encodeToString(JsonObject.serializer(), req).encodeToByteArray()
    val packet = cipher.encrypt(sendSeq.getAndIncrement(), bytes)
    pending[id] = def
    try {
      transport.send(packet)
    } catch (e: Throwable) {
      pending.remove(id)
      def.completeExceptionally(e)
    }
    return def
  }

  fun handleIncoming(packet: ByteArray) {
    if (closed.get()) return
    val plain = cipher.decrypt(recvSeq.getAndIncrement(), packet)
    val obj = json.parseToJsonElement(plain.decodeToString()) as? JsonObject ?: return
    val id = (obj["id"] as? JsonPrimitive)?.intOrNull
    if (id != null) {
      pending.remove(id)?.complete(obj)
    }
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    // Fail every pending JSON-RPC future immediately so awaiting callers
    // (authorize, signMessages, etc.) unblock with a typed exception
    // instead of hanging until their own timeout. This was the original
    // "hung futures / resource leak" bug from the audit.
    val cancel = CancellationException("MWA session closed")
    val snapshot = pending.values.toList()
    pending.clear()
    for (def in snapshot) {
      def.completeExceptionally(cancel)
    }
    try { transport.close(1000, "bye") } catch (_: Throwable) {}
    scope.cancel(cancel)
  }

  private fun buildJsonRpcRequest(id: Int, method: String, params: JsonElement?): JsonObject {
    val m = mutableMapOf<String, JsonElement>(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to JsonPrimitive(id),
      "method" to JsonPrimitive(method)
    )
    if (params != null) m["params"] = params
    return JsonObject(m)
  }

  companion object {
    /**
     * Test-only factory that returns a session backed by an in-memory
     * transport and a throwaway AES-128-GCM key. The returned session will
     * throw on any send/receive attempt — it exists only so behavior tests
     * can pass an opaque token through a stubbed [MwaClient] without
     * standing up a real association socket. Never call this from
     * production code paths.
     */
    internal fun testSession(): MwaSession {
      val transport = object : MwaTransport {
        override fun send(data: ByteArray) = error("test session: send not supported")
        override fun close(code: Int, reason: String) = Unit
        override val incoming: Channel<ByteArray> = Channel()
      }
      return MwaSession(transport = transport, cipher = Aes128Gcm(ByteArray(16)))
    }

    internal suspend fun connectLocal(
      server: MwaWebSocketServer,
      associationKeypair: java.security.KeyPair,
      protocolVersionMajor: Int = 2,
      timeoutMs: Long = 10_000
    ): MwaSession {
      val transport = server.accept(timeoutMs)

      // HELLO_REQ
      val eph = EcP256.generateKeypair()
      val qd = EcP256.x962Uncompressed(eph.public)
      val sa = EcP256.signP1363(associationKeypair.private, qd)
      transport.send(qd + sa)

      // HELLO_RSP
      val helloRsp = withTimeout(timeoutMs) { transport.incoming.receive() }
      require(helloRsp.size >= 65) { "HELLO_RSP too short" }
      val qwBytes = helloRsp.copyOfRange(0, 65)
      val walletEphPub = EcP256.publicKeyFromX962(qwBytes)

      val ikm = EcP256.ecdhSecret(eph.private, walletEphPub)
      val salt = EcP256.x962Uncompressed(associationKeypair.public)
      val key16 = HkdfSha256.derive(ikm = ikm, salt = salt, length = 16)
      val cipher = Aes128Gcm(key16)

      val session = MwaSession(transport = transport, cipher = cipher)

      // Any additional bytes after Qw are encrypted session props (v param
      // present). When present, parse the protocol version so subsequent
      // RPC calls can branch on MWA 1.x vs 2.x wallets instead of assuming.
      if (helloRsp.size > 65) {
        val propsPacket = helloRsp.copyOfRange(65, helloRsp.size)
        runCatching {
          val plain = cipher.decrypt(expectedSeq = 1, packet = propsPacket)
          val propsJson = session.json.parseToJsonElement(plain.decodeToString()) as? JsonObject
          val versionWire = (propsJson?.get("v") as? JsonPrimitive)?.content
          if (versionWire != null) {
            val parsedVersion = MwaSessionProperties.ProtocolVersion.fromWireValueOrDefault(
              versionWire,
              MwaSessionProperties.ProtocolVersion.V1
            )
            session.sessionProperties = MwaSessionProperties(protocolVersion = parsedVersion)
          }
        }
        session.recvSeq.set(2) // we've consumed one encrypted message
      }

      // Wire the receive loop onto the session's own scope so close()
      // cancels it deterministically. Previous implementation launched on
      // a fresh CoroutineScope that nobody held a handle to, leaking the
      // receive coroutine past teardown.
      session.scope.launch {
        for (pkt in transport.incoming) session.handleIncoming(pkt)
      }

      return session
    }
  }
}
