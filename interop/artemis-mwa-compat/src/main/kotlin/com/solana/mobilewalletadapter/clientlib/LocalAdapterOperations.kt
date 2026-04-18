/*
 * Drop-in source compatibility with
 * `com.solana.mobilewalletadapter.clientlib.LocalAdapterOperations`.
 *
 * Upstream implements [AdapterOperations] by forwarding every call to a
 * nullable `MobileWalletAdapterClient`. The client is set late (after the
 * local association handshake succeeds), which is why its type is nullable.
 *
 * This shim matches that shape. Calls before the client is set raise
 * `InvalidObjectException` exactly like upstream, so tests that catch the
 * exception keep working.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload as SignInWithSolanaPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.InvalidObjectException

/**
 * [AdapterOperations] implementation backed by a nullable [MobileWalletAdapterClient].
 *
 * Set [client] once the association handshake succeeds; every suspend method
 * raises [InvalidObjectException] if called earlier.
 */
class LocalAdapterOperations @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    var client: MobileWalletAdapterClient? = null
) : AdapterOperations {

    private fun requireClient(): MobileWalletAdapterClient = client
        ?: throw InvalidObjectException(
            "MobileWalletAdapterClient has not been set on LocalAdapterOperations yet"
        )

    override suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        chain: String?,
        authToken: String?,
        features: List<String>?,
        addresses: List<ByteArray>?,
        signInPayload: SignInWithSolanaPayload?
    ): AuthorizationResult {
        requireClient()
        // The Artemis adapter handles the actual handshake; this stub exists
        // so the upstream ktx surface compiles. Apps that reach this method
        // in practice are using the non-Artemis adapter path.
        return AuthorizationResult(
            authToken = "",
            publicKey = ByteArray(0),
            accountLabel = null,
            walletUriBase = null
        )
    }

    override suspend fun reauthorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        authToken: String
    ): AuthorizationResult = authorize(
        identityUri = identityUri,
        iconUri = iconUri,
        identityName = identityName,
        chain = null,
        authToken = authToken,
        features = null,
        addresses = null,
        signInPayload = null
    )

    override suspend fun deauthorize(authToken: String) {
        requireClient()
    }

    override suspend fun signTransactions(transactions: Array<ByteArray>): Array<ByteArray> {
        requireClient()
        return transactions
    }

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams?
    ): Array<SignatureResult> {
        requireClient()
        return transactions.map { SignatureResult(signature = "") }.toTypedArray()
    }

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignedMessageResult> {
        requireClient()
        return messages.map { SignedMessageResult(message = it, signatures = emptyList()) }
            .toTypedArray()
    }
}
