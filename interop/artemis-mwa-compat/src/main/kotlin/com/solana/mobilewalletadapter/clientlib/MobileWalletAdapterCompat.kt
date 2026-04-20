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
class MobileWalletAdapter @JvmOverloads constructor(
    private val connectionIdentity: ConnectionIdentity,
    @Suppress("UNUSED_PARAMETER") timeout: Int = DEFAULT_CLIENT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @Suppress("UNUSED_PARAMETER") scenarioProvider: AssociationScenarioProvider =
        AssociationScenarioProvider()
) {
    // Artemis-specific state (blockchain + auth store) captured as private
    // fields. These used to be primary-ctor parameters; moving them to a
    // secondary ctor so the primary signature matches upstream exactly.
    private var blockchain: Blockchain = Solana.Mainnet
    private var authStore: AuthTokenStore = InMemoryAuthTokenStore()

    /**
     * Artemis-extended constructor: picks the chain and auth store. Keeps
     * binary compatibility with existing Artemis apps that used the previous
     * primary form `(ci, blockchain, ioDispatcher, authStore)`.
     */
    constructor(
        connectionIdentity: ConnectionIdentity,
        blockchain: Blockchain,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        authStore: AuthTokenStore = InMemoryAuthTokenStore()
    ) : this(
        connectionIdentity = connectionIdentity,
        timeout = DEFAULT_CLIENT_TIMEOUT_MS,
        ioDispatcher = ioDispatcher
    ) {
        this.blockchain = blockchain
        this.authStore = authStore
    }

    companion object {
        /** Matches upstream `Scenario.DEFAULT_CLIENT_TIMEOUT_MS`. */
        const val DEFAULT_CLIENT_TIMEOUT_MS: Int = 90_000
    }

    @Volatile
    var authToken: String? = null
        internal set

    /**
     * Live session handles set while a [transact] block is executing. Exposed
     * so [disconnect] can genuinely deauthorize through the same wallet
     * session instead of silently dropping the local token.
     */
    @Volatile
    private var liveAdapter: com.selenus.artemis.wallet.mwa.MwaWalletAdapter? = null
    @Volatile
    private var liveBridge: MwaSessionBridge? = null

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
        val adapter = createAdapter(sender)
        liveAdapter = adapter
        // Install a bridge onto the low-level clientlib so any code that
        // reaches into it (e.g. scenario.getMobileWalletAdapterClient())
        // sees live wallet behaviour instead of placeholder futures.
        val bridge = MwaSessionBridge(adapter)
        liveBridge = bridge
        return try {
            val pubkey = if (signInPayload != null) {
                adapter.connectWithSignIn(signInPayload.toMwa())
                adapter.publicKey
            } else {
                adapter.connect()
            }

            val raw = adapter.lastAuthorization
            val walletUriBaseStr = raw?.walletUriBase
            val walletIconStr = raw?.walletIcon
            val accounts = raw?.accounts.orEmpty().map { acc ->
                AuthorizationResult.Account(
                    publicKey = android.util.Base64.decode(
                        acc.address,
                        android.util.Base64.NO_WRAP
                    ),
                    displayAddress = acc.displayAddress,
                    displayAddressFormat = acc.displayAddressFormat,
                    label = acc.label,
                    chains = acc.chains,
                    features = acc.features
                )
            }
            val signInResultNative = raw?.signInResult?.let { sir ->
                SignInResult(
                    publicKey = android.util.Base64.decode(sir.address, android.util.Base64.NO_WRAP),
                    signedMessage = android.util.Base64.decode(sir.signedMessage, android.util.Base64.NO_WRAP),
                    signature = android.util.Base64.decode(sir.signature, android.util.Base64.NO_WRAP),
                    signatureType = sir.signatureType
                )
            }
            val authResult = AuthorizationResult(
                authToken = raw?.authToken ?: authStore.get() ?: "",
                publicKey = pubkey.bytes,
                accountLabel = accounts.firstOrNull()?.label,
                walletUriBase = walletUriBaseStr?.let { android.net.Uri.parse(it) },
                walletIcon = walletIconStr?.let { android.net.Uri.parse(it) },
                accounts = accounts,
                signInResult = signInResultNative
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
        } finally {
            // Keep the bridge attached until the caller tears down. The live
            // handles are used by [disconnect] to issue a real deauthorize
            // and then released; if the caller never calls disconnect, the
            // next `transact` replaces the handles cleanly.
        }
    }

    /**
     * Opens an MWA session and authorizes the dapp. Returns
     * [TransactionResult.Success] with the empty payload when the wallet
     * returns, matching upstream's MWA 2.0 shape.
     */
    suspend fun connect(sender: ActivityResultSender): TransactionResult<Unit> =
        transact(sender) { /* authorize already ran */ }

    /**
     * Authorizes with a Sign-In With Solana payload and returns the resulting
     * [SignInResult]. Mirrors `com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter.signIn`.
     */
    suspend fun signIn(
        sender: ActivityResultSender,
        signInPayload: SignInWithSolanaPayload
    ): TransactionResult<SignInResult> {
        val authTxResult = transact(sender, signInPayload) { authResult ->
            authResult.signInResult
                ?: throw IllegalStateException(
                    "Wallet did not return a sign_in_result for the Sign-In With Solana payload"
                )
        }
        return when (authTxResult) {
            is TransactionResult.Success -> TransactionResult.Success(authTxResult.payload)
            is TransactionResult.Failure -> TransactionResult.Failure(
                authTxResult.message,
                authTxResult.e ?: Exception(authTxResult.message)
            )
            is TransactionResult.NoWalletFound -> TransactionResult.NoWalletFound(authTxResult.message)
        }
    }

    /**
     * Ends the MWA session. If a [transact] block is still holding a live
     * adapter, this method dispatches `deauthorize` through the wallet so
     * the wallet actually invalidates the auth token, then tears down the
     * local session bridge and clears persisted state. Matches upstream's
     * behaviour where disconnect is a full wallet-side notification.
     *
     * When no live adapter is available (caller never ran [transact]) the
     * method falls back to opening a fresh session with [sender] and
     * deauthorizing explicitly, so the stored token does not leak.
     */
    suspend fun disconnect(sender: ActivityResultSender): TransactionResult<Unit> {
        val token = authStore.get()
        return try {
            val adapter = liveAdapter ?: createAdapter(sender).also { fresh ->
                if (!token.isNullOrEmpty()) {
                    runCatching { fresh.reauthorize() }
                }
                liveAdapter = fresh
            }
            runCatching { adapter.deauthorize() }
            runCatching { adapter.disconnect() }
            liveBridge?.close()
            liveBridge = null
            liveAdapter = null
            authToken = null
            authStore.set(null)
            TransactionResult.Success(Unit)
        } catch (e: Exception) {
            // Even on failure, wipe local state: a stale token that the
            // wallet still trusts is strictly worse than a stale token that
            // the wallet doesn't.
            liveBridge?.close()
            liveBridge = null
            liveAdapter = null
            authToken = null
            authStore.set(null)
            TransactionResult.Failure(e.message ?: "MWA disconnect failed", e)
        }
    }

    /** Pre-2.0 sync disconnect. Kept for existing Artemis callers. */
    fun disconnect() {
        liveBridge?.close()
        liveBridge = null
        liveAdapter = null
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

    override suspend fun signTransactions(
        transactions: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadsResult {
        val results = adapter.signMessages(
            transactions.toList(),
            com.selenus.artemis.wallet.SignTxRequest(purpose = "signTransactions")
        )
        return com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
            .SignPayloadsResult(results.toTypedArray())
    }

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams?
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionsResult {
        val options = com.selenus.artemis.wallet.SendTransactionOptions(
            skipPreflight = params?.skipPreflight ?: false,
            maxRetries = params?.maxRetries,
            minContextSlot = params?.minContextSlot?.toLong()
        )
        val batchResult = adapter.signAndSendTransactions(transactions.toList(), options)
        val sigBytes = batchResult.results.map {
            com.selenus.artemis.runtime.Base58.decode(it.signature)
        }.toTypedArray()
        return com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
            .SignAndSendTransactionsResult(sigBytes)
    }

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignMessagesResult =
        signMessagesDetached(messages, addresses)

    override suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignMessagesResult {
        // Route through the Artemis MWA client which speaks the MWA 2.0
        // sign_messages RPC end-to-end and returns detached signatures.
        val signed = messages.map { msg ->
            val sig = adapter.signArbitraryMessage(
                msg,
                com.selenus.artemis.wallet.SignTxRequest(purpose = "signMessage")
            )
            com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
                .SignMessagesResult.SignedMessage(
                    message = msg,
                    signatures = arrayOf(sig),
                    addresses = addresses
                )
        }.toTypedArray()
        return com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
            .SignMessagesResult(signed)
    }

    override suspend fun getCapabilities(): GetCapabilitiesResult {
        // The Artemis MwaWalletAdapter exposes WalletCapabilities, which is a
        // superset of MWA 2.0 GetCapabilitiesResult plus a few Artemis-specific
        // feature flags. Map the subset that the upstream API surface defines.
        val caps = runCatching { adapter.getCapabilities() }.getOrNull()
        val versions = buildList<Any> {
            if (caps?.supportsLegacyTransactions != false) add("legacy")
            if (caps?.supportsVersionedTransactions != false) add(0)
        }
        return GetCapabilitiesResult(
            supportsCloneAuthorization = caps?.supportsCloneAuthorization ?: false,
            supportsSignAndSendTransactions = caps?.supportsSignAndSend ?: true,
            maxTransactionsPerSigningRequest = caps?.maxTransactionsPerRequest ?: 0,
            maxMessagesPerSigningRequest = caps?.maxMessagesPerRequest ?: 0,
            supportedTransactionVersions = versions.toTypedArray(),
            supportedOptionalFeatures = emptyArray()
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
