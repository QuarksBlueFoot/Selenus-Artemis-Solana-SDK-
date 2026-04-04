/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

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
        signInPayload: SignInWithSolana.Payload? = null
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
}

/**
 * Parameters for sign-and-send transaction requests.
 */
open class TransactionParams(
    val minContextSlot: Int? = null,
    val commitment: String? = null,
    val skipPreflight: Boolean = false,
    val maxRetries: Int? = null,
    val waitForCommitmentToSendNextTransaction: Boolean = false
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
 * Sign In With Solana (SIWS) payload and result types.
 */
object SignInWithSolana {
    data class Payload(
        val domain: String? = null,
        val address: String? = null,
        val statement: String? = null,
        val uri: String? = null,
        val version: String? = null,
        val chainId: String? = null,
        val nonce: String? = null,
        val issuedAt: String? = null,
        val expirationTime: String? = null,
        val notBefore: String? = null,
        val requestId: String? = null,
        val resources: List<String>? = null
    )

    data class Result(
        val account: AuthorizationResult.Account,
        val signInMessage: ByteArray,
        val signature: ByteArray,
        val signatureType: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            return signInMessage.contentEquals(other.signInMessage) &&
                    signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int = signInMessage.contentHashCode()
    }
}
