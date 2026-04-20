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
    associationKeyPair: java.security.KeyPair = generateAssociationKeyPair()
) {

    /**
     * Ephemeral P-256 keypair used for the association handshake. The public
     * portion (SEC1 uncompressed encoding, 65 bytes) is what the dapp puts
     * in the `association` URI parameter; the wallet uses it to derive the
     * shared AES-128-GCM session key per the MWA spec. Previous versions
     * defaulted to an empty byte array, which caused wallets following the
     * spec to reject the connection up front.
     */
    val associationKeyPair: java.security.KeyPair = associationKeyPair

    /** SEC1 uncompressed encoding of the P-256 public point (65 bytes). */
    @JvmField
    val associationPublicKey: ByteArray = run {
        val pk = associationKeyPair.public as java.security.interfaces.ECPublicKey
        val w = pk.w
        val x = w.affineX.toByteArray().let { arr ->
            if (arr.size == 32) arr
            else if (arr.size == 33 && arr[0] == 0.toByte()) arr.copyOfRange(1, 33)
            else ByteArray(32).also { out -> arr.copyInto(out, 32 - arr.size) }
        }
        val y = w.affineY.toByteArray().let { arr ->
            if (arr.size == 32) arr
            else if (arr.size == 33 && arr[0] == 0.toByte()) arr.copyOfRange(1, 33)
            else ByteArray(32).also { out -> arr.copyInto(out, 32 - arr.size) }
        }
        ByteArray(65).also { buf ->
            buf[0] = 0x04
            x.copyInto(buf, 1)
            y.copyInto(buf, 33)
        }
    }

    @JvmField
    protected val mMobileWalletAdapterClient: MobileWalletAdapterClient =
        MobileWalletAdapterClient(clientTimeoutMs)

    /**
     * Public accessor for the underlying [MobileWalletAdapterClient]. Needed
     * by higher layers (the Artemis ktx bridge, fakewallet-style tests) that
     * want to install a real `SessionBridge` without subclassing the scenario.
     */
    fun getMobileWalletAdapterClient(): MobileWalletAdapterClient = mMobileWalletAdapterClient

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

        internal fun generateAssociationKeyPair(): java.security.KeyPair {
            val kpg = java.security.KeyPairGenerator.getInstance("EC")
            kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            return kpg.generateKeyPair()
        }
    }
}

/** Local (same-device) association scenario. */
open class LocalAssociationScenario @JvmOverloads constructor(
    private val clientTimeoutMs: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS
) : Scenario(clientTimeoutMs = clientTimeoutMs) {

    @Volatile private var _port: Int = 0
    @Volatile private var reservedSocket: java.net.ServerSocket? = null

    /** Ephemeral TCP port chosen at association time. Range 0..65535. */
    fun getPort(): Int = _port

    /**
     * Allocate an ephemeral TCP port + mark the scenario ready. The real
     * wallet connection is established by the ktx [MobileWalletAdapter]
     * after the wallet honours the association URI; this scenario reserves
     * the port so the URI emitted by [createAssociationUri] points at a
     * socket nothing else on the device can steal.
     */
    override fun start() {
        if (reservedSocket != null) return
        val ss = java.net.ServerSocket(0).apply {
            reuseAddress = true
        }
        reservedSocket = ss
        _port = ss.localPort
        mCallbacks?.onScenarioReady()
    }

    /** Releases the reserved port and clears the session bridge. */
    override fun close() {
        mMobileWalletAdapterClient.installBridge(null)
        try { reservedSocket?.close() } catch (_: Exception) {}
        reservedSocket = null
        _port = 0
        mCallbacks?.onScenarioTeardownComplete()
    }

    /**
     * Async variant matching upstream's real `start()` return type. Used by
     * the clientlib-ktx wrapper.
     */
    fun startAsync(): NotifyOnCompleteFuture<MobileWalletAdapterClient> {
        return ArtemisNotifyingFuture(
            java.util.concurrent.CompletableFuture.supplyAsync {
                start()
                mMobileWalletAdapterClient
            }
        )
    }

    /** Async variant of [close]. */
    fun closeAsync(): NotifyOnCompleteFuture<Void?> {
        return ArtemisNotifyingFuture(
            java.util.concurrent.CompletableFuture.supplyAsync<Void?> {
                close(); null
            }
        )
    }

    /**
     * Build the `solana-wallet://v1/associate/local?port=...&association=...`
     * URI used to launch the wallet. [endpointPrefix] overrides the scheme
     * and authority; pass `null` to use the default `solana-wallet://`.
     */
    fun createAssociationUri(endpointPrefix: Uri?): Uri {
        val base = endpointPrefix ?: Uri.parse("${AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER}://")
        // Public MWA spec defines the association-token parameter as the
        // base64url-without-padding encoding of the ephemeral P-256 public
        // key point. Previous code used Base58, which wallets that strictly
        // follow the spec (including the SMS fakewallet) reject as malformed.
        val associationToken = android.util.Base64.encodeToString(
            associationPublicKey,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        return base.buildUpon()
            .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
            .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT, _port.toString())
            .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN, associationToken)
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
