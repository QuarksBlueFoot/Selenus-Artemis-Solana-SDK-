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
    ): AuthorizationFuture = AuthorizationFuture(notYetAuthorized("cluster=$cluster"))

    open fun authorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        chain: String?,
        authToken: String? = null,
        features: Array<String>? = null,
        addresses: Array<ByteArray>? = null,
        signInPayload: SignInWithSolana.Payload? = null
    ): AuthorizationFuture = AuthorizationFuture(notYetAuthorized("chain=$chain"))

    open fun reauthorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        authToken: String
    ): AuthorizationFuture = AuthorizationFuture(notYetAuthorized("reauthorize"))

    open fun deauthorize(authToken: String): DeauthorizeFuture {
        val f = CompletableFuture<Void?>()
        f.complete(null)
        return DeauthorizeFuture(f)
    }

    open fun getCapabilities(): GetCapabilitiesFuture =
        GetCapabilitiesFuture(CompletableFuture.completedFuture(defaultCapabilities()))

    @Deprecated("Use signAndSendTransactions.", level = DeprecationLevel.WARNING)
    open fun signTransactions(transactions: Array<ByteArray>): SignPayloadsFuture =
        SignPayloadsFuture(unsupported("signTransactions"))

    @Deprecated("Use signMessagesDetached.", level = DeprecationLevel.WARNING)
    open fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): SignPayloadsFuture = SignPayloadsFuture(unsupported("signMessages"))

    open fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): SignMessagesFuture = SignMessagesFuture(unsupported("signMessagesDetached"))

    open fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int? = null
    ): SignAndSendTransactionsFuture = SignAndSendTransactionsFuture(unsupported("signAndSendTransactions"))

    open fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        waitForCommitmentToSendNextTransaction: Boolean?
    ): SignAndSendTransactionsFuture = SignAndSendTransactionsFuture(unsupported("signAndSendTransactions"))

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun <T> unsupported(method: String): CompletableFuture<T> {
        val f = CompletableFuture<T>()
        f.completeExceptionally(
            UnsupportedOperationException(
                "$method is not invokable from this shim directly. " +
                "Use com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter (ktx) " +
                "or the Artemis MwaWalletAdapter from artemis-wallet-mwa-android."
            )
        )
        return f
    }

    private fun notYetAuthorized(context: String): CompletableFuture<AuthorizationResult> =
        unsupported<AuthorizationResult>("authorize ($context)")

    private fun defaultCapabilities(): GetCapabilitiesResult = GetCapabilitiesResult(
        supportsCloneAuthorization = false,
        supportsSignAndSendTransactions = true,
        maxTransactionsPerSigningRequest = 0,
        maxMessagesPerSigningRequest = 0,
        supportedTransactionVersions = arrayOf("legacy", 0),
        supportedOptionalFeatures = arrayOf(
            ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS,
            ProtocolContract.FEATURE_ID_SIGN_MESSAGES,
            ProtocolContract.FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS
        )
    )

    // ─── Nested result classes (FQNs: MobileWalletAdapterClient.X) ────────

    class AuthorizationResult(
        @JvmField val authToken: String,
        @Deprecated("Read from accounts[0].publicKey instead.")
        @JvmField val publicKey: ByteArray,
        @Deprecated("Read from accounts[0].accountLabel instead.")
        @JvmField val accountLabel: String?,
        @JvmField val walletUriBase: Uri?,
        @JvmField val walletIcon: Uri? = null,
        @JvmField val accounts: Array<AuthorizedAccount> = emptyArray(),
        @JvmField val signInResult: SignInResult? = null
    ) {
        /** Returns a copy with the given [signInResult] attached. */
        fun with(signInResult: SignInResult?): AuthorizationResult = AuthorizationResult(
            authToken = authToken,
            publicKey = publicKey,
            accountLabel = accountLabel,
            walletUriBase = walletUriBase,
            walletIcon = walletIcon,
            accounts = accounts,
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

    class InvalidPayloadsException(
        code: Int,
        message: String,
        data: String?,
        @JvmField val validPayloads: BooleanArray
    ) : com.solana.mobilewalletadapter.common.protocol.JsonRpc20Client.JsonRpc20RemoteException(code, message, data)

    class NotSubmittedException(
        code: Int,
        message: String,
        data: String?,
        @JvmField val signatures: Array<ByteArray?>
    ) : com.solana.mobilewalletadapter.common.protocol.JsonRpc20Client.JsonRpc20RemoteException(code, message, data)
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

/** Concrete subclass used by the local association scenario. */
class MobileWalletAdapterSession : MobileWalletAdapterSessionCommon() {
    override fun getAssociationPublicKey(): ECPublicKey =
        throw UnsupportedOperationException(
            "Association public key is generated by the Artemis MWA protocol layer; " +
            "this shim exposes only the upstream Java class hierarchy."
        )

    override fun getEncodedAssociationPublicKey(): ByteArray = ByteArray(0)
    override fun getSessionProperties(): SessionProperties =
        SessionProperties(SessionProperties.ProtocolVersion.V1)

    override fun getSupportedProtocolVersions(): Set<SessionProperties.ProtocolVersion> =
        setOf(SessionProperties.ProtocolVersion.LEGACY, SessionProperties.ProtocolVersion.V1)
}
