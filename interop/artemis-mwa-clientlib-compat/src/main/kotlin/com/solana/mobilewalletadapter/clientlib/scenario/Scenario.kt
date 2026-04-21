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
    associationKeyPair: java.security.KeyPair = MwaAssociationKeys.generateKeyPair()
) {

    /**
     * Ephemeral P-256 keypair used for the association handshake. The public
     * portion (SEC1 uncompressed encoding, 65 bytes) is what the dapp puts
     * in the `association` URI parameter; the wallet uses it to derive the
     * shared AES-128-GCM session key per the MWA spec.
     *
     * Generation is delegated to [MwaAssociationKeys]. Scenario does not
     * implement the crypto itself; it holds the material so the upstream
     * public field shape (`Scenario.associationKeyPair`) continues to
     * resolve for dapps migrating from the official clientlib.
     */
    val associationKeyPair: java.security.KeyPair = associationKeyPair

    /** SEC1 uncompressed encoding of the P-256 public point (65 bytes). */
    @JvmField
    val associationPublicKey: ByteArray = MwaAssociationKeys.encodedPublicKey(associationKeyPair)

    /**
     * Low-level MWA protocol client used during this scenario's lifetime.
     *
     * The upstream clientlib treats the client as Scenario-owned; the
     * non-ktx API surface depends on that relationship (dapps reach into
     * `scenario.getMobileWalletAdapterClient()` to install a bridge or
     * read nested result types). We mirror that ownership exactly rather
     * than invent a parallel shape.
     */
    @JvmField
    protected val mMobileWalletAdapterClient: MobileWalletAdapterClient =
        MobileWalletAdapterClient(clientTimeoutMs)

    /**
     * Public accessor for the underlying [MobileWalletAdapterClient]. Needed
     * by higher layers (the Artemis ktx bridge, fakewallet-style tests) that
     * want to install a real `SessionBridge` without subclassing the scenario.
     */
    fun getMobileWalletAdapterClient(): MobileWalletAdapterClient = mMobileWalletAdapterClient

    /**
     * Emit the `solana-wallet://.../associate/local?port=...&association=...`
     * URI used to launch the wallet. Every scenario subclass produces the
     * same spec-conformant shape through this single seam. Subclasses that
     * own a reserved port (see [LocalAssociationScenario]) provide a no-port
     * convenience overload that delegates here.
     */
    open fun createAssociationUri(endpointPrefix: Uri?, port: Int): Uri {
        val base = endpointPrefix ?: Uri.parse("${AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER}://")
        val associationToken = android.util.Base64.encodeToString(
            associationPublicKey,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        return base.buildUpon()
            .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
            .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT, port.toString())
            .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN, associationToken)
            .build()
    }

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

/**
 * Association-key primitives. Extracted from [Scenario] so the scenario
 * class carries no crypto logic of its own: it holds material produced
 * here, nothing more. Kept internal because the public surface consumers
 * see is `Scenario.associationKeyPair` / `associationPublicKey`, not
 * these helpers.
 */
internal object MwaAssociationKeys {

    /** Fresh ephemeral P-256 keypair for an association handshake. */
    fun generateKeyPair(): java.security.KeyPair {
        val kpg = java.security.KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    /** SEC1 uncompressed encoding of [keyPair]'s public point (65 bytes). */
    fun encodedPublicKey(keyPair: java.security.KeyPair): ByteArray {
        val pk = keyPair.public as java.security.interfaces.ECPublicKey
        val w = pk.w
        fun pad(b: java.math.BigInteger): ByteArray {
            val raw = b.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
                else -> ByteArray(32).also { out -> raw.copyInto(out, 32 - raw.size) }
            }
        }
        val x = pad(w.affineX)
        val y = pad(w.affineY)
        return ByteArray(65).also { buf ->
            buf[0] = 0x04
            x.copyInto(buf, 1)
            y.copyInto(buf, 33)
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
     * Convenience overload that injects the scenario's reserved port.
     * Delegates to [Scenario.createAssociationUri], which owns the single
     * source of truth for the URI shape so primary and fallback paths
     * cannot diverge on encoding.
     */
    fun createAssociationUri(endpointPrefix: Uri?): Uri =
        createAssociationUri(endpointPrefix, _port)

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
        // Single seam, no branching. `Scenario.createAssociationUri` is the
        // only place the association URI shape is produced; every subclass
        // goes through it, so primary and fallback paths cannot diverge on
        // encoding (Base58 fallback is unreachable by construction now).
        return Intent(Intent.ACTION_VIEW, scenario.createAssociationUri(endpointPrefix, port))
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
