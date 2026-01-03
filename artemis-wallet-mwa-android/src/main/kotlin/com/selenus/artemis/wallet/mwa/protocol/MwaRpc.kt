package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MwaIdentity(
  val uri: String? = null,
  val icon: String? = null,
  val name: String? = null
)

@Serializable
internal data class MwaAuthorizeRequest(
  val identity: MwaIdentity? = null,
  val chain: String? = null,
  val features: List<String>? = null,
  val addresses: List<String>? = null,
  @SerialName("auth_token") val authToken: String? = null,
  val cluster: String? = null
)

@Serializable
internal data class MwaAccount(
  // base64 encoded public key bytes
  val address: String,
  val label: String? = null
)

@Serializable
internal data class MwaAuthorizeResult(
  @SerialName("auth_token") val authToken: String,
  val accounts: List<MwaAccount> = emptyList()
)

@Serializable
internal data class MwaCapabilities(
  // Wallets vary. We keep fields optional and rely on ignoreUnknownKeys.
  @SerialName("supports_sign_and_send_transactions") val supportsSignAndSendTransactions: Boolean? = null,
  @SerialName("supports_sign_transactions") val supportsSignTransactions: Boolean? = null,
  @SerialName("max_transactions_per_request") val maxTransactionsPerRequest: Int? = null,
  @SerialName("max_messages_per_request") val maxMessagesPerRequest: Int? = null,
  @SerialName("features") val features: List<String>? = null
) {
  fun supportsSignAndSend(): Boolean = supportsSignAndSendTransactions == true || (features?.contains("sign_and_send_transactions") == true)
  fun supportsSignTransactions(): Boolean = supportsSignTransactions != false
}

@Serializable
internal data class MwaSignTransactionsRequest(
  val payloads: List<String>
)

@Serializable
internal data class MwaSignTransactionsResult(
  @SerialName("signed_payloads") val signedPayloads: List<String>
)

@Serializable
internal data class MwaSendOptions(
  @SerialName("skip_preflight") val skipPreflight: Boolean? = null,
  @SerialName("max_retries") val maxRetries: Int? = null,
  @SerialName("preflight_commitment") val preflightCommitment: String? = null,
  @SerialName("min_context_slot") val minContextSlot: Long? = null
)

@Serializable
internal data class MwaSignAndSendRequest(
  val payloads: List<String>,
  val options: MwaSendOptions? = null
)

@Serializable
internal data class MwaSignAndSendResult(
  val signatures: List<String>
)
