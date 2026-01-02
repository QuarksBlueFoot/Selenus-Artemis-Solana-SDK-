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

class MwaSession internal constructor(
  private val transport: MwaTransport,
  private val cipher: Aes128Gcm,
  private val json: Json = Json { ignoreUnknownKeys = true }
) : Closeable {

  @Suppress("unused")
  private val scope = CoroutineScope(Dispatchers.IO + Job())

  private var sendSeq = 1
  private var recvSeq = 1

  private var nextId = 1
  private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

  fun sendJsonRpc(method: String, params: JsonElement? = null): CompletableDeferred<JsonObject> {
    val id = nextId++
    val req = buildJsonRpcRequest(id, method, params)
    val bytes = json.encodeToString(JsonObject.serializer(), req).encodeToByteArray()
    val packet = cipher.encrypt(sendSeq++, bytes)
    val def = CompletableDeferred<JsonObject>()
    pending[id] = def
    transport.send(packet)
    return def
  }

  fun handleIncoming(packet: ByteArray) {
    val plain = cipher.decrypt(recvSeq++, packet)
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

      // Any additional bytes after Qw are encrypted session props (v param present).
      if (helloRsp.size > 65) {
        // We don't need to parse session_props for basic functionality; if wallets send it,
        // it is a good sanity check that decryption works at seq=1.
        val propsPacket = helloRsp.copyOfRange(65, helloRsp.size)
        runCatching { cipher.decrypt(expectedSeq = 1, packet = propsPacket) }
        session.recvSeq = 2 // we've consumed one encrypted message
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
