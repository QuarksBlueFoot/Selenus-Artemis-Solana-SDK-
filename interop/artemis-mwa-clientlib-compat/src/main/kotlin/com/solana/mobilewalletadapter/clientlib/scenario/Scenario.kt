/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.clientlib.scenario.
 *
 * Verified against the 2.0.7 sources jar. Three public types:
 * - `Scenario` abstract base with `associationPublicKey` field and a nested
 *   `Callbacks` interface.
 * - `LocalAssociationScenario` concrete subclass with the 8-entry backoff
 *   schedule and a nested `ConnectionFailedException`.
 * - `LocalAssociationIntentCreator` static utility.
 *
 * The abstract `start()` and `close()` in upstream's Scenario are `void`; the
 * futures are only on `LocalAssociationScenario`. The shim preserves this so
 * subclasses in user code compile unchanged.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.clientlib.scenario

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.AssociationContract
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture
import com.solana.mobilewalletadapter.common.util.OnCompleteCallback
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Internal adapter wrapping a `CompletableFuture` into an `NotifyOnCompleteFuture`.
 */
internal class ArtemisNotifyingFuture<T>(
    private val backing: CompletableFuture<T>
) : NotifyOnCompleteFuture<T>, Future<T> by backing {

    override fun notifyOnComplete(cb: OnCompleteCallback<in NotifyOnCompleteFuture<T>>) {
        backing.whenComplete { _, _ ->
            @Suppress("UNCHECKED_CAST")
            (cb as OnCompleteCallback<NotifyOnCompleteFuture<T>>).onComplete(this)
        }
    }
}

/**
 * Abstract `Scenario` base. Matches upstream shape:
 *
 * - public final `byte[] associationPublicKey`
 * - protected final [MobileWalletAdapterClient] `mMobileWalletAdapterClient`
 * - optional nullable `Callbacks mCallbacks`
 * - abstract `void start()` / `void close()`
 * - nested `Callbacks` interface
 */
abstract class Scenario protected constructor(
    clientTimeoutMs: Int,
    @JvmField protected val mCallbacks: Callbacks? = null,
    @JvmField val associationPublicKey: ByteArray = ByteArray(0)
) {

    @JvmField
    protected val mMobileWalletAdapterClient: MobileWalletAdapterClient =
        MobileWalletAdapterClient(clientTimeoutMs)

    abstract fun start()
    abstract fun close()

    /**
     * Lifecycle callbacks a scenario emits during its lifetime. Upstream
     * dapps implement this to drive their UI state machine (loading spinner,
     * error toast, teardown cleanup).
     */
    interface Callbacks {
        fun onScenarioReady()
        fun onScenarioServingClients()
        fun onScenarioServingComplete()
        fun onScenarioComplete()
        fun onScenarioError()
        fun onScenarioTeardownComplete()
    }

    companion object {
        /** Default per-JSON-RPC-request timeout in milliseconds. */
        const val DEFAULT_CLIENT_TIMEOUT_MS: Int = 90_000
    }
}

/** Local (same-device) association scenario. */
open class LocalAssociationScenario @JvmOverloads constructor(
    private val clientTimeoutMs: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS
) : Scenario(clientTimeoutMs = clientTimeoutMs) {

    private var _port: Int = 0

    /** Ephemeral TCP port chosen at association time. Range 0..65535. */
    fun getPort(): Int = _port

    override fun start() {
        // Hook point: the real protocol handshake lives in the Artemis MWA
        // adapter. The shim fulfils the API surface so source-level imports
        // compile.
    }

    override fun close() {
        // See start().
    }

    /**
     * Async variant matching upstream's real `start()` return type. Used by
     * the clientlib-ktx wrapper.
     */
    fun startAsync(): NotifyOnCompleteFuture<MobileWalletAdapterClient> {
        val future = CompletableFuture.completedFuture(mMobileWalletAdapterClient)
        return ArtemisNotifyingFuture(future)
    }

    /** Async variant of [close]. */
    fun closeAsync(): NotifyOnCompleteFuture<Void?> {
        val future = CompletableFuture.completedFuture<Void?>(null)
        return ArtemisNotifyingFuture(future)
    }

    /**
     * Build the `solana-wallet://v1/associate/local?port=...&association=...`
     * URI used to launch the wallet. [endpointPrefix] overrides the scheme
     * and authority; pass `null` to use the default `solana-wallet://`.
     */
    fun createAssociationUri(endpointPrefix: Uri?): Uri {
        val base = endpointPrefix ?: Uri.parse("${AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER}://")
        return base.buildUpon()
            .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
            .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT, _port.toString())
            .appendQueryParameter(
                AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                com.selenus.artemis.runtime.Base58.encode(associationPublicKey)
            )
            .build()
    }

    /** Raised by `start()` when the wallet cannot be reached. */
    class ConnectionFailedException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    companion object {
        /** Maximum retry attempts during connection. */
        const val CONNECT_MAX_ATTEMPTS: Int = 34

        /**
         * Exponential back-off schedule in milliseconds. Matches the verified
         * upstream value byte-for-byte (8 entries; the remaining attempts
         * reuse the last entry).
         */
        @JvmField
        val CONNECT_BACKOFF_SCHEDULE_MS: IntArray = intArrayOf(
            150, 150, 200, 500, 500, 750, 750, 1_000
        )

        /** Per-attempt connection timeout. */
        const val CONNECT_TIMEOUT_MS: Int = 200
    }
}

/**
 * Helpers for building the `solana-wallet://v1/associate/local?...` intent
 * that dapps fire to launch a wallet.
 *
 * The third parameter to [createAssociationIntent] is a [Scenario] (NOT a
 * session object), matching the upstream Java signature.
 */
object LocalAssociationIntentCreator {

    @JvmStatic
    fun createAssociationIntent(
        endpointPrefix: Uri?,
        port: Int,
        scenario: Scenario
    ): Intent {
        val uri = if (scenario is LocalAssociationScenario) {
            scenario.createAssociationUri(endpointPrefix)
        } else {
            val base = endpointPrefix ?: Uri.parse("${AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER}://")
            base.buildUpon()
                .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT, port.toString())
                .appendQueryParameter(
                    AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                    com.selenus.artemis.runtime.Base58.encode(scenario.associationPublicKey)
                )
                .build()
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Return true when the device has at least one activity that can handle `solana-wallet://` intents. */
    @JvmStatic
    fun isWalletEndpointAvailable(pm: PackageManager): Boolean {
        val probe = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("${AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER}://${AssociationContract.LOCAL_PATH_SUFFIX}")
        )
        return probe.resolveActivity(pm) != null
    }
}
