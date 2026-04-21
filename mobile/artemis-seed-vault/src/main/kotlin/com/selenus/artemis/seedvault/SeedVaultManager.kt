package com.selenus.artemis.seedvault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.seedvault.internal.SeedVaultConstants
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_CREATE_SEED
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.ACTION_IMPORT_SEED
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.SERVICE_PACKAGE
import com.selenus.artemis.seedvault.internal.ipc.ISeedVaultService
import com.selenus.artemis.seedvault.internal.ipc.ISeedVaultCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * SeedVaultManager
 *
 * Coroutine-first entry point for the Solana Seed Vault. Owns the binder
 * lifecycle and exposes typed verbs. The typed surface is implemented by
 * [SeedVaultAccountProviderImpl] and [SeedVaultSigningProviderImpl]; the
 * manager is a facade that composes them:
 *
 *   ServiceBinderContract: raw IPC (this file, internal)
 *        v
 *   SeedVaultAccountProvider / SeedVaultSigningProvider: typed IO, response validation
 *        v
 *   SeedVaultManager.getAccounts / signTransactions / signMessages / ... : public API
 *
 * Binder-death / disconnect semantics are deterministic: every in-flight
 * call is tracked; if the binder dies or the system service disconnects,
 * all pending continuations fail immediately with
 * [SeedVaultException.ServiceUnavailable] instead of hanging until the
 * per-call timeout.
 */
class SeedVaultManager(private val context: Context) {

    @Volatile private var serviceBinder: IBinder? = null
    @Volatile private var service: ISeedVaultService? = null
    @Volatile private var isBound = false

    private val pendingBindWaiters =
        mutableListOf<Continuation<ISeedVaultService>>()
    private val bindLock = Any()

    /**
     * In-flight IPC calls. Keyed by a monotonically-increasing request id
     * so binder death / disconnect can enumerate every outstanding
     * continuation and fail it with a typed error. The map is populated
     * inside [performActionInner] before the AIDL verb is dispatched and
     * cleared by the [ISeedVaultCallback.Stub] on success or error.
     */
    private val pendingRequests = ConcurrentHashMap<Long, PendingRequest>()
    private val nextRequestId = AtomicLong(1L)

    /** Snapshot of a pending IPC call used when failing it on disconnect. */
    private data class PendingRequest(
        val method: String,
        val cont: Continuation<Bundle>
    )

    private val binderDeathRecipient = IBinder.DeathRecipient {
        failAllPending("Seed Vault binder died")
        synchronized(bindLock) {
            serviceBinder = null
            service = null
            isBound = false
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = binder?.let { ISeedVaultService.Stub.asInterface(it) }
            runCatching { binder?.linkToDeath(binderDeathRecipient, 0) }
            val waiters = synchronized(bindLock) {
                serviceBinder = binder
                service = svc
                val snapshot = pendingBindWaiters.toList()
                pendingBindWaiters.clear()
                snapshot
            }
            waiters.forEach { cont ->
                if (svc != null) cont.resume(svc)
                else cont.resumeWithException(
                    IllegalStateException("Seed Vault returned a null binder")
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            failAllPending("Seed Vault service disconnected")
            synchronized(bindLock) {
                serviceBinder = null
                service = null
                isBound = false
            }
        }
    }

    /**
     * Internal [SeedVaultContractClient] backed by the bound binder. Every
     * AIDL verb routes through [performAction] so binder-death handling is
     * uniform. The providers (account / signing) are constructed on this
     * contract; [SeedVaultManager] delegates its typed verbs to them so
     * the Manager is a thin facade instead of a god-object.
     */
    private val binderContract: SeedVaultContractClient = object : SeedVaultContractClient {
        override suspend fun authorize(params: Bundle) = performAction("authorize", params)
        override suspend fun createSeed(params: Bundle) = performAction("createSeed", params)
        override suspend fun importSeed(params: Bundle) = performAction("importSeed", params)
        override suspend fun updateSeed(params: Bundle) = performAction("updateSeed", params)
        override suspend fun getAccounts(params: Bundle) = performAction("getAccounts", params)
        override suspend fun resolveDerivationPath(params: Bundle) =
            performAction("resolveDerivationPath", params)
        override suspend fun signTransactions(params: Bundle) =
            performAction("signTransactions", params)
        override suspend fun signMessages(params: Bundle) = performAction("signMessages", params)
        override suspend fun deauthorize(params: Bundle) = performAction("deauthorize", params)
    }

    private val accountProvider: SeedVaultAccountProvider = SeedVaultAccountProviderImpl(binderContract)
    private val signingProvider: SeedVaultSigningProvider = SeedVaultSigningProviderImpl(binderContract)

    companion object {
        private const val ACTION_BIND_SEED_VAULT = SeedVaultConstants.ACTION_BIND_SEED_VAULT

        /**
         * Default per-IPC timeout in milliseconds. The Seed Vault system
         * service can stall (binder death, provider misbehaviour,
         * Doze-induced throttling); without a timeout callers hang forever.
         */
        const val DEFAULT_IPC_TIMEOUT_MS: Long = 30_000

        /** Resolves the component for the Intent using Artemis internal check logic. */
        fun resolveComponent(context: Context, intent: Intent) {
             com.selenus.artemis.seedvault.internal.SeedVaultCheck.resolveComponentForIntent(context, intent)
        }

        /**
         * Parse [authToken] into the underlying long identifier. Throws
         * immediately on malformed input rather than coercing silently to
         * the sentinel `-1L`, which used to produce "auth token not found"
         * errors one IPC later.
         */
        @JvmStatic
        fun parseAuthTokenStrict(authToken: String): Long =
            authToken.toLongOrNull()
                ?: throw SeedVaultException.Unknown(
                    "Seed Vault authToken must be a decimal long, got '$authToken'"
                )
    }

    /** Expose the internal contract client for tests and advanced callers. */
    internal fun contractClient(): SeedVaultContractClient = binderContract

    fun connect() {
        if (isBound) return
        val intent = Intent(ACTION_BIND_SEED_VAULT).apply {
            setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
        }
        resolveComponent(context, intent)
        isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    suspend fun connectSuspending(): ISeedVaultService = suspendCancellableCoroutine { cont ->
        synchronized(bindLock) {
            service?.let { existing ->
                cont.resume(existing)
                return@suspendCancellableCoroutine
            }
            pendingBindWaiters.add(cont)
            if (!isBound) {
                val intent = Intent(ACTION_BIND_SEED_VAULT).apply {
                    setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
                }
                resolveComponent(context, intent)
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    pendingBindWaiters.remove(cont)
                    cont.resumeWithException(
                        IllegalStateException("Seed Vault bindService returned false")
                    )
                    return@suspendCancellableCoroutine
                }
                isBound = true
            }
        }
        cont.invokeOnCancellation {
            synchronized(bindLock) { pendingBindWaiters.remove(cont) }
        }
    }

    fun disconnect() {
        failAllPending("Seed Vault disconnect() called by host")
        val binder = serviceBinder
        if (isBound) {
            runCatching { binder?.unlinkToDeath(binderDeathRecipient, 0) }
            context.unbindService(connection)
            isBound = false
            service = null
            serviceBinder = null
        }
    }

    /**
     * Fail every in-flight continuation with a typed ServiceUnavailable
     * error. Invoked from [onServiceDisconnected], from [binderDeathRecipient],
     * and from [disconnect]. Callers waiting on any IPC get an immediate
     * error instead of hanging until [DEFAULT_IPC_TIMEOUT_MS].
     */
    private fun failAllPending(reason: String) {
        val snapshot = pendingRequests.toMap()
        pendingRequests.clear()
        snapshot.values.forEach { pending ->
            runCatching {
                pending.cont.resumeWithException(
                    SeedVaultException.ServiceUnavailable(
                        "Seed Vault ${pending.method} aborted: $reason"
                    )
                )
            }
        }
    }

    fun buildAuthorizeIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_AUTHORIZE_SEED_ACCESS).apply {
            setPackage(SERVICE_PACKAGE)
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildCreateSeedIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_CREATE_SEED).apply {
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildImportSeedIntent(purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION): Intent {
        val intent = Intent(ACTION_IMPORT_SEED).apply {
            putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildSignTransactionsIntent(
        authToken: Long,
        signingRequests: ArrayList<android.os.Parcelable>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SIGN_TRANSACTION).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildSignMessagesIntent(
        authToken: Long,
        signingRequests: ArrayList<android.os.Parcelable>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SIGN_MESSAGE).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildGetPublicKeysIntent(
        authToken: Long,
        derivationPaths: ArrayList<android.net.Uri>
    ): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_GET_PUBLIC_KEY).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            putParcelableArrayListExtra(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPaths)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun buildSeedSettingsIntent(authToken: Long): Intent {
        val intent = Intent(SeedVaultConstants.ACTION_SEED_SETTINGS).apply {
            putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
        }
        resolveComponent(context, intent)
        return intent
    }

    fun parseAuthorizationResult(data: Intent): SeedVaultTokenResult {
        val token = data.getLongExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, -1L)
        if (token == -1L) throw SeedVaultException.Unknown("Invalid auth token in result")
        val accountId = data.getLongExtra(SeedVaultConstants.EXTRA_ACCOUNT_ID, 0L)
        return SeedVaultTokenResult(token, accountId)
    }

    suspend fun resolveAuthorization(result: SeedVaultTokenResult): SeedVaultAuthorization {
        val accounts = getAccounts(result.authToken.toString())
        val account = accounts.firstOrNull { it.id == result.accountId }
            ?: accounts.firstOrNull()
            ?: throw SeedVaultException.Unknown("No accounts found for auth token ${result.authToken}")
        return SeedVaultAuthorization(result.authToken.toString(), account)
    }

    fun parseSigningResult(data: Intent): List<SigningResponse> {
        val responses = data.getParcelableArrayListExtra<SigningResponse>(SeedVaultConstants.EXTRA_SIGNING_RESPONSE)
        return responses ?: emptyList()
    }

    fun parsePublicKeyResult(data: Intent): List<PublicKeyResponse> {
        val keys = data.getParcelableArrayListExtra<PublicKeyResponse>(SeedVaultConstants.EXTRA_PUBLIC_KEY)
        return keys ?: emptyList()
    }

    // Typed verbs, delegated to providers.

    suspend fun getAccounts(authToken: String): List<SeedVaultAccount> =
        accountProvider.getAccounts(authToken)

    suspend fun requestPublicKeys(
        authToken: String,
        derivationPaths: List<android.net.Uri>
    ): List<Pubkey> = accountProvider.requestPublicKeys(authToken, derivationPaths)

    suspend fun resolveDerivationPath(
        authToken: String,
        derivationPath: android.net.Uri
    ): Pubkey = accountProvider.resolveDerivationPath(authToken, derivationPath)

    suspend fun signTransactions(
        authToken: String,
        transactions: List<ByteArray>
    ): List<ByteArray> = signingProvider.signTransactions(authToken, transactions)

    suspend fun signMessages(
        authToken: String,
        messages: List<ByteArray>
    ): List<ByteArray> = signingProvider.signMessages(authToken, messages)

    suspend fun signWithDerivationPath(
        authToken: String,
        derivationPath: android.net.Uri,
        payloads: List<ByteArray>
    ): List<ByteArray> = signingProvider.signWithDerivationPath(authToken, derivationPath, payloads)

    suspend fun deauthorize(authToken: String) {
        val params = Bundle().apply {
            putLong(SeedVaultConstants.EXTRA_AUTH_TOKEN, parseAuthTokenStrict(authToken))
        }
        binderContract.deauthorize(params)
    }

    // ─── Internal IPC dispatch ────────────────────────────────────────────

    private suspend fun performAction(
        method: String,
        params: Bundle,
        timeoutMs: Long = DEFAULT_IPC_TIMEOUT_MS
    ): Bundle = try {
        withTimeout(timeoutMs) { performActionInner(method, params) }
    } catch (e: TimeoutCancellationException) {
        throw SeedVaultException.Unknown(
            "Seed Vault $method timed out after ${timeoutMs}ms"
        )
    }

    private suspend fun performActionInner(
        method: String,
        params: Bundle
    ): Bundle = suspendCancellableCoroutine { cont ->
        val requestId = nextRequestId.getAndIncrement()
        try {
            val svc = service ?: throw SeedVaultException.ServiceUnavailable(
                "Seed Vault not connected. Call connect() or connectSuspending() first."
            )

            pendingRequests[requestId] = PendingRequest(method, cont)
            cont.invokeOnCancellation { pendingRequests.remove(requestId) }

            val callback = object : ISeedVaultCallback.Stub() {
                override fun onResponse(response: Bundle) {
                    val pending = pendingRequests.remove(requestId) ?: return
                    if (pending.cont.context[kotlinx.coroutines.Job]?.isActive == true) {
                        pending.cont.resume(response)
                    }
                }
                override fun onError(error: Bundle) {
                    val pending = pendingRequests.remove(requestId) ?: return
                    if (pending.cont.context[kotlinx.coroutines.Job]?.isActive == true) {
                        pending.cont.resumeWithException(SeedVaultException.fromBundle(error))
                    }
                }
            }

            when (method) {
                "authorize" -> svc.authorize(params, callback)
                "createSeed" -> svc.createSeed(params, callback)
                "importSeed" -> svc.importSeed(params, callback)
                "updateSeed" -> svc.updateSeed(params, callback)
                "getAccounts" -> svc.getAccounts(params, callback)
                "resolveDerivationPath" -> svc.resolveDerivationPath(params, callback)
                "signTransactions" -> svc.signTransactions(params, callback)
                "signMessages" -> svc.signMessages(params, callback)
                "deauthorize" -> svc.deauthorize(params, callback)
                else -> {
                    pendingRequests.remove(requestId)
                    cont.resumeWithException(IllegalArgumentException("Unknown method: $method"))
                }
            }
        } catch (e: RemoteException) {
            pendingRequests.remove(requestId)
            if (cont.isActive) {
                cont.resumeWithException(
                    SeedVaultException.ServiceUnavailable("Seed Vault $method: ${e.message}")
                )
            }
        } catch (e: SeedVaultException) {
            pendingRequests.remove(requestId)
            if (cont.isActive) cont.resumeWithException(e)
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            if (cont.isActive) {
                cont.resumeWithException(
                    SeedVaultException.InternalError("Seed Vault $method: ${e.message}")
                )
            }
        }
    }
}
