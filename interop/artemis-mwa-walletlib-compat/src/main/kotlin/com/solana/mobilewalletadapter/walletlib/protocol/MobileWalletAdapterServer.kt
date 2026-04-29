/*
 * Drop-in source compatibility for com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer.
 *
 * Upstream's class is the wallet-side JSON-RPC server: it consumes
 * decrypted MWA frames from a session, dispatches them onto a
 * `MethodHandlers` set, and serializes the wallet's typed completions
 * back onto the wire. The Artemis equivalent is `WalletMwaServer`,
 * which is module-internal, this shim exposes the upstream FQN as a
 * thin façade for code that catches the nested exception types.
 *
 * The methods are present for binary parity with upstream Java callers
 * but most consumer code only ever interacts through the [Scenario]
 * façade; the dispatcher itself is owned by the scenario.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.protocol

/**
 * Wallet-side MWA JSON-RPC server. Exposed at the upstream FQN so
 * existing Java code that imports the typed exception classes (the
 * primary use of this class outside the dispatcher) keeps compiling.
 *
 * Construction is a no-op: the production server is created and owned
 * by the [com.solana.mobilewalletadapter.walletlib.scenario.Scenario]
 * implementation. Holding a stand-alone instance is meaningful only
 * for users who want to extend or proxy the dispatch path; that
 * contract is delegated to the underlying Artemis `WalletMwaServer`.
 */
class MobileWalletAdapterServer {

    /**
     * Base for every wallet-side dispatch error. Maps onto the
     * `MobileWalletAdapterSessionCommon.ErrorCode` typed code so a dApp
     * client that catches a `JsonRpc20RemoteException` can resolve the
     * specific subtype without string-matching.
     */
    open class MobileWalletAdapterServerException(
        message: String?,
        cause: Throwable? = null
    ) : RuntimeException(message, cause)

    /** User declined the request. Maps to `AUTHORIZATION_FAILED` on the wire. */
    class RequestDeclinedException(
        message: String? = "user declined the request"
    ) : MobileWalletAdapterServerException(message)

    /**
     * One or more payloads failed an internal validation step.
     * @property valid Per-payload boolean; `false` entries identify
     *   the rejected indices.
     */
    class InvalidPayloadsException(
        message: String?,
        @JvmField val valid: BooleanArray
    ) : MobileWalletAdapterServerException(message)

    /**
     * Wallet signed but failed to broadcast one or more transactions.
     * @property signatures Per-payload signature; `null` entries
     *   identify transactions that were never submitted.
     */
    class NotSubmittedException(
        message: String?,
        @JvmField val signatures: Array<ByteArray?>
    ) : MobileWalletAdapterServerException(message)

    /** Caller exceeded the wallet's per-call payload cap. */
    class TooManyPayloadsException(
        message: String? = "request exceeded the wallet's per-call payload cap"
    ) : MobileWalletAdapterServerException(message)

    /** Auth token was unknown, expired, or chain-mismatched. */
    class AuthorizationNotValidException(
        message: String? = "authorization no longer valid; reauthorize"
    ) : MobileWalletAdapterServerException(message)

    /**
     * Wallet does not support the requested chain. Upstream historically
     * named this `ClusterNotSupportedException`; the chain-named alias
     * is preferred in MWA 2.x.
     */
    class ChainNotSupportedException(
        @JvmField val chain: String?,
        message: String? = "wallet does not support chain `$chain`"
    ) : MobileWalletAdapterServerException(message)

    /**
     * Legacy alias for [ChainNotSupportedException]. Kept so MWA 1.x
     * `catch (ClusterNotSupportedException)` still compiles. New code
     * should catch the chain variant instead.
     */
    @Deprecated(
        "Use ChainNotSupportedException.",
        ReplaceWith("ChainNotSupportedException")
    )
    class ClusterNotSupportedException(
        @JvmField val cluster: String?,
        message: String? = "wallet does not support cluster `$cluster`"
    ) : MobileWalletAdapterServerException(message)

    /**
     * Method-level handler registry. Upstream surfaces this as a public
     * inner type so wallets that wrap the server can override individual
     * verbs; the Artemis path delegates to `Scenario.Callbacks`, so this
     * registry is a no-op marker kept for FQN compatibility only.
     */
    interface MethodHandlers
}

/**
 * Wallet-side MWA session. Upstream `MobileWalletAdapterSession` is the
 * crypto + sequence-number layer that encrypts/decrypts JSON-RPC frames
 * before they reach [MobileWalletAdapterServer]. The Artemis equivalent
 * is [com.selenus.artemis.wallet.mwa.protocol.MwaSession]; this façade
 * exists so Java callers writing `is MobileWalletAdapterSession` checks
 * keep resolving.
 */
class MobileWalletAdapterSession {
    companion object {
        const val PROTOCOL_VERSION_LEGACY: String = "legacy"
        const val PROTOCOL_VERSION_V1: String = "v1"
    }
}

