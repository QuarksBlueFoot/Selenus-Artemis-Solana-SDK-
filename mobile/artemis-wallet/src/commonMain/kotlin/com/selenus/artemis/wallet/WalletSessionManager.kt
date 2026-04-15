package com.selenus.artemis.wallet

import com.selenus.artemis.core.ArtemisEvent
import com.selenus.artemis.core.ArtemisEventBus
import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WalletEvents — lifecycle callbacks for wallet state changes.
 *
 * Implement to react when the user switches accounts, the session expires,
 * or the wallet explicitly disconnects.
 *
 * ```kotlin
 * manager.onDisconnect { showConnectButton() }
 * manager.onAccountChanged { newKey -> refreshUI(newKey) }
 * manager.onSessionExpired { manager.connect() }
 * ```
 */
interface WalletEvents {
    /** Invoked when the wallet connection is explicitly closed. */
    fun onDisconnect(callback: () -> Unit)

    /** Invoked when the active wallet account changes (e.g. user switches accounts). */
    fun onAccountChanged(callback: (Pubkey) -> Unit)

    /** Invoked when the session is invalidated and a fresh connection is needed. */
    fun onSessionExpired(callback: () -> Unit)
}

/**
 * WalletSessionManager — MWA session lifecycle management.
 *
 * Handles connection persistence, lazy reconnect, invalidation, and wallet event
 * callbacks so apps never need to manage raw adapter state directly.
 *
 * ```kotlin
 * val manager = WalletSessionManager {
 *     adapter.connect()
 *     WalletSession.fromAdapter(adapter, txEngine)
 * }
 *
 * // Gate any wallet operation — connects once, reuses on subsequent calls
 * val result = manager.withWallet { session ->
 *     session.sendSol(recipient, 1_000_000_000L)
 * }
 *
 * // React to wallet lifecycle events
 * manager.onDisconnect { showConnectButton() }
 * manager.onSessionExpired { println("Session expired — next call will reconnect") }
 * ```
 *
 * @param connector Suspending factory that opens a wallet connection and returns a [WalletSession].
 *                  Called only when no valid session exists.
 */
class WalletSessionManager(
    private val connector: suspend () -> WalletSession,
    /**
     * When true (the default), session lifecycle transitions are mirrored to
     * [ArtemisEventBus] so the framework-level event stream reflects wallet state.
     * Set to false for unit tests that care only about direct callbacks.
     */
    private val publishToBus: Boolean = true,
    /** Human-readable wallet label included in [ArtemisEvent.Wallet.Connected]. */
    private val walletName: String? = null
) : WalletEvents {

    private val mutex = Mutex()
    private var currentSession: WalletSession? = null
    private var lastPublicKey: Pubkey? = null

    private var disconnectCallback: (() -> Unit)? = null
    private var accountChangedCallback: ((Pubkey) -> Unit)? = null
    private var sessionExpiredCallback: (() -> Unit)? = null

    // ─── WalletEvents ─────────────────────────────────────────────────────────

    override fun onDisconnect(callback: () -> Unit) {
        disconnectCallback = callback
    }

    override fun onAccountChanged(callback: (Pubkey) -> Unit) {
        accountChangedCallback = callback
    }

    override fun onSessionExpired(callback: () -> Unit) {
        sessionExpiredCallback = callback
    }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    /**
     * Get the current session. Connects if no session exists.
     * Thread-safe: concurrent calls share the same connection result.
     */
    suspend fun get(): WalletSession = mutex.withLock {
        currentSession ?: connectInternal()
    }

    /**
     * Force a fresh connection regardless of whether a session exists.
     * Fires [onAccountChanged] if the new public key differs from the previous session.
     */
    suspend fun connect(): WalletSession = mutex.withLock {
        connectInternal()
    }

    /**
     * Invalidate the current session without triggering a reconnect.
     * The next call to [get] or [withWallet] will establish a new connection.
     * Fires [onSessionExpired] and publishes [ArtemisEvent.Wallet.SessionExpired].
     */
    suspend fun invalidate() = mutex.withLock {
        currentSession = null
        sessionExpiredCallback?.invoke()
        if (publishToBus) ArtemisEventBus.emit(ArtemisEvent.Wallet.SessionExpired())
    }

    /**
     * Disconnect the current session and clear all state.
     * Fires [onDisconnect] and publishes [ArtemisEvent.Wallet.Disconnected].
     */
    suspend fun disconnect() = mutex.withLock {
        currentSession = null
        lastPublicKey = null
        disconnectCallback?.invoke()
        if (publishToBus) ArtemisEventBus.emit(ArtemisEvent.Wallet.Disconnected(reason = "user"))
    }

    /**
     * Get a session, reconnecting automatically on error.
     *
     * Suitable for retry flows where the session may have expired between calls.
     */
    suspend fun ensureValid(): WalletSession {
        return try {
            get()
        } catch (e: Exception) {
            // Clear the stale session and try once more with a fresh connection.
            runCatching { mutex.withLock { currentSession = null } }
            val session = connector()
            mutex.withLock {
                currentSession = session
                updatePublicKey(session)
            }
            session
        }
    }

    /**
     * Execute an action gated on a valid wallet session.
     *
     * Connects on first call. On [IllegalStateException] (session expired), invalidates
     * and retries once before propagating the error.
     *
     * ```kotlin
     * val sig = manager.withWallet { session ->
     *     session.sendSol(recipient, lamports)
     * }
     * ```
     */
    suspend fun <T> withWallet(action: suspend (WalletSession) -> T): T {
        return try {
            action(get())
        } catch (e: IllegalStateException) {
            // Session may have expired — invalidate and retry once.
            runCatching { mutex.withLock { currentSession = null; sessionExpiredCallback?.invoke() } }
            action(get())
        }
    }

    /** Whether a session is currently held. Does not initiate a connection. */
    val isConnected: Boolean
        get() = currentSession != null

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun connectInternal(): WalletSession {
        val session = connector()
        updatePublicKey(session)
        currentSession = session
        if (publishToBus) {
            val pk = runCatching { session.publicKey.toBase58() }.getOrNull()
            if (pk != null) {
                ArtemisEventBus.emit(
                    ArtemisEvent.Wallet.Connected(publicKey = pk, walletName = walletName)
                )
            }
        }
        return session
    }

    private fun updatePublicKey(session: WalletSession) {
        val newKey = try {
            session.publicKey
        } catch (_: Exception) {
            return
        }
        val prev = lastPublicKey
        if (prev != null && prev != newKey) {
            accountChangedCallback?.invoke(newKey)
            if (publishToBus) {
                ArtemisEventBus.emit(
                    ArtemisEvent.Wallet.AccountChanged(
                        previousKey = runCatching { prev.toBase58() }.getOrNull(),
                        currentKey = runCatching { newKey.toBase58() }.getOrNull() ?: ""
                    )
                )
            }
        }
        lastPublicKey = newKey
    }
}
