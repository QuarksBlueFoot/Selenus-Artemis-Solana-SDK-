package com.selenus.artemis.wallet.mwa.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MwaIdentity(
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
  val cluster: String? = null,
  @SerialName("sign_in_payload") val signInPayload: MwaSignInPayload? = null
)

@Serializable
data class MwaAccount(
  // base64 encoded public key bytes
  val address: String,
  val label: String? = null
)

@Serializable
data class MwaAuthorizeResult(
  @SerialName("auth_token") val authToken: String,
  val accounts: List<MwaAccount> = emptyList(),
  @SerialName("sign_in_result") val signInResult: MwaSignInResult? = null
)

/**
 * MWA Capabilities Response
 * 
 * Full MWA 2.0 spec compliant capabilities with Artemis enhancements.
 * Provides feature detection for wallet capabilities.
 */
@Serializable
data class MwaCapabilities(
  // Core MWA 2.0 capabilities
  @SerialName("max_transactions_per_request") val maxTransactionsPerRequest: Int? = null,
  @SerialName("max_messages_per_request") val maxMessagesPerRequest: Int? = null,
  @SerialName("supported_transaction_versions") val supportedTransactionVersions: List<@Serializable(with = TransactionVersionSerializer::class) Any>? = null,
  @SerialName("features") val features: List<String>? = null,
  
  // Legacy/deprecated fields (still supported for compatibility)
  @Deprecated("Use features array instead")
  @SerialName("supports_sign_and_send_transactions") val supportsSignAndSendTransactions: Boolean? = null,
  @Deprecated("Use features array instead")  
  @SerialName("supports_clone_authorization") val supportsCloneAuthorization: Boolean? = null,
  @SerialName("supports_sign_transactions") val supportsSignTransactions: Boolean? = null
) {
  companion object {
    // MWA 2.0 Feature Identifiers
    const val FEATURE_SIGN_AND_SEND_TRANSACTIONS = "solana:signAndSendTransaction"
    const val FEATURE_SIGN_TRANSACTIONS = "solana:signTransactions"
    const val FEATURE_SIGN_MESSAGES = "solana:signMessages"
    const val FEATURE_SIGN_IN_WITH_SOLANA = "solana:signInWithSolana"
    const val FEATURE_CLONE_AUTHORIZATION = "solana:cloneAuthorization"
  }
  
  /** Check if wallet supports sign-and-send (MWA 2.0 mandatory feature) */
  fun supportsSignAndSend(): Boolean = 
    supportsSignAndSendTransactions == true || 
    features?.contains(FEATURE_SIGN_AND_SEND_TRANSACTIONS) == true
  
  /** Check if wallet supports sign-only transactions (optional MWA 2.0 feature) */
  fun supportsSignTransactions(): Boolean = 
    supportsSignTransactions != false || 
    features?.contains(FEATURE_SIGN_TRANSACTIONS) == true
  
  /** Check if wallet supports clone_authorization (optional MWA 2.0 feature) */
  fun supportsCloneAuth(): Boolean = 
    supportsCloneAuthorization == true || 
    features?.contains(FEATURE_CLONE_AUTHORIZATION) == true
  
  /** Check if wallet supports Sign In With Solana (SIWS) */
  fun supportsSignIn(): Boolean = 
    features?.contains(FEATURE_SIGN_IN_WITH_SOLANA) == true
  
  /** Check if wallet supports legacy transaction format */
  fun supportsLegacyTransactions(): Boolean = 
    supportedTransactionVersions?.contains("legacy") == true ||
    supportedTransactionVersions == null // Assume legacy if not specified
  
  /** Check if wallet supports versioned transactions (v0) */
  fun supportsVersionedTransactions(): Boolean = 
    supportedTransactionVersions?.any { it == 0 || it == "0" } == true
  
  /** Get all supported features as a set for easy checking */
  fun allFeatures(): Set<String> = features?.toSet() ?: emptySet()
}

/**
 * Custom serializer for transaction versions that can be "legacy" (String) or 0 (Int)
 */
object TransactionVersionSerializer : kotlinx.serialization.KSerializer<Any> {
  override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
    "TransactionVersion", 
    kotlinx.serialization.descriptors.PrimitiveKind.STRING
  )
  
  override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any) {
    when (value) {
      is String -> encoder.encodeString(value)
      is Int -> encoder.encodeInt(value)
      else -> encoder.encodeString(value.toString())
    }
  }
  
  override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any {
    val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
      ?: return decoder.decodeString()
    return when (val element = jsonDecoder.decodeJsonElement()) {
      is kotlinx.serialization.json.JsonPrimitive -> {
        if (element.isString) element.content
        else element.content.toIntOrNull() ?: element.content
      }
      else -> element.toString()
    }
  }
}

@Serializable
internal data class MwaSignTransactionsRequest(
  val payloads: List<String>
)

@Serializable
internal data class MwaSignTransactionsResult(
  @SerialName("signed_payloads") val signedPayloads: List<String>
)

/**
 * MWA Sign and Send Transaction Options
 * 
 * Full MWA 2.0 spec compliant options for sign_and_send_transactions.
 * Includes all parameters from the protocol specification.
 */
@Serializable
data class MwaSendOptions(
  /** Minimum slot that the request can be evaluated at */
  @SerialName("min_context_slot") val minContextSlot: Long? = null,
  
  /** Commitment level for confirmation: "processed", "confirmed", or "finalized" */
  @SerialName("commitment") val commitment: String? = null,
  
  /** If true, skip the preflight transaction checks */
  @SerialName("skip_preflight") val skipPreflight: Boolean? = null,
  
  /** Maximum number of times for the RPC node to retry sending to the leader */
  @SerialName("max_retries") val maxRetries: Int? = null,
  
  /** 
   * Commitment level to use for the preflight check.
   * Only relevant when skipPreflight is false.
   */
  @SerialName("preflight_commitment") val preflightCommitment: String? = null,
  
  /**
   * MWA 2.0: For batch operations, wait for each transaction to reach
   * commitment before sending the next one. Critical for dependent transactions.
   */
  @SerialName("wait_for_commitment_to_send_next_transaction") 
  val waitForCommitmentToSendNextTransaction: Boolean? = null
) {
  companion object {
    /** Default options suitable for most transactions */
    val Default = MwaSendOptions()
    
    /** Fast options: skip preflight, max retries */
    val Fast = MwaSendOptions(
      skipPreflight = true,
      maxRetries = 5
    )
    
    /** Finalized options: wait for finalized commitment */
    val Finalized = MwaSendOptions(
      commitment = "finalized",
      preflightCommitment = "processed"
    )
    
    /** Sequential options for dependent transactions */
    val Sequential = MwaSendOptions(
      waitForCommitmentToSendNextTransaction = true,
      commitment = "confirmed"
    )
  }
}

@Serializable
internal data class MwaSignAndSendRequest(
  val payloads: List<String>,
  val options: MwaSendOptions? = null
)

@Serializable
internal data class MwaSignAndSendResult(
  val signatures: List<String>
)

@Serializable
internal data class MwaReauthorizeRequest(
  val identity: MwaIdentity? = null,
  @SerialName("auth_token") val authToken: String
)

@Serializable
internal data class MwaDeauthorizeRequest(
  @SerialName("auth_token") val authToken: String
)

@Serializable
internal data class MwaSignMessagesRequest(
  val payloads: List<String>,
  val addresses: List<String>
)

@Serializable
internal data class MwaSignMessagesResult(
  @SerialName("signed_payloads") val signedPayloads: List<String>
)

// ============================================================================
// Clone Authorization (MWA 2.0 Optional Feature)
// ============================================================================

@Serializable
internal data class MwaCloneAuthorizationRequest(
  @SerialName("auth_token") val authToken: String
)

@Serializable
internal data class MwaCloneAuthorizationResult(
  @SerialName("auth_token") val authToken: String
)

// ============================================================================
// Sign Messages Detached Result (Improved MWA 2.0 Response)
// ============================================================================

/**
 * Detached signature result that separates the original message from signatures.
 * This is the improved response format for sign_messages in MWA 2.0.
 */
@Serializable
data class MwaSignMessagesDetachedResult(
  /** The original messages that were signed */
  val messages: List<String>,
  /** Signatures for each message, in the same order */
  val signatures: List<List<String>>,
  /** The addresses that signed each message */
  val addresses: List<String>
)

// ============================================================================
// MWA Error Codes (from MWA 2.0 Specification)
// ============================================================================

/**
 * Standard MWA protocol error codes.
 * These match the JSON-RPC error codes defined in the MWA specification.
 */
object MwaErrorCodes {
  const val AUTHORIZATION_FAILED = -1
  const val INVALID_PAYLOADS = -2
  const val NOT_SIGNED = -3
  const val NOT_SUBMITTED = -4
  const val NOT_CLONED = -5
  const val TOO_MANY_PAYLOADS = -6
  const val CHAIN_NOT_SUPPORTED = -7
  const val ATTEST_ORIGIN_ANDROID = -100
}

/**
 * MWA protocol exception with structured error information.
 */
class MwaProtocolException(
  val code: Int,
  override val message: String,
  val data: String? = null
) : Exception(message) {
  companion object {
    fun fromCode(code: Int, data: String? = null): MwaProtocolException {
      val message = when (code) {
        MwaErrorCodes.AUTHORIZATION_FAILED -> "Authorization failed"
        MwaErrorCodes.INVALID_PAYLOADS -> "Invalid payloads"
        MwaErrorCodes.NOT_SIGNED -> "Transaction not signed"
        MwaErrorCodes.NOT_SUBMITTED -> "Transaction not submitted"
        MwaErrorCodes.NOT_CLONED -> "Authorization could not be cloned"
        MwaErrorCodes.TOO_MANY_PAYLOADS -> "Too many payloads in request"
        MwaErrorCodes.CHAIN_NOT_SUPPORTED -> "Chain not supported by wallet"
        MwaErrorCodes.ATTEST_ORIGIN_ANDROID -> "Android origin attestation failed"
        else -> "Unknown MWA error (code: $code)"
      }
      return MwaProtocolException(code, message, data)
    }
  }
}
