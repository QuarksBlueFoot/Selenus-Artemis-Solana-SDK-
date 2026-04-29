/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.walletlib.scenario.
 *
 * Upstream's `Scenario` is a Java abstract class with `void start()`,
 * `CompletableFuture<String> startAsync()`, `void close()`, a
 * `byte[] associationPublicKey` field, and a nested `Callbacks`
 * interface with both lifecycle methods and per-RPC request callbacks.
 * The shim mirrors that exact shape and routes each call through to
 * the Artemis [LocalScenario] implementation in
 * [:artemis-wallet-mwa-walletlib-android].
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.scenario

import android.content.Context
import com.selenus.artemis.wallet.mwa.walletlib.AuthorizeRequest as ArtemisAuthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.DeauthorizedEvent as ArtemisDeauthorizedEvent
import com.selenus.artemis.wallet.mwa.walletlib.LocalScenario as ArtemisLocalScenario
import com.selenus.artemis.wallet.mwa.walletlib.ReauthorizeRequest as ArtemisReauthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.Scenario as ArtemisScenario
import com.selenus.artemis.wallet.mwa.walletlib.SignAndSendTransactionsRequest as ArtemisSignAndSendTransactionsRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignMessagesRequest as ArtemisSignMessagesRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignTransactionsRequest as ArtemisSignTransactionsRequest
import com.selenus.artemis.wallet.mwa.walletlib.DevicePowerConfigProvider
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository
import com.solana.mobilewalletadapter.walletlib.authorization.toArtemisAdapter
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract Scenario base. Mirrors the upstream walletlib shape so
 * subclasses in user code keep compiling.
 *
 * The upstream class exposes both a deprecated synchronous `start()`
 * and a `startAsync()` future. Both are surfaced here; `startAsync()`
 * returns a [CompletableFuture] that resolves to the session id when
 * the HELLO handshake succeeds and completes exceptionally on failure.
 */
abstract class Scenario protected constructor(
    @JvmField protected val mCallbacks: Callbacks,
    @JvmField val associationPublicKey: ByteArray
) {
    /** Deprecated upstream entry point. Calls through to [startAsync]. */
    @Deprecated(
        "Use startAsync() so callers can react to handshake failure.",
        ReplaceWith("startAsync()")
    )
    open fun start() {
        startAsync()
    }

    /**
     * Build a [Scenario]-id future. Upstream returns
     * `NotifyingCompletableFuture<String>`; we expose the JDK
     * [CompletableFuture] which is API-compatible for `.get()` /
     * `.thenAccept()` / `.exceptionally()` callers.
     */
    abstract fun startAsync(): CompletableFuture<String>

    abstract fun close()

    /**
     * Wallet-side callback set. Mirrors the upstream Java interface
     * exactly. every method is non-suspending so existing wallet
     * subclasses that implement `Callbacks` keep compiling without
     * touching coroutines.
     */
    interface Callbacks {
        fun onScenarioReady() {}
        fun onScenarioServingClients() {}
        fun onScenarioServingComplete() {}
        fun onScenarioComplete() {}
        fun onScenarioError() {}
        fun onScenarioTeardownComplete() {}
        fun onAuthorizeRequest(request: AuthorizeRequest)
        fun onReauthorizeRequest(request: ReauthorizeRequest)
        fun onSignTransactionsRequest(request: SignTransactionsRequest)
        fun onSignMessagesRequest(request: SignMessagesRequest)
        fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest)
        fun onDeauthorizedEvent(event: DeauthorizedEvent) {}
        fun onLowPowerAndNoConnection() {}
    }
}

/**
 * Local-association concrete scenario. Constructed with the parsed
 * URI plus the wallet's [MobileWalletAdapterConfig], identical to
 * the upstream Java constructor signature.
 *
 * Bridges into the Artemis [ArtemisLocalScenario] under the hood.
 */
open class LocalAssociationScenario private constructor(
    associationUri: LocalAssociationUri,
    private val config: MobileWalletAdapterConfig,
    callbacks: Callbacks,
    private val authRepository: AuthRepository,
    private val powerProvider: com.selenus.artemis.wallet.mwa.walletlib.PowerConfigProvider
) : Scenario(callbacks, associationUri.associationPublicKey) {

    /** Upstream constructor with full lifecycle wiring (auth + power). */
    @JvmOverloads
    constructor(
        context: Context,
        associationUri: LocalAssociationUri,
        config: MobileWalletAdapterConfig,
        authIssuerConfig: AuthIssuerConfig,
        callbacks: Callbacks,
        authRepository: AuthRepository =
            com.solana.mobilewalletadapter.walletlib.authorization.InMemoryAuthRepository(authIssuerConfig)
    ) : this(
        associationUri = associationUri,
        config = config,
        callbacks = callbacks,
        authRepository = authRepository,
        powerProvider = DevicePowerConfigProvider(context)
    )

    /**
     * Lower-level constructor used by tests + samples that don't have
     * a Context handy. Uses the in-memory repo and never fires the
     * low-power warning so unit tests do not need to stub
     * `PowerManager`.
     */
    @JvmOverloads
    constructor(
        associationUri: LocalAssociationUri,
        config: MobileWalletAdapterConfig,
        callbacks: Callbacks,
        authRepository: AuthRepository =
            com.solana.mobilewalletadapter.walletlib.authorization.InMemoryAuthRepository(
                AuthIssuerConfig(name = "Wallet")
            )
    ) : this(
        associationUri = associationUri,
        config = config,
        callbacks = callbacks,
        authRepository = authRepository,
        powerProvider = com.selenus.artemis.wallet.mwa.walletlib.PowerConfigProvider.AlwaysHighPower
    )

    private val artemis: ArtemisLocalScenario = ArtemisLocalScenario(
        associationUri = associationUri.toArtemis(),
        config = config.toArtemis(),
        authRepository = authRepository.toArtemisAdapter(),
        powerConfigProvider = powerProvider
    )

    /**
     * Lifecycle scope owned by this scenario. start() launches the
     * suspend bridge on it; close() cancels it deterministically so
     * UI flows do not leak past teardown.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    override fun startAsync(): CompletableFuture<String> {
        return scope.future {
            try {
                if (!started.compareAndSet(false, true)) {
                    error("startAsync() already invoked")
                }
                artemis.start(buildArtemisCallbacks())
            } catch (t: Throwable) {
                mCallbacks.onScenarioError()
                throw t
            }
        }
    }

    override fun start() {
        // Fire-and-forget bridge for the upstream void start().
        // Errors are surfaced through `onScenarioError`.
        startAsync()
    }

    override fun close() {
        runBlocking { artemis.close() }
        scope.cancel()
    }

    private fun buildArtemisCallbacks() = object : ArtemisScenario.Callbacks {
        override fun onScenarioReady() = mCallbacks.onScenarioReady()
        override fun onScenarioServingClients() = mCallbacks.onScenarioServingClients()
        override fun onScenarioServingComplete() = mCallbacks.onScenarioServingComplete()
        override fun onScenarioComplete() = mCallbacks.onScenarioComplete()
        override fun onScenarioError(t: Throwable) = mCallbacks.onScenarioError()
        override fun onScenarioTeardownComplete() = mCallbacks.onScenarioTeardownComplete()

        override suspend fun onAuthorizeRequest(request: ArtemisAuthorizeRequest) {
            mCallbacks.onAuthorizeRequest(AuthorizeRequest(request))
        }

        override suspend fun onReauthorizeRequest(request: ArtemisReauthorizeRequest) {
            mCallbacks.onReauthorizeRequest(ReauthorizeRequest(request))
        }

        override suspend fun onSignTransactionsRequest(request: ArtemisSignTransactionsRequest) {
            mCallbacks.onSignTransactionsRequest(SignTransactionsRequest(request))
        }

        override suspend fun onSignMessagesRequest(request: ArtemisSignMessagesRequest) {
            mCallbacks.onSignMessagesRequest(SignMessagesRequest(request))
        }

        override suspend fun onSignAndSendTransactionsRequest(
            request: ArtemisSignAndSendTransactionsRequest
        ) {
            mCallbacks.onSignAndSendTransactionsRequest(SignAndSendTransactionsRequest(request))
        }

        override fun onDeauthorizedEvent(event: ArtemisDeauthorizedEvent) {
            mCallbacks.onDeauthorizedEvent(DeauthorizedEvent(event))
        }

        override fun onLowPowerAndNoConnection() = mCallbacks.onLowPowerAndNoConnection()
    }
}

/**
 * Reflector / cross-device association scenario. Upstream walletlib
 * ships this on the wallet side for the QR-pair flow; the Artemis
 * walletlib does not yet implement the reflector loop, so the scenario
 * is a stub that fires `onScenarioError()` when started. Surfacing the
 * FQN keeps source-level compatibility for code that branches on
 * `is RemoteWebSocketServerScenario`; runtime use should be gated.
 */
open class RemoteWebSocketServerScenario(
    associationUri: RemoteAssociationUri,
    @Suppress("unused") private val config: MobileWalletAdapterConfig,
    @Suppress("unused") private val authIssuerConfig: AuthIssuerConfig,
    callbacks: Callbacks
) : Scenario(callbacks, associationUri.associationPublicKey) {

    override fun startAsync(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        future.completeExceptionally(
            UnsupportedOperationException(
                "RemoteWebSocketServerScenario is not yet implemented in Artemis walletlib. " +
                    "Track the reflector module in advanced/artemis-streaming."
            )
        )
        mCallbacks.onScenarioError()
        return future
    }

    override fun close() { /* no-op */ }
}
