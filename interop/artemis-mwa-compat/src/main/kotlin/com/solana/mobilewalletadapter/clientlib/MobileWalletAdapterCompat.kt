/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for the Solana Mobile MWA client library.
 * Provides familiar API surface backed by Artemis MWA implementation.
 */
package com.solana.mobilewalletadapter.clientlib

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.selenus.artemis.wallet.mwa.AuthTokenStore
import com.selenus.artemis.wallet.mwa.InMemoryAuthTokenStore
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload as SignInWithSolanaPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Drop-in replacement for `com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter`.
 *
 * Wraps an Artemis [MwaWalletAdapter] with the upstream constructor pattern.
 * The [transact] method matches the official high-level API, executing an
 * [AdapterOperations] block inside an auto-managed wallet session.
 */
class MobileWalletAdapter(
    private val connectionIdentity: ConnectionIdentity,
    private val blockchain: Blockchain = Solana.Mainnet,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val authStore: AuthTokenStore = InMemoryAuthTokenStore()
) {
    @Volatile
    var authToken: String? = null
        internal set

    /**
     * Execute a block of wallet operations inside an auto-managed MWA session.
     *
     * This is the primary entry point matching the upstream `transact()` API.
     *
     * @param sender The [ActivityResultSender] registered in the host Activity
     * @param signInPayload Optional Sign-In With Solana payload
     * @param block The operations to perform; receives [AuthorizationResult] after auth
     * @return [TransactionResult] wrapping the block's return value
     */
    suspend fun <T> transact(
        sender: ActivityResultSender,
        signInPayload: SignInWithSolanaPayload? = null,
        block: suspend AdapterOperations.(authResult: AuthorizationResult) -> T
    ): TransactionResult<T> {
        return try {
            // Create a temporary Activity-backed adapter using the sender's context.
            // The sender wraps a ComponentActivity, but we need an Activity for MwaWalletAdapter.
            // We extract the Activity from the registered launcher's lifecycle owner.
            val adapter = createAdapter(sender)

            val pubkey = adapter.connect()

            val authResult = AuthorizationResult(
                authToken = authStore.get() ?: "",
                publicKey = pubkey.bytes,
                accountLabel = null,
                walletUriBase = null
            )

            authToken = authResult.authToken

            val operations = MwaAdapterOperations(
                adapter = adapter,
                identity = connectionIdentity,
                blockchain = blockchain,
                authStore = authStore
            )

            val payload = operations.block(authResult)

            TransactionResult.Success(payload, authResult)
        } catch (e: Exception) {
            TransactionResult.Failure(e.message ?: "MWA transaction failed", e)
        }
    }

    /**
     * Simple connection that returns the public key.
     */
    suspend fun connect(sender: ActivityResultSender): ByteArray {
        val result = transact(sender) { it.publicKey }
        return when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.Failure -> throw result.e ?: Exception(result.message)
            is TransactionResult.NoWalletFound -> throw Exception(result.message)
        }
    }

    /**
     * Disconnect (clear stored auth token).
     */
    fun disconnect() {
        authToken = null
        authStore.set(null)
    }

    private fun createAdapter(sender: ActivityResultSender): MwaWalletAdapter {
        return MwaWalletAdapter(
            activity = sender.hostActivity,
            identityUri = connectionIdentity.identityUri,
            iconPath = connectionIdentity.iconUri.toString(),
            identityName = connectionIdentity.identityName,
            chain = blockchain.fullName,
            authStore = authStore
        )
    }
}

/**
 * Internal implementation of [AdapterOperations] delegating to [MwaWalletAdapter].
 */
internal class MwaAdapterOperations(
    private val adapter: MwaWalletAdapter,
    private val identity: ConnectionIdentity,
    private val blockchain: Blockchain,
    private val authStore: AuthTokenStore
) : AdapterOperations {

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
        val pubkey = adapter.connect()
        return AuthorizationResult(
            authToken = authStore.get() ?: "",
            publicKey = pubkey.bytes,
            accountLabel = null,
            walletUriBase = null
        )
    }

    override suspend fun reauthorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        authToken: String
    ): AuthorizationResult {
        adapter.reauthorize()
        return AuthorizationResult(
            authToken = authStore.get() ?: "",
            publicKey = adapter.publicKey.bytes,
            accountLabel = null,
            walletUriBase = null
        )
    }

    override suspend fun deauthorize(authToken: String) {
        adapter.disconnect()
    }

    override suspend fun signTransactions(transactions: Array<ByteArray>): Array<ByteArray> {
        val results = adapter.signMessages(
            transactions.toList(),
            com.selenus.artemis.wallet.SignTxRequest(purpose = "signTransactions")
        )
        return results.toTypedArray()
    }

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams?
    ): Array<SignatureResult> {
        val options = com.selenus.artemis.wallet.SendTransactionOptions(
            skipPreflight = params?.skipPreflight ?: false,
            maxRetries = params?.maxRetries,
            minContextSlot = params?.minContextSlot?.toLong()
        )
        val batchResult = adapter.signAndSendTransactions(transactions.toList(), options)
        return batchResult.results.map { result ->
            SignatureResult(signature = result.signature, commitment = params?.commitment)
        }.toTypedArray()
    }

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignedMessageResult> = signMessagesDetached(messages, addresses)

    override suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignedMessageResult> {
        // Route through the Artemis MWA client which speaks the MWA 2.0
        // sign_messages RPC end-to-end and returns detached signatures.
        return messages.map { msg ->
            val sig = adapter.signArbitraryMessage(
                msg,
                com.selenus.artemis.wallet.SignTxRequest(purpose = "signMessage")
            )
            SignedMessageResult(message = msg, signatures = listOf(sig))
        }.toTypedArray()
    }

    override suspend fun getCapabilities(): GetCapabilitiesResult {
        // The Artemis MwaWalletAdapter exposes WalletCapabilities, which is a
        // superset of MWA 2.0 GetCapabilitiesResult plus a few Artemis-specific
        // feature flags. Map the subset that the upstream API surface defines.
        val caps = runCatching { adapter.getCapabilities() }.getOrNull()
        val versions = buildList {
            if (caps?.supportsLegacyTransactions != false) add("legacy")
            if (caps?.supportsVersionedTransactions != false) add("0")
        }
        return GetCapabilitiesResult(
            supportsCloneAuthorization = caps?.supportsCloneAuthorization ?: false,
            supportsSignAndSendTransactions = caps?.supportsSignAndSend ?: true,
            maxTransactionsPerSigningRequest = caps?.maxTransactionsPerRequest ?: 0,
            maxMessagesPerSigningRequest = caps?.maxMessagesPerRequest ?: 0,
            supportedTransactionVersions = versions
        )
    }
}

/**
 * Convenience factory for creating [MwaWalletAdapter] directly.
 *
 * Use this when you have an Activity reference and want the Artemis-native
 * adapter without the upstream compat API surface.
 */
object MobileWalletAdapterCompat {
    fun create(
        activity: Activity,
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        cluster: String = "solana:mainnet",
        authStore: AuthTokenStore = InMemoryAuthTokenStore()
    ): MwaWalletAdapter = MwaWalletAdapter(
        activity = activity,
        identityUri = identityUri,
        iconPath = iconUri.toString(),
        identityName = identityName,
        chain = cluster,
        authStore = authStore
    )
}
