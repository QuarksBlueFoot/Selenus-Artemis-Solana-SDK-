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

    suspend fun signTransactions(transactions: Array<ByteArray>): Array<ByteArray>

    suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams? = null
    ): Array<SignatureResult>

    suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignedMessageResult>

    /**
     * Sign messages and return the signatures detached from the original
     * payload. This is the preferred method in MWA 2.0 because it avoids
     * the legacy concatenation format and lets the caller keep the original
     * messages for client-side display.
     */
    suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignedMessageResult> = signMessages(messages, addresses)

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
            supportedTransactionVersions = listOf("legacy", "0")
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
    val supportedTransactionVersions: List<String>
)

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
