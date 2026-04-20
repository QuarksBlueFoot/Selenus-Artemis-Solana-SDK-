/*
 * [AdapterOperations] implementation backed by a nullable [MobileWalletAdapterClient].
 *
 * Semantics match upstream clientlib-ktx: callers wire the client in after
 * the association handshake succeeds, and every operation forwards through
 * the raw clientlib (whose SessionBridge has been installed by Artemis's
 * [MwaSessionBridge]). If no client is set when a method fires, raise
 * [InvalidObjectException] so the caller knows to complete the handshake
 * first rather than silently receiving synthetic data.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload as SignInWithSolanaPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.InvalidObjectException

class LocalAdapterOperations @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    var client: MobileWalletAdapterClient? = null
) : AdapterOperations {

    private fun requireClient(): MobileWalletAdapterClient = client
        ?: throw InvalidObjectException(
            "MobileWalletAdapterClient has not been set on LocalAdapterOperations yet; " +
                "open an association scenario (LocalAssociationScenario.start() + " +
                "MwaSessionBridge.attach(scenario, adapter)) before invoking this method."
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
    ): AuthorizationResult = withContext(ioDispatcher) {
        val nested = requireClient().authorize(
            identityUri = identityUri,
            iconUri = iconUri,
            identityName = identityName,
            chain = chain,
            authToken = authToken,
            features = features?.toTypedArray(),
            addresses = addresses?.toTypedArray(),
            signInPayload = signInPayload
        ).asCompletableFuture().await()
        nested.toTopLevelResult()
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

    override suspend fun deauthorize(authToken: String) = withContext(ioDispatcher) {
        requireClient().deauthorize(authToken).asCompletableFuture().await()
        Unit
    }

    override suspend fun signTransactions(
        transactions: Array<ByteArray>
    ): MobileWalletAdapterClient.SignPayloadsResult = withContext(ioDispatcher) {
        @Suppress("DEPRECATION")
        requireClient().signTransactions(transactions).asCompletableFuture().await()
    }

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams?
    ): MobileWalletAdapterClient.SignAndSendTransactionsResult = withContext(ioDispatcher) {
        requireClient().signAndSendTransactions(
            transactions = transactions,
            minContextSlot = params?.minContextSlot,
            commitment = params?.commitment,
            skipPreflight = params?.skipPreflight,
            maxRetries = params?.maxRetries,
            waitForCommitmentToSendNextTransaction = params?.waitForCommitmentToSendNextTransaction
        ).asCompletableFuture().await()
    }

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): MobileWalletAdapterClient.SignMessagesResult = signMessagesDetached(messages, addresses)

    override suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): MobileWalletAdapterClient.SignMessagesResult = withContext(ioDispatcher) {
        requireClient().signMessagesDetached(messages, addresses)
            .asCompletableFuture().await()
    }

    /**
     * Bridge between the upstream `NotifyOnCompleteFuture` and
     * `kotlinx.coroutines.future.await`. The future is already a
     * `CompletableFuture` under the hood, so we just cast.
     */
    private fun <T> com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture<T>
        .asCompletableFuture(): java.util.concurrent.CompletableFuture<T> =
        this as java.util.concurrent.CompletableFuture<T>

    private fun MobileWalletAdapterClient.AuthorizationResult.toTopLevelResult(): AuthorizationResult {
        val topLevelAccounts = accounts.map { a ->
            AuthorizationResult.Account(
                publicKey = a.publicKey,
                displayAddress = a.displayAddress,
                displayAddressFormat = a.displayAddressFormat,
                label = a.accountLabel,
                chains = a.chains?.toList(),
                features = a.features?.toList()
            )
        }
        val siwsResult = signInResult?.let { s ->
            SignInResult(
                publicKey = s.publicKey,
                signedMessage = s.signedMessage,
                signature = s.signature,
                signatureType = s.signatureType
            )
        }
        return AuthorizationResult(
            authToken = authToken,
            publicKey = topLevelAccounts.firstOrNull()?.publicKey ?: ByteArray(0),
            accountLabel = topLevelAccounts.firstOrNull()?.label,
            walletUriBase = walletUriBase,
            walletIcon = walletIcon,
            accounts = topLevelAccounts,
            signInResult = siwsResult
        )
    }
}

/**
 * Backwards-compatibility extensions. Artemis callers that were reading raw
 * arrays off of the results keep the shortened shape via these extensions.
 */
val MobileWalletAdapterClient.SignPayloadsResult.payloads: Array<ByteArray>
    get() = signedPayloads

val MobileWalletAdapterClient.SignAndSendTransactionsResult.signatureStrings: Array<String>
    get() = signatures.map { com.selenus.artemis.runtime.Base58.encode(it) }.toTypedArray()
