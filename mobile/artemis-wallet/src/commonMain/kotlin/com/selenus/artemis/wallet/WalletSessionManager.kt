package com.selenus.artemis.wallet

import com.selenus.artemis.core.ArtemisEvent
import com.selenus.artemis.core.ArtemisEventBus
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WalletEvents - lifecycle callbacks for wallet state changes.
 *
 * Implement to react when the user switches accounts, the session expires,
 * or the wallet explicitly disconnects. The same transitions are also
 * published on [WalletSessionManager.state] as a typed [WalletSessionState]
 * StateFlow, which is the recommended surface for Compose and ViewModel
 * layers. Callbacks remain available for legacy callers.
 *
 * ```kotlin
 * manager.onDisconnect { showConnectButton() }
 * manager.onAccountChanged { newKey -> refreshUI(newKey) }
 * manager.onSessionExpired { manager.reconnect() }
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
 * WalletSessionManager - MWA session lifecycle with a typed state machine.
 *
 * Handles connection persistence, silent reauthorize, invalidation, and
 * wallet event propagation so apps never touch raw adapter state.
 *
 * ```kotlin
 * val manager = WalletSessionManager(
 *     connector       = { adapter.connect(); WalletSession.fromAdapter(adapter, txEngine) },
 *     silentConnector = { adapter.reauthorize(); WalletSession.fromAdapter(adapter, txEngine) }
 * )
 *
 * manager.state
 *     .onEach { state ->
 *         when (state) {
 *             is WalletSessionState.Connected    -> showWallet(state.publicKey)
 *             is WalletSessionState.Expired      -> showBanner("re-linking")
 *             is WalletSessionState.Disconnected -> showConnectButton()
 *             else -> Unit
 *         }
 *     }
 *     .launchIn(uiScope)
 *
 * // withWallet connects lazily, retries silently on expiry, and propagates
 * // the final result to the caller.
 * val sig = manager.withWallet { session -> session.sendSol(recipient, 1_000_000_000L) }
 * ```
 *
 * @param connector        Suspending factory that opens a fresh, user-visible wallet
 *                         connection and returns a [WalletSession]. Invoked when no
 *                         session exists and no auth token is persisted.
 * @param silentConnector  Optional factory that reauthorizes using a persisted auth
 *                         token without prompting the user. If omitted, the manager
 *                         falls back to [connector] on expiry, which may re-prompt.
 * @param publishToBus     When true, every transition is mirrored to [ArtemisEventBus].
 * @param walletName       Human-readable wallet label included in emitted events.
 */
class WalletSessionManager(
    private val connector: suspend () -> WalletSession,
    private val silentConnector: (suspend () -> WalletSession)? = null,
    private val publishToBus: Boolean = true,
    private val walletName: String? = null
) : WalletEvents {

    private val mutex = Mutex()
    private var currentSession: WalletSession? = null
    private var lastPublicKey: Pubkey? = null
    private var epoch: Long = 0

    private val _state = MutableStateFlow<WalletSessionState>(WalletSessionState.Disconnected())

    /**
     * Observable state machine. Emits every transition exactly once.
     *
     * Kept as a `StateFlow` (not a `SharedFlow`) so late subscribers always
     * receive the current state as their first value. Equality is structural,
     * so duplicate emissions do not produce spurious UI updates.
     */
    val state: StateFlow<WalletSessionState> = _state.asStateFlow()

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
     * Get the current session. Connects via [connector] if no session exists.
     * Thread-safe: concurrent calls share the same connection result.
     */
    suspend fun get(): WalletSession = mutex.withLock {
        currentSession ?: performConnect(silent = false)
    }

    /**
     * Force a user-visible connect regardless of whether a session exists.
     * Fires [onAccountChanged] when the new public key differs from the previous
     * session.
     */
    suspend fun connect(): WalletSession = mutex.withLock {
        performConnect(silent = false)
    }

    /**
     * Reauthorize silently using a stored auth token.
     *
     * Intended for reconnect-on-resume flows where the app already has a
     * persisted session credential and must not re-prompt the user. Falls
     * back to [connector] when no silent path is configured.
     */
    suspend fun reconnect(): WalletSession = mutex.withLock {
        performConnect(silent = true)
    }

    /**
     * Invalidate the current session without triggering a reconnect. The next
     * call to [get] or [withWallet] establishes a new connection. Fires
     * [onSessionExpired] and emits [ArtemisEvent.Wallet.SessionExpired].
     */
    suspend fun invalidate() = mutex.withLock {
        transitionToExpired()
    }

    /**
     * Disconnect the current session and clear all state. Fires [onDisconnect]
     * and emits [ArtemisEvent.Wallet.Disconnected].
     */
    suspend fun disconnect() = mutex.withLock {
        currentSession = null
        lastPublicKey = null
        disconnectCallback?.invoke()
        epoch++
        _state.value = WalletSessionState.Disconnected(epoch = epoch, atMs = currentTimeMillis())
        if (publishToBus) ArtemisEventBus.emit(ArtemisEvent.Wallet.Disconnected(reason = "user"))
    }

    /**
     * Return a session, reauthorizing silently on recoverable errors.
     *
     * Suitable for retry flows where the session may have expired between
     * two operations. On failure, the state machine transitions to
     * [WalletSessionState.Expired] and the manager attempts exactly one
     * silent reconnect before giving up.
     */
    suspend fun ensureValid(): WalletSession {
        return try {
            get()
        } catch (e: Throwable) {
            if (!isRecoverable(e)) throw e
            mutex.withLock {
                currentSession = null
                transitionToExpired()
            }
            reconnect()
        }
    }

    /**
     * Execute [action] against a valid wallet session.
     *
     * Connects on first call. On a recoverable error (session expired,
     * auth token invalid, wallet-side deauthorize), the manager silently
     * reauthorizes exactly once and replays [action]. Non-recoverable
     * errors propagate to the caller unchanged.
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
        } catch (e: Throwable) {
            if (!isRecoverable(e)) throw e
            // Drop the cached session, transition to Expired, then silently
            // reauthorize and replay the action exactly once.
            mutex.withLock {
                currentSession = null
                transitionToExpired()
            }
            val fresh = reconnect()
            action(fresh)
        }
    }

    /** Whether a session is currently held. Does not initiate a connection. */
    val isConnected: Boolean
        get() = currentSession != null

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun performConnect(silent: Boolean): WalletSession {
        epoch++
        _state.value = WalletSessionState.Connecting(
            silent = silent,
            epoch = epoch,
            atMs = currentTimeMillis()
        )

        val session: WalletSession = try {
            if (silent && silentConnector != null) {
                silentConnector.invoke()
            } else {
                connector()
            }
        } catch (e: Throwable) {
            epoch++
            _state.value = WalletSessionState.Failed(
                reason = e.message ?: e::class.simpleName ?: "unknown",
                epoch = epoch,
                atMs = currentTimeMillis()
            )
            throw e
        }

        updatePublicKey(session)
        currentSession = session

        epoch++
        _state.value = WalletSessionState.Connected(
            publicKey = runCatching { session.publicKey }.getOrElse { Pubkey(ByteArray(32)) },
            walletName = walletName,
            epoch = epoch,
            atMs = currentTimeMillis()
        )

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

    private fun transitionToExpired() {
        currentSession = null
        sessionExpiredCallback?.invoke()
        epoch++
        _state.value = WalletSessionState.Expired(epoch = epoch, atMs = currentTimeMillis())
        if (publishToBus) ArtemisEventBus.emit(ArtemisEvent.Wallet.SessionExpired())
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

    /**
     * Decide whether an error is worth retrying with a silent reauthorize.
     *
     * The heuristic looks for the common expired-session signatures surfaced
     * by the Artemis MWA adapter: [IllegalStateException] (the adapter's
     * generic "no active session" marker), plus any exception whose message
     * mentions an auth-token or session-level failure. Network errors and
     * wallet-level failures bubble up to the caller unchanged.
     */
    private fun isRecoverable(error: Throwable): Boolean {
        if (error is IllegalStateException) return true
        val message = error.message?.lowercase() ?: return false
        return "auth_token" in message ||
            "authtoken" in message ||
            "session expired" in message ||
            "invalid_authorization" in message ||
            "reauthorize" in message ||
            "not authorized" in message
    }
}
