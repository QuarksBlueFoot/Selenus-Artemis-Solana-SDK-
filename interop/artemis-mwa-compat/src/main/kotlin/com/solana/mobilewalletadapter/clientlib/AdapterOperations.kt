/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

// Explicit import of the nested SIWS Payload class. Although this file
// declares a `typealias SignInWithSolana = common.protocol.SignInWithSolana`
// below, Kotlin does not always resolve type references to nested classes
// through a typealias at declaration sites. Pulling the nested type in by
// its full name makes `SignInWithSolanaPayload` unambiguous.
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload as SignInWithSolanaPayload

/**
 * Operations available inside an MWA `transact` block.
 *
 * Matches upstream `com.solana.mobilewalletadapter.clientlib.AdapterOperations`.
 */
interface AdapterOperations {
    suspend fun authorize(
        identityUri: android.net.Uri,
        iconUri: android.net.Uri,
        identityName: String,
        chain: String? = null,
        authToken: String? = null,
        features: List<String>? = null,
        addresses: List<ByteArray>? = null,
        signInPayload: SignInWithSolanaPayload? = null
    ): AuthorizationResult

    suspend fun reauthorize(
        identityUri: android.net.Uri,
        iconUri: android.net.Uri,
        identityName: String,
        authToken: String
    ): AuthorizationResult

    suspend fun deauthorize(authToken: String)

    /**
     * Upstream returns `MobileWalletAdapterClient.SignPayloadsResult` with a
     * `signedPayloads: Array<ByteArray>` field. Matching the nested type lets
     * `when (val r = ops.signTransactions(txs))` and `r.signedPayloads`
     * pattern that every upstream guide uses resolve without rewriting call
     * sites. The nested type exposes the raw array too, so the shortcut
     * [signedPayloadsOf] extension preserves the `Array<ByteArray>` read path.
     */
    suspend fun signTransactions(
        transactions: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadsResult

    suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams? = null
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionsResult

    suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignMessagesResult

    /**
     * Sign messages and return the signatures detached from the original
     * payload. This is the preferred method in MWA 2.0 because it avoids
     * the legacy concatenation format and lets the caller keep the original
     * messages for client-side display.
     */
    suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignMessagesResult =
        signMessages(messages, addresses)

    /**
     * Return the capability descriptor advertised by the connected wallet.
     *
     * MWA 2.0 wallets implement `get_capabilities` so dapps can query
     * supported feature flags before issuing extension methods. Default
     * implementations return an empty capability set so legacy adapters keep
     * compiling.
     */
    suspend fun getCapabilities(): GetCapabilitiesResult =
        GetCapabilitiesResult(
            supportsCloneAuthorization = false,
            supportsSignAndSendTransactions = true,
            maxTransactionsPerSigningRequest = 0,
            maxMessagesPerSigningRequest = 0,
            // Upstream mixes types deliberately: legacy is a String, v0 is an
            // Int. Reproducing the heterogeneous array so wallets that type-
            // dispatch on the entries read the same bytes Artemis emits.
            supportedTransactionVersions = arrayOf<Any>("legacy", 0),
            supportedOptionalFeatures = emptyArray()
        )
}

/**
 * Capability descriptor returned by `get_capabilities`.
 *
 * The fields mirror the MWA 2.0 specification verbatim so callers that read
 * `result.supportsSignAndSendTransactions` continue to work without changes.
 */
data class GetCapabilitiesResult(
    val supportsCloneAuthorization: Boolean,
    val supportsSignAndSendTransactions: Boolean,
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    val supportedTransactionVersions: Array<Any>,
    val supportedOptionalFeatures: Array<String> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetCapabilitiesResult) return false
        return supportsCloneAuthorization == other.supportsCloneAuthorization &&
            supportsSignAndSendTransactions == other.supportsSignAndSendTransactions &&
            maxTransactionsPerSigningRequest == other.maxTransactionsPerSigningRequest &&
            maxMessagesPerSigningRequest == other.maxMessagesPerSigningRequest &&
            supportedTransactionVersions.contentEquals(other.supportedTransactionVersions) &&
            supportedOptionalFeatures.contentEquals(other.supportedOptionalFeatures)
    }

    override fun hashCode(): Int {
        var result = supportsCloneAuthorization.hashCode()
        result = 31 * result + supportsSignAndSendTransactions.hashCode()
        result = 31 * result + maxTransactionsPerSigningRequest
        result = 31 * result + maxMessagesPerSigningRequest
        result = 31 * result + supportedTransactionVersions.contentHashCode()
        result = 31 * result + supportedOptionalFeatures.contentHashCode()
        return result
    }
}

/**
 * Parameters for sign-and-send transaction requests.
 */
open class TransactionParams(
    val minContextSlot: Int? = null,
    val commitment: String? = null,
    val skipPreflight: Boolean? = null,
    val maxRetries: Int? = null,
    val waitForCommitmentToSendNextTransaction: Boolean? = null
)

object DefaultTransactionParams : TransactionParams()

/**
 * Result of a signed-and-sent transaction.
 */
data class SignatureResult(
    val signature: String,
    val commitment: String? = null
)

/**
 * Result of a signed message.
 */
data class SignedMessageResult(
    val message: ByteArray,
    val signatures: List<ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedMessageResult) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int = message.contentHashCode()
}

/**
 * `SignInWithSolana` at the ktx package level points at the verified
 * common-module class so `import com.solana.mobilewalletadapter.clientlib.SignInWithSolana`
 * resolves to the same type `import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana`
 * does. This matches the upstream behaviour (clientlib-ktx re-exports the
 * SIWS type from common).
 */
typealias SignInWithSolana = com.solana.mobilewalletadapter.common.protocol.SignInWithSolana

/**
 * `SignInResult` data class upstream exposes at the ktx package level,
 * distinct from the inner class on `MobileWalletAdapterClient.AuthorizationResult`.
 * Apps import `com.solana.mobilewalletadapter.clientlib.SignInResult` so this
 * file re-exports it.
 */
data class SignInResult(
    val publicKey: ByteArray,
    val signedMessage: ByteArray,
    val signature: ByteArray,
    val signatureType: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignInResult) return false
        return publicKey.contentEquals(other.publicKey) &&
            signedMessage.contentEquals(other.signedMessage) &&
            signature.contentEquals(other.signature) &&
            signatureType == other.signatureType
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + signedMessage.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + (signatureType?.hashCode() ?: 0)
        return result
    }
}
