/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.
 *
 * Upstream's non-ktx Java client is where every `AuthorizationResult`,
 * `GetCapabilitiesResult`, `SignPayloadsResult`, `SignMessagesResult`, and
 * `SignAndSendTransactionsResult` is NESTED. Apps that wrote:
 *
 *   import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
 *   val account = result.accounts.first()
 *
 * need this class to exist so the nested types resolve at the exact same
 * fully qualified name. The shim is defined in Kotlin but lays out all
 * nested types the way the Java source does.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.clientlib.protocol

import android.net.Uri
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.SessionProperties
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture
import com.solana.mobilewalletadapter.common.util.OnCompleteCallback
import java.security.interfaces.ECPublicKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Low-level MWA JSON-RPC client.
 *
 * All public method return types are `NotifyOnCompleteFuture<T>` wrapping a
 * nested result class. The shim keeps the exact nested-class FQNs because
 * dapps import them as `MobileWalletAdapterClient.AuthorizationResult`, etc.
 */
open class MobileWalletAdapterClient(
    @Suppress("UNUSED_PARAMETER") clientTimeoutMs: Int
) {
    /**
     * Live-session bridge. When Scenario.start() completes the association
     * handshake it installs a bridge that routes each method call through
     * to the real wallet. While null, the client returns a failed future
     * with an explicit error rather than silently succeeding with synthetic
     * data.
     */
    interface SessionBridge {
        fun authorize(
            identityUri: Uri?,
            iconUri: Uri?,
            identityName: String?,
            chain: String?,
            authToken: String?,
            features: Array<String>?,
            addresses: Array<ByteArray>?,
            signInPayload: SignInWithSolana.Payload?
        ): CompletableFuture<AuthorizationResult>

        fun deauthorize(authToken: String): CompletableFuture<Void?>
        fun getCapabilities(): CompletableFuture<GetCapabilitiesResult>
        fun signTransactions(transactions: Array<ByteArray>): CompletableFuture<SignPayloadsResult>
        fun signMessagesDetached(
            messages: Array<ByteArray>,
            addresses: Array<ByteArray>
        ): CompletableFuture<SignMessagesResult>
        fun signAndSendTransactions(
            transactions: Array<ByteArray>,
            minContextSlot: Int?,
            commitment: String?,
            skipPreflight: Boolean?,
            maxRetries: Int?,
            waitForCommitmentToSendNextTransaction: Boolean?
        ): CompletableFuture<SignAndSendTransactionsResult>
    }

    @Volatile
    private var bridge: SessionBridge? = null

    /** Installed by [scenario.Scenario.start] after the association succeeds. */
    fun installBridge(bridge: SessionBridge?) {
        this.bridge = bridge
    }

    internal class NotifyingFuture<T>(private val backing: CompletableFuture<T>) :
        NotifyOnCompleteFuture<T>, Future<T> by backing {
        override fun notifyOnComplete(cb: OnCompleteCallback<in NotifyOnCompleteFuture<T>>) {
            backing.whenComplete { _, _ -> cb.onComplete(this) }
        }
    }

    // ─── Futures returned by each method ──────────────────────────────────

    class AuthorizationFuture internal constructor(backing: CompletableFuture<AuthorizationResult>) :
        NotifyOnCompleteFuture<AuthorizationResult> by NotifyingFuture(backing)
    class DeauthorizeFuture internal constructor(backing: CompletableFuture<Void?>) :
        NotifyOnCompleteFuture<Void?> by NotifyingFuture(backing)
    class GetCapabilitiesFuture internal constructor(backing: CompletableFuture<GetCapabilitiesResult>) :
        NotifyOnCompleteFuture<GetCapabilitiesResult> by NotifyingFuture(backing)
    class SignPayloadsFuture internal constructor(backing: CompletableFuture<SignPayloadsResult>) :
        NotifyOnCompleteFuture<SignPayloadsResult> by NotifyingFuture(backing)
    class SignMessagesFuture internal constructor(backing: CompletableFuture<SignMessagesResult>) :
        NotifyOnCompleteFuture<SignMessagesResult> by NotifyingFuture(backing)
    class SignAndSendTransactionsFuture internal constructor(backing: CompletableFuture<SignAndSendTransactionsResult>) :
        NotifyOnCompleteFuture<SignAndSendTransactionsResult> by NotifyingFuture(backing)

    // ─── Public methods (stubs surfacing the exact upstream signatures) ───

    @Deprecated(
        "Use the `chain`-based overload. The cluster parameter is MWA 1.x legacy.",
        level = DeprecationLevel.WARNING
    )
    open fun authorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        cluster: String?
    ): AuthorizationFuture = authorize(
        identityUri = identityUri,
        iconUri = iconUri,
        identityName = identityName,
        chain = when (cluster?.lowercase()) {
            null, "" -> null
            "mainnet-beta", "mainnet" -> "solana:mainnet"
            "testnet" -> "solana:testnet"
            "devnet" -> "solana:devnet"
            else -> "solana:$cluster"
        }
    )

    open fun authorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        chain: String?,
        authToken: String? = null,
        features: Array<String>? = null,
        addresses: Array<ByteArray>? = null,
        signInPayload: SignInWithSolana.Payload? = null
    ): AuthorizationFuture = AuthorizationFuture(
        requireBridge("authorize").authorize(
            identityUri, iconUri, identityName, chain, authToken, features, addresses, signInPayload
        )
    )

    /**
     * Unified reauthorize. MWA 2.0 merges reauthorize into authorize with the
     * auth_token parameter set; matching upstream behaviour here keeps the
     * non-ktx callers aligned with what the walletlib actually accepts.
     */
    open fun reauthorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        authToken: String
    ): AuthorizationFuture = authorize(
        identityUri = identityUri,
        iconUri = iconUri,
        identityName = identityName,
        chain = null,
        authToken = authToken
    )

    open fun deauthorize(authToken: String): DeauthorizeFuture {
        // Deauthorize with no live bridge is a no-op by design: there is
        // nothing to deauthorize and the caller is clearing local state.
        val b = bridge
            ?: return DeauthorizeFuture(CompletableFuture.completedFuture(null))
        return DeauthorizeFuture(b.deauthorize(authToken))
    }

    open fun getCapabilities(): GetCapabilitiesFuture = GetCapabilitiesFuture(
        requireBridge("getCapabilities").getCapabilities()
    )

    @Deprecated("Use signAndSendTransactions.", level = DeprecationLevel.WARNING)
    open fun signTransactions(transactions: Array<ByteArray>): SignPayloadsFuture =
        SignPayloadsFuture(requireBridge("signTransactions").signTransactions(transactions))

    @Deprecated("Use signMessagesDetached.", level = DeprecationLevel.WARNING)
    open fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): SignPayloadsFuture {
        // Pre-2.0 legacy path: wallet returned concatenated (message || signature)
        // blobs. Bridge sits on the detached API and re-packs so callers that
        // still consume signedPayloads get parity with upstream legacy output.
        return SignPayloadsFuture(
            requireBridge("signMessages").signMessagesDetached(messages, addresses).thenApply { det ->
                val packed = det.messages.map { sm ->
                    val sig = sm.signatures.firstOrNull() ?: ByteArray(0)
                    sm.message + sig
                }.toTypedArray()
                SignPayloadsResult(packed)
            }
        )
    }

    open fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): SignMessagesFuture = SignMessagesFuture(
        requireBridge("signMessagesDetached").signMessagesDetached(messages, addresses)
    )

    open fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int? = null
    ): SignAndSendTransactionsFuture = signAndSendTransactions(
        transactions, minContextSlot, null, null, null, null
    )

    open fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        waitForCommitmentToSendNextTransaction: Boolean?
    ): SignAndSendTransactionsFuture = SignAndSendTransactionsFuture(
        requireBridge("signAndSendTransactions").signAndSendTransactions(
            transactions, minContextSlot, commitment, skipPreflight,
            maxRetries, waitForCommitmentToSendNextTransaction
        )
    )

    // ─── Helpers ──────────────────────────────────────────────────────────

    /**
     * Returns the currently installed bridge or throws a typed, actionable
     * [SessionNotReadyException]. Previous revisions returned a future that
     * completed with an opaque [UnsupportedOperationException] pointing
     * nowhere; this variant tells the caller exactly how to wire a bridge
     * (via [scenario.LocalAssociationScenario.start] plus
     * [com.solana.mobilewalletadapter.clientlib.MwaSessionBridge.attach],
     * or the ktx [com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter.transact]).
     */
    private fun requireBridge(method: String): SessionBridge =
        bridge ?: throw SessionNotReadyException(method)

    /**
     * Raised when the low-level MWA client is invoked before any bridge has
     * been installed. The message names the missing operation and the two
     * supported remedies; callers that catch it know exactly what to do
     * next instead of discovering it in Stack Overflow.
     */
    class SessionNotReadyException(method: String) : IllegalStateException(
        "MobileWalletAdapterClient.$method was called before a SessionBridge " +
        "was installed. Options:\n" +
        "  1. (recommended) use the ktx wrapper: " +
        "com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter.transact { ... }, " +
        "which installs a bridge automatically for the duration of the block.\n" +
        "  2. install a bridge manually: " +
        "MwaSessionBridge.attach(scenario, Artemis.MwaWalletAdapter(...)) before invoking " +
        "client methods."
    )

    // ─── Nested result classes (FQNs: MobileWalletAdapterClient.X) ────────

    /**
     * Upstream positional ordering: (authToken, accounts, walletUriBase, walletIcon, signInResult).
     *
     * The deprecated `publicKey` and `accountLabel` properties forward to
     * `accounts[0]` so existing getter call sites continue to resolve without
     * source changes. Constructing positionally continues to work Java-style.
     */
    class AuthorizationResult(
        @JvmField val authToken: String,
        @JvmField val accounts: Array<AuthorizedAccount>,
        @JvmField val walletUriBase: Uri?,
        @JvmField val walletIcon: Uri?,
        @JvmField val signInResult: SignInResult?
    ) {
        @Deprecated("Read from accounts[0].publicKey instead.")
        val publicKey: ByteArray
            get() = accounts.firstOrNull()?.publicKey ?: ByteArray(0)

        @Deprecated("Read from accounts[0].accountLabel instead.")
        val accountLabel: String?
            get() = accounts.firstOrNull()?.accountLabel

        /** Returns a copy with the given [signInResult] attached. */
        fun with(signInResult: SignInResult?): AuthorizationResult = AuthorizationResult(
            authToken = authToken,
            accounts = accounts,
            walletUriBase = walletUriBase,
            walletIcon = walletIcon,
            signInResult = signInResult
        )

        class AuthorizedAccount(
            @JvmField val publicKey: ByteArray,
            @JvmField val accountLabel: String?,
            @JvmField val chains: Array<String>?,
            @JvmField val features: Array<String>?,
            @JvmField val displayAddress: String?,
            @JvmField val displayAddressFormat: String?,
            @JvmField val icon: Uri?
        )

        class SignInResult(
            @JvmField val publicKey: ByteArray,
            @JvmField val signedMessage: ByteArray,
            @JvmField val signature: ByteArray,
            @JvmField val signatureType: String?
        )
    }

    class GetCapabilitiesResult(
        @Deprecated("Inferred from supportedOptionalFeatures instead.")
        @JvmField val supportsCloneAuthorization: Boolean,
        @Deprecated("Inferred from supportedOptionalFeatures instead.")
        @JvmField val supportsSignAndSendTransactions: Boolean,
        @JvmField val maxTransactionsPerSigningRequest: Int,
        @JvmField val maxMessagesPerSigningRequest: Int,
        @JvmField val supportedTransactionVersions: Array<Any>,
        @JvmField val supportedOptionalFeatures: Array<String>
    )

    class SignPayloadsResult(@JvmField val signedPayloads: Array<ByteArray>)

    class SignMessagesResult(@JvmField val messages: Array<SignedMessage>) {
        class SignedMessage(
            @JvmField val message: ByteArray,
            @JvmField val signatures: Array<ByteArray>,
            @JvmField val addresses: Array<ByteArray>
        )
    }

    class SignAndSendTransactionsResult(@JvmField val signatures: Array<ByteArray>)

    /**
     * Upstream ctor: `(message, data, expectedNumSignedPayloads)`. The code
     * is fixed at `ProtocolContract.ERROR_INVALID_PAYLOADS = -2`, not passed
     * positionally, so callers that `catch (InvalidPayloadsException)` can
     * read `e.expectedNumSignedPayloads` as a plain Int (not a BooleanArray).
     */
    class InvalidPayloadsException(
        message: String,
        data: String?,
        @JvmField val expectedNumSignedPayloads: Int
    ) : com.solana.mobilewalletadapter.common.protocol.JsonRpc20Client.JsonRpc20RemoteException(
        code = com.solana.mobilewalletadapter.common.ProtocolContract.ERROR_INVALID_PAYLOADS,
        message = message,
        data = data
    )

    /**
     * Upstream ctor: `(message, data, expectedNumSignatures)`. Fixed code
     * `ProtocolContract.ERROR_NOT_SUBMITTED = -4`.
     */
    class NotSubmittedException(
        message: String,
        data: String?,
        @JvmField val expectedNumSignatures: Int
    ) : com.solana.mobilewalletadapter.common.protocol.JsonRpc20Client.JsonRpc20RemoteException(
        code = com.solana.mobilewalletadapter.common.ProtocolContract.ERROR_NOT_SUBMITTED,
        message = message,
        data = data
    )

    /** Fixed code `ProtocolContract.ERROR_NOT_CLONED = -5`. */
    class NotClonedException(
        message: String,
        data: String? = null
    ) : com.solana.mobilewalletadapter.common.protocol.JsonRpc20Client.JsonRpc20RemoteException(
        code = com.solana.mobilewalletadapter.common.ProtocolContract.ERROR_NOT_CLONED,
        message = message,
        data = data
    )
}

/**
 * Abstract base for the MWA session. Upstream surfaces this type from the
 * clientlib scenario layer.
 */
abstract class MobileWalletAdapterSessionCommon {
    abstract fun getAssociationPublicKey(): ECPublicKey
    abstract fun getEncodedAssociationPublicKey(): ByteArray
    abstract fun getSessionProperties(): SessionProperties
    abstract fun getSupportedProtocolVersions(): Set<SessionProperties.ProtocolVersion>
}

/**
 * Concrete subclass used by the local association scenario.
 *
 * Each instance owns an ephemeral P-256 keypair, the same key material
 * used by [com.solana.mobilewalletadapter.clientlib.scenario.Scenario] to
 * build the `association` URI. Earlier revisions threw on
 * [getAssociationPublicKey] and returned an empty array from
 * [getEncodedAssociationPublicKey]; both now return real values so
 * callers that inspect the session's public key work end-to-end.
 */
class MobileWalletAdapterSession @JvmOverloads constructor(
    private val keyPair: java.security.KeyPair = generateAssociationKeyPair(),
    private val supportedVersions: Set<SessionProperties.ProtocolVersion> = setOf(
        SessionProperties.ProtocolVersion.LEGACY,
        SessionProperties.ProtocolVersion.V1
    ),
    private val properties: SessionProperties = SessionProperties(
        SessionProperties.ProtocolVersion.V1
    )
) : MobileWalletAdapterSessionCommon() {

    override fun getAssociationPublicKey(): ECPublicKey =
        keyPair.public as ECPublicKey

    override fun getEncodedAssociationPublicKey(): ByteArray {
        val pk = keyPair.public as ECPublicKey
        val point = pk.w
        fun pad(b: java.math.BigInteger): ByteArray {
            val raw = b.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
                else -> ByteArray(32).also { out -> raw.copyInto(out, 32 - raw.size) }
            }
        }
        val x = pad(point.affineX)
        val y = pad(point.affineY)
        return ByteArray(65).also { buf ->
            buf[0] = 0x04
            x.copyInto(buf, 1)
            y.copyInto(buf, 33)
        }
    }

    override fun getSessionProperties(): SessionProperties = properties

    override fun getSupportedProtocolVersions(): Set<SessionProperties.ProtocolVersion> =
        supportedVersions

    companion object {
        @JvmStatic
        fun generateAssociationKeyPair(): java.security.KeyPair {
            val kpg = java.security.KeyPairGenerator.getInstance("EC")
            kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            return kpg.generateKeyPair()
        }
    }
}
