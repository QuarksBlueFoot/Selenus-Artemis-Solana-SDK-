package com.selenus.artemis.wallet.mwa.protocol

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.security.KeyPair

class MwaClient(
  private val json: Json = Json { ignoreUnknownKeys = true }
) {

  suspend fun openSession(
    activity: Activity,
    walletUriPrefix: Uri? = null,
    protocolVersionMajor: Int = 2,
    timeoutMs: Long = 10_000
  ): Pair<MwaSession, KeyPair> {
    val associationKeypair = MwaAndroid.generateAssociationKeypair()
    val server = MwaWebSocketServer()
    val port = server.bind(0) // Bind to random port

    val base = (walletUriPrefix ?: Uri.parse("solana-wallet:/"))
    val local = Uri.parse(base.toString().trimEnd('/') + "/v1/associate/local")
      .buildUpon()
      .appendQueryParameter("association", MwaAndroid.associationToken(associationKeypair))
      .appendQueryParameter("port", port.toString())
      .appendQueryParameter("v", protocolVersionMajor.toString())
      .build()

    MwaAndroid.launchWallet(activity, local)
    val session = MwaSession.connectLocal(
      server = server,
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
    signInPayload: MwaSignInPayload? = null,
    timeoutMs: Long = 30_000
  ): MwaAuthorizeResult {
    val params = json.encodeToJsonElement(
      MwaAuthorizeRequest.serializer(),
      MwaAuthorizeRequest(
        identity = identity, 
        chain = chain, 
        authToken = authToken, 
        features = features,
        signInPayload = signInPayload
      )
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

  suspend fun reauthorize(
    session: MwaSession,
    identity: MwaIdentity,
    authToken: String,
    timeoutMs: Long = 10_000
  ): MwaAuthorizeResult {
    val params = json.encodeToJsonElement(
      MwaReauthorizeRequest.serializer(),
      MwaReauthorizeRequest(identity = identity, authToken = authToken)
    )
    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("reauthorize", params).await() }
    return parseResult(rsp)
  }

  suspend fun deauthorize(
    session: MwaSession,
    authToken: String,
    timeoutMs: Long = 10_000
  ) {
    val params = json.encodeToJsonElement(
      MwaDeauthorizeRequest.serializer(),
      MwaDeauthorizeRequest(authToken = authToken)
    )
    withTimeout(timeoutMs) { session.sendJsonRpc("deauthorize", params).await() }
  }

  suspend fun signMessages(
    session: MwaSession,
    payloads: List<ByteArray>,
    addresses: List<ByteArray>,
    timeoutMs: Long = 60_000
  ): List<ByteArray> {
    val b64Payloads = payloads.map { java.util.Base64.getEncoder().encodeToString(it) }
    val b64Addresses = addresses.map { java.util.Base64.getEncoder().encodeToString(it) }
    val params = json.encodeToJsonElement(
      MwaSignMessagesRequest.serializer(),
      MwaSignMessagesRequest(b64Payloads, b64Addresses)
    )

    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("sign_messages", params).await() }
    val result: MwaSignMessagesResult = parseResult(rsp)
    return result.signedPayloads.map { java.util.Base64.getDecoder().decode(it) }
  }

  /**
   * Clone an existing authorization token.
   * 
   * MWA 2.0 Optional Feature: Allows apps to duplicate an authorization
   * without requiring user interaction. Useful for background operations
   * or multi-window scenarios.
   * 
   * @param session The active MWA session
   * @param authToken The auth token to clone
   * @return A new auth token with the same permissions
   * @throws IllegalStateException if wallet doesn't support clone_authorization
   */
  suspend fun cloneAuthorization(
    session: MwaSession,
    authToken: String,
    timeoutMs: Long = 10_000
  ): String {
    val params = json.encodeToJsonElement(
      MwaCloneAuthorizationRequest.serializer(),
      MwaCloneAuthorizationRequest(authToken = authToken)
    )
    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("clone_authorization", params).await() }
    val result: MwaCloneAuthorizationResult = parseResult(rsp)
    return result.authToken
  }

  /**
   * Sign messages with detached signature result.
   * 
   * Improved MWA 2.0 API that separates messages from signatures in the response.
   * This is cleaner than the original signMessages which concatenated them.
   * 
   * @param session The active MWA session
   * @param messages List of messages to sign
   * @param addresses List of addresses (one per message, or one for all)
   * @return Pair of (original messages, signatures for each message)
   */
  suspend fun signMessagesDetached(
    session: MwaSession,
    messages: List<ByteArray>,
    addresses: List<ByteArray>,
    timeoutMs: Long = 60_000
  ): Pair<List<ByteArray>, List<ByteArray>> {
    val b64Messages = messages.map { java.util.Base64.getEncoder().encodeToString(it) }
    val b64Addresses = addresses.map { java.util.Base64.getEncoder().encodeToString(it) }
    val params = json.encodeToJsonElement(
      MwaSignMessagesRequest.serializer(),
      MwaSignMessagesRequest(b64Messages, b64Addresses)
    )

    val rsp = withTimeout(timeoutMs) { session.sendJsonRpc("sign_messages", params).await() }
    val result: MwaSignMessagesResult = parseResult(rsp)
    
    // Parse signed payloads - each contains message + signature (64 bytes at end)
    val signedPayloads = result.signedPayloads.map { java.util.Base64.getDecoder().decode(it) }
    
    // Extract signatures (last 64 bytes of each signed payload)
    val signatures = signedPayloads.map { signed ->
      if (signed.size >= 64) {
        signed.sliceArray((signed.size - 64) until signed.size)
      } else {
        signed // Malformed, return as-is
      }
    }
    
    return messages to signatures
  }

  private inline fun <reified T> parseResult(rsp: JsonObject): T {
    val err = rsp["error"]
    if (err != null) throw IllegalStateException("MWA error: $err")
    val result = rsp["result"] ?: throw IllegalStateException("Missing result")
    return json.decodeFromJsonElement(result)
  }
}
