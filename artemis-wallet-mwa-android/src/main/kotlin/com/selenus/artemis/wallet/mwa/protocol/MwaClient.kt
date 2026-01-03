package com.selenus.artemis.wallet.mwa.protocol

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import java.security.KeyPair

internal class MwaClient(
  private val okHttp: OkHttpClient = MwaSession.defaultOkHttp(),
  private val json: Json = Json { ignoreUnknownKeys = true }
) {

  suspend fun openSession(
    activity: Activity,
    walletUriPrefix: Uri? = null,
    protocolVersionMajor: Int = 2,
    timeoutMs: Long = 10_000
  ): Pair<MwaSession, KeyPair> {
    val associationKeypair = MwaAndroid.generateAssociationKeypair()
    val port = MwaAndroid.randomEphemeralPort()
    val base = (walletUriPrefix ?: Uri.parse("solana-wallet:/"))
    val local = Uri.parse(base.toString().trimEnd('/') + "/v1/associate/local")
      .buildUpon()
      .appendQueryParameter("association", MwaAndroid.associationToken(associationKeypair))
      .appendQueryParameter("port", port.toString())
      .appendQueryParameter("v", protocolVersionMajor.toString())
      .build()

    MwaAndroid.launchWallet(activity, local)
    val session = MwaSession.connectLocal(
      okHttp = okHttp,
      port = port,
      associationKeypair = associationKeypair,
      protocolVersionMajor = protocolVersionMajor,
      timeoutMs = timeoutMs
    )
    return session to associationKeypair
  }

  suspend fun getCapabilities(session: MwaSession, timeoutMs: Long = 10_000): MwaCapabilities {
    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("get_capabilities").await() }
    return parseResult(rsp)
  }

  suspend fun authorize(
    session: MwaSession,
    identity: MwaIdentity,
    chain: String? = null,
    authToken: String? = null,
    features: List<String>? = null,
    timeoutMs: Long = 30_000
  ): MwaAuthorizeResult {
    val params = json.encodeToJsonElement(
      MwaAuthorizeRequest.serializer(),
      MwaAuthorizeRequest(identity = identity, chain = chain, authToken = authToken, features = features)
    )
    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("authorize", params).await() }
    return parseResult(rsp)
  }

  suspend fun signTransactions(
    session: MwaSession,
    payloads: List<ByteArray>,
    timeoutMs: Long = 60_000
  ): List<ByteArray> {
    val b64 = payloads.map { java.util.Base64.getEncoder().encodeToString(it) }
    val params = json.encodeToJsonElement(MwaSignTransactionsRequest.serializer(), MwaSignTransactionsRequest(b64))

    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("sign_transactions", params).await() }
    val result: MwaSignTransactionsResult = parseResult(rsp)
    return result.signedPayloads.map { java.util.Base64.getDecoder().decode(it) }
  }

  suspend fun signAndSend(
    session: MwaSession,
    payloads: List<ByteArray>,
    options: MwaSendOptions? = null,
    timeoutMs: Long = 90_000
  ): List<String> {
    val b64 = payloads.map { java.util.Base64.getEncoder().encodeToString(it) }
    val params = json.encodeToJsonElement(MwaSignAndSendRequest.serializer(), MwaSignAndSendRequest(b64, options))
    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("sign_and_send_transactions", params).await() }
    val result: MwaSignAndSendResult = parseResult(rsp)
    return result.signatures
  }

  private inline fun <reified T> parseResult(rsp: JsonObject): T {
    val err = rsp["error"]
    if (err != null) throw IllegalStateException("MWA error: $err")
    val result = rsp["result"] ?: throw IllegalStateException("Missing result")
    return json.decodeFromJsonElement(result)
  }
}
