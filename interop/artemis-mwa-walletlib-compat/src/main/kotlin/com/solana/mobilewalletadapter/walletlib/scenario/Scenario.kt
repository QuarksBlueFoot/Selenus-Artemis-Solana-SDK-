/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.walletlib.scenario.
 *
 * Upstream's `Scenario` is a Java abstract class with `void start()`,
 * `void close()`, a `byte[] associationPublicKey` field, and a nested
 * `Callbacks` interface with both lifecycle methods and per-RPC
 * request callbacks. The shim mirrors that exact shape and routes
 * each call through to the Artemis [LocalScenario]
 * implementation in [:artemis-wallet-mwa-walletlib-android].
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.scenario

import com.selenus.artemis.wallet.mwa.walletlib.AuthorizeRequest as ArtemisAuthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.DeauthorizedEvent as ArtemisDeauthorizedEvent
import com.selenus.artemis.wallet.mwa.walletlib.LocalScenario as ArtemisLocalScenario
import com.selenus.artemis.wallet.mwa.walletlib.MobileWalletAdapterConfig as ArtemisConfig
import com.selenus.artemis.wallet.mwa.walletlib.ReauthorizeRequest as ArtemisReauthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.Scenario as ArtemisScenario
import com.selenus.artemis.wallet.mwa.walletlib.SignAndSendTransactionsRequest as ArtemisSignAndSendTransactionsRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignMessagesRequest as ArtemisSignMessagesRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignTransactionsRequest as ArtemisSignTransactionsRequest
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig as CompatMobileWalletAdapterConfig
import com.selenus.artemis.wallet.mwa.walletlib.AuthRepository
import com.selenus.artemis.wallet.mwa.walletlib.AuthIssuerConfig
import com.selenus.artemis.wallet.mwa.walletlib.InMemoryAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract Scenario base. Mirrors the upstream walletlib shape so
 * subclasses in user code keep compiling.
 */
abstract class Scenario protected constructor(
    @JvmField protected val mCallbacks: Callbacks,
    @JvmField val associationPublicKey: ByteArray
) {
    abstract fun start()
    abstract fun close()

    /**
     * Wallet-side callback set. Mirrors the upstream Java interface
     * exactly — every method is non-suspending so existing wallet
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
 * URI plus the wallet's [CompatMobileWalletAdapterConfig], identical
 * to the upstream Java constructor signature.
 *
 * Bridges into the Artemis [ArtemisLocalScenario] under the hood.
 */
open class LocalAssociationScenario @JvmOverloads constructor(
    associationUri: LocalAssociationUri,
    private val config: CompatMobileWalletAdapterConfig,
    callbacks: Callbacks,
    private val authRepository: AuthRepository = InMemoryAuthRepository(
        AuthIssuerConfig(name = "Wallet")
    )
) : Scenario(callbacks, associationUri.associationPublicKey) {

    private val artemis: ArtemisLocalScenario = ArtemisLocalScenario(
        associationUri = associationUri.toArtemis(),
        config = config.toArtemis(),
        authRepository = authRepository
    )

    /**
     * Lifecycle scope owned by this scenario. start() launches the
     * suspend bridge on it; close() cancels it deterministically so
     * UI flows do not leak past teardown.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        // Upstream's start() is non-suspending; bridge through a
        // background coroutine. Errors surface through onScenarioError.
        scope.launch {
            try {
                artemis.start(buildArtemisCallbacks())
            } catch (t: Throwable) {
                mCallbacks.onScenarioError()
            }
        }
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
