package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

  @Suppress("unused")
  private val scope = CoroutineScope(Dispatchers.IO + Job())

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
    val id = nextId.getAndIncrement()
    val req = buildJsonRpcRequest(id, method, params)
    val bytes = json.encodeToString(JsonObject.serializer(), req).encodeToByteArray()
    val packet = cipher.encrypt(sendSeq.getAndIncrement(), bytes)
    val def = CompletableDeferred<JsonObject>()
    pending[id] = def
    transport.send(packet)
    return def
  }

  fun handleIncoming(packet: ByteArray) {
    val plain = cipher.decrypt(recvSeq.getAndIncrement(), packet)
    val obj = json.parseToJsonElement(plain.decodeToString()) as? JsonObject ?: return
    val id = (obj["id"] as? JsonPrimitive)?.intOrNull
    if (id != null) {
      pending.remove(id)?.complete(obj)
    }
  }

  override fun close() {
    try { transport.close(1000, "bye") } catch (_: Throwable) {}
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

      // Wire receive loop
      CoroutineScope(Dispatchers.IO).apply {
        launch {
          for (pkt in transport.incoming) session.handleIncoming(pkt)
        }
      }

      return session
    }
  }
}
