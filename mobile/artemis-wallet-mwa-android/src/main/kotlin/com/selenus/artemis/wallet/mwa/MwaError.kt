/*
 * MwaError
 *
 * Exhaustive, spec-first error taxonomy for the Mobile Wallet Adapter 2.0
 * protocol. Upstream (`solana-mobile/mobile-wallet-adapter` #314) never fully
 * enumerated the error codes, leaving apps to reverse-engineer whether a
 * failure came from the transport, the session establishment, the wallet's
 * policy, or the signed-but-not-broadcast fallback.
 *
 * Artemis normalizes every shipping wallet's response into a single sealed
 * hierarchy with a numeric code, a machine-readable reason, a user-facing
 * hint, and a recovery action the app can take without a round-trip to
 * product design.
 */
package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.wallet.SessionExpiredException

/**
 * Recovery hint Artemis recommends for a given [MwaError]. Apps that surface
 * generic "something went wrong" text today can drive their UX off this
 * instead.
 */
enum class Recovery {
    /** Prompt the user to install or reopen a compatible wallet. */
    InstallOrOpenWallet,

    /** Prompt the user to unlock their wallet and try again. */
    UnlockWallet,

    /** User cancelled the action; no further retry. */
    UserCancel,

    /** Transient error; safe to retry immediately. */
    Retry,

    /** Transient error; retry after re-authorizing. */
    Reauthorize,

    /** The transaction was actually signed but not broadcast; submit it via RPC. */
    BroadcastSignedFallback,

    /** Unrecoverable client bug or malformed payload; surface as an internal error. */
    Fatal,

    /** App should verify it is targeting the same chain as the wallet. */
    VerifyChain,
}

/**
 * Typed MWA error. Construct via [MwaError.from] on the catch path or by
 * inspecting the sealed subclasses directly.
 *
 * [code] mirrors the JSON-RPC error code when the wallet returned one, or a
 * synthetic Artemis code otherwise (all Artemis-synthetic codes sit in the
 * `-4000..-4999` range to avoid clashes with the `-32000..-32099` reserved
 * JSON-RPC server range).
 */
sealed class MwaError(
    val code: Int,
    val reason: String,
    val hint: String,
    val recovery: Recovery,
    cause: Throwable? = null,
) : Exception("[$code] $reason: $hint", cause) {

    // ─── Transport ──────────────────────────────────────────────────────

    /** Activity Result returned RESULT_CANCELED or no wallet installed. */
    class WalletNotFound(cause: Throwable? = null) : MwaError(
        code = -4001,
        reason = "WALLET_NOT_FOUND",
        hint = "No MWA-compatible wallet responded. The user may not have a wallet installed.",
        recovery = Recovery.InstallOrOpenWallet,
        cause = cause,
    )

    /** Local WebSocket never opened inside the spec timeout. Common on TWA/Seeker (#1458). */
    class ConnectionTimeout(cause: Throwable? = null) : MwaError(
        code = -4002,
        reason = "CONNECTION_TIMEOUT",
        hint = "Wallet did not open its side of the local WebSocket. May indicate the user dismissed the dialog.",
        recovery = Recovery.Retry,
        cause = cause,
    )

    /** Association token mismatch between the app and the wallet. */
    class AssociationMismatch(cause: Throwable? = null) : MwaError(
        code = -4003,
        reason = "ASSOCIATION_MISMATCH",
        hint = "Wallet presented a P-256 key we did not associate with. Retry from a clean Activity.",
        recovery = Recovery.Retry,
        cause = cause,
    )

    // ─── Session establishment ─────────────────────────────────────────

    class ProtocolVersionUnsupported(version: String, cause: Throwable? = null) : MwaError(
        code = -4004,
        reason = "PROTOCOL_VERSION_UNSUPPORTED",
        hint = "Wallet only speaks MWA $version. Update the wallet or the app.",
        recovery = Recovery.Fatal,
        cause = cause,
    )

    class SessionCipherFailed(cause: Throwable? = null) : MwaError(
        code = -4005,
        reason = "SESSION_CIPHER_FAILED",
        hint = "AES-128-GCM decrypt failed on a session frame. Usually indicates a key desync; reconnecting fixes it.",
        recovery = Recovery.Retry,
        cause = cause,
    )

    // ─── Authorization ─────────────────────────────────────────────────

    /** JSON-RPC -32602 from authorize / reauthorize. */
    class AuthorizationFailed(reason: String, cause: Throwable? = null) : MwaError(
        code = -4010,
        reason = "AUTHORIZATION_FAILED",
        hint = "Wallet declined authorization: $reason",
        recovery = Recovery.UserCancel,
        cause = cause,
    )

    /** Auth token expired or rejected on reauthorize. */
    class SessionExpired(cause: Throwable? = null) : MwaError(
        code = -4011,
        reason = "SESSION_EXPIRED",
        hint = "The stored auth token is no longer valid. Reauthorize from scratch.",
        recovery = Recovery.Reauthorize,
        cause = cause,
    )

    class UserDeclined(cause: Throwable? = null) : MwaError(
        code = -4012,
        reason = "USER_DECLINED",
        hint = "User dismissed the wallet sheet without approving.",
        recovery = Recovery.UserCancel,
        cause = cause,
    )

    // ─── Signing ───────────────────────────────────────────────────────

    /** Wallet returned error for sign_transactions / sign_messages (JSON-RPC -1). */
    class PayloadRejected(detail: String, cause: Throwable? = null) : MwaError(
        code = -4020,
        reason = "PAYLOAD_REJECTED",
        hint = "Wallet rejected one or more payloads: $detail",
        recovery = Recovery.Fatal,
        cause = cause,
    )

    class MaxPayloadsExceeded(requested: Int, maxAllowed: Int, cause: Throwable? = null) : MwaError(
        code = -4021,
        reason = "MAX_PAYLOADS_EXCEEDED",
        hint = "Tried to sign $requested payloads but wallet caps at $maxAllowed. Split into batches.",
        recovery = Recovery.Fatal,
        cause = cause,
    )

    class MinContextSlotTooHigh(cause: Throwable? = null) : MwaError(
        code = -4022,
        reason = "MIN_CONTEXT_SLOT_TOO_HIGH",
        hint = "Wallet is behind the required slot. Retry after the wallet catches up.",
        recovery = Recovery.Retry,
        cause = cause,
    )

    /**
     * Wallet signed the transaction but declined to broadcast it (MWA 2.0
     * `notSubmitted` sentinel). Apps should pull the signed bytes and
     * submit through their own RPC client.
     */
    class SignedButNotBroadcast(
        val signedRaw: ByteArray,
        cause: Throwable? = null,
    ) : MwaError(
        code = -4023,
        reason = "SIGNED_BUT_NOT_BROADCAST",
        hint = "Wallet returned signed transaction bytes but did not submit. Broadcast via your RPC layer.",
        recovery = Recovery.BroadcastSignedFallback,
        cause = cause,
    )

    // ─── Chain / cluster ──────────────────────────────────────────────

    class ChainMismatch(appChain: String, walletChain: String, cause: Throwable? = null) : MwaError(
        code = -4030,
        reason = "CHAIN_MISMATCH",
        hint = "App is on $appChain but wallet is on $walletChain. Surface a warning and abort before signing.",
        recovery = Recovery.VerifyChain,
        cause = cause,
    )

    // ─── Internal / fallback ──────────────────────────────────────────

    class InternalError(detail: String, cause: Throwable? = null) : MwaError(
        code = -4999,
        reason = "INTERNAL_ERROR",
        hint = "Unexpected client-side error: $detail",
        recovery = Recovery.Fatal,
        cause = cause,
    )

    companion object {
        /**
         * Map a thrown exception from the MWA call path into a typed [MwaError].
         * Unknown exceptions come back as [InternalError]; the original cause
         * is always preserved.
         */
        @JvmStatic
        fun from(throwable: Throwable): MwaError {
            if (throwable is MwaError) return throwable
            // Match Artemis's existing session-expiry sentinel.
            if (throwable is SessionExpiredException) return SessionExpired(cause = throwable)
            val msg = throwable.message.orEmpty().lowercase()
            return when {
                "timeout" in msg && "connect" in msg -> ConnectionTimeout(cause = throwable)
                "timeout" in msg -> ConnectionTimeout(cause = throwable)
                "cancelled" in msg || "canceled" in msg -> UserDeclined(cause = throwable)
                "unauthorized" in msg || "auth" in msg -> AuthorizationFailed(
                    reason = throwable.message ?: "unknown",
                    cause = throwable,
                )
                "cipher" in msg || "gcm" in msg || "decrypt" in msg -> SessionCipherFailed(cause = throwable)
                "association" in msg -> AssociationMismatch(cause = throwable)
                "no wallet" in msg || "wallet not found" in msg -> WalletNotFound(cause = throwable)
                else -> InternalError(detail = throwable.message ?: "unknown", cause = throwable)
            }
        }
    }
}
