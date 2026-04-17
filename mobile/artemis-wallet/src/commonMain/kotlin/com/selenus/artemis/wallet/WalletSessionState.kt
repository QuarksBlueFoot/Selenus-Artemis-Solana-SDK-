package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Pubkey

/**
 * WalletSessionState - typed state machine for the MWA wallet lifecycle.
 *
 * The Solana Mobile Stack treats MWA as a protocol with explicit lifecycle
 * phases: the dapp requests authorization, the wallet runs its UX and
 * returns a session, the session can later expire or be revoked. Apps need
 * a single source of truth so the UI tracks these transitions instead of
 * duplicating boolean flags across the code base.
 *
 * [WalletSessionManager] exposes `state: StateFlow<WalletSessionState>` so
 * Compose or ViewModel layers can observe the full lifecycle without
 * subscribing to multiple callbacks. Every state carries a monotonic
 * [epoch] counter so collectors can tell an expired-then-reconnected
 * session apart from a session that merely refreshed its auth token.
 *
 * Transitions:
 *
 * ```
 *  Disconnected ──connect()──▶ Connecting ──success──▶ Connected
 *       ▲                         │                       │
 *       │                         │ failure               │ invalidate() / session expiry
 *       │                         ▼                       ▼
 *       │                      Failed                  Expired
 *       │                                                 │
 *       │                                                 │ get() / withWallet {}
 *       │                                                 ▼
 *       └──disconnect()──────── Connected ◀─── Connecting (silent reconnect using authToken)
 * ```
 *
 * All states expose [atMs] (wall-clock millis when the state was entered)
 * for telemetry and audit trails.
 */
sealed class WalletSessionState {

    /** Monotonic counter, incremented on every state change. Useful for dedupe. */
    abstract val epoch: Long

    /** Wall-clock millis the state was entered. */
    abstract val atMs: Long

    /** Initial state before any `connect()` call. */
    data class Disconnected(
        override val epoch: Long = 0,
        override val atMs: Long = 0
    ) : WalletSessionState()

    /** A connect or silent reauthorize is in flight. */
    data class Connecting(
        val silent: Boolean,
        override val epoch: Long,
        override val atMs: Long
    ) : WalletSessionState()

    /** The wallet is connected and a [WalletSession] is available. */
    data class Connected(
        val publicKey: Pubkey,
        val walletName: String?,
        override val epoch: Long,
        override val atMs: Long
    ) : WalletSessionState()

    /**
     * The most recent connect attempt failed. The [WalletSessionManager] is
     * ready to try again via [WalletSessionManager.connect] or a subsequent
     * `withWallet { }` call.
     */
    data class Failed(
        val reason: String,
        override val epoch: Long,
        override val atMs: Long
    ) : WalletSessionState()

    /**
     * The previously-valid session is no longer usable (auth token revoked,
     * wallet-side deauthorize, or expiry). The next `withWallet { }` call
     * will attempt a silent reauthorize if an auth token is still persisted;
     * otherwise it falls back to a full user-facing connect flow.
     */
    data class Expired(
        override val epoch: Long,
        override val atMs: Long
    ) : WalletSessionState()

    /** Convenience: true when the session is usable right now. */
    val isLive: Boolean get() = this is Connected

    /** Convenience: true when an explicit action is required from the user. */
    val needsUserAction: Boolean
        get() = this is Disconnected || this is Failed
}
