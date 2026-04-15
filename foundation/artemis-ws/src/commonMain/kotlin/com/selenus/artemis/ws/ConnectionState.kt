package com.selenus.artemis.ws

/**
 * ConnectionState — explicit transport state for realtime subscriptions.
 *
 * A typed state machine for the websocket lifecycle. Exposed as a
 * [kotlinx.coroutines.flow.StateFlow] by [RealtimeEngine.state] so UI
 * layers and supervisors can react deterministically to transport changes
 * instead of parsing raw [WsEvent]s.
 *
 * Transitions:
 * ```
 *  Idle ─connect()──▶ Connecting ─onOpen──▶ Connected
 *                          │                    │
 *                          │                    ▼
 *                          │              onFailure / onClosed
 *                          │                    │
 *                          ▼                    ▼
 *                      Reconnecting  ◀──────────┘
 *                          │
 *                          │ onOpen
 *                          ▼
 *                       Connected
 *                          │ close()
 *                          ▼
 *                       Closed
 * ```
 *
 * Every state carries a monotonically increasing [epoch] so observers can
 * tell a fresh connect from a reconnect even if they both land on [Connected].
 */
sealed class ConnectionState {
    /** Monotonic counter that increments on every state change. Useful for dedupe. */
    abstract val epoch: Long

    /** Wall-clock millis the state was entered. */
    abstract val atMs: Long

    /** Initial state before the first [RealtimeEngine.connect] call. */
    data class Idle(
        override val epoch: Long = 0,
        override val atMs: Long = 0
    ) : ConnectionState()

    /** Transport socket is opening for the first time. */
    data class Connecting(
        val endpoint: String,
        override val epoch: Long,
        override val atMs: Long
    ) : ConnectionState()

    /** Transport socket is open and ready to carry subscriptions. */
    data class Connected(
        val endpoint: String,
        val subscriptions: Int,
        override val epoch: Long,
        override val atMs: Long
    ) : ConnectionState()

    /**
     * Transport was lost. A backoff-scheduled reconnect is in progress.
     * [attempt] starts at 1 for the first retry and resets after a successful connect.
     */
    data class Reconnecting(
        val endpoint: String,
        val attempt: Int,
        val nextDelayMs: Long,
        val reason: String,
        override val epoch: Long,
        override val atMs: Long
    ) : ConnectionState()

    /**
     * Terminal state — [RealtimeEngine.close] was called or the max reconnect budget was exhausted.
     * A new [RealtimeEngine] instance is required to resume.
     */
    data class Closed(
        val reason: String,
        override val epoch: Long,
        override val atMs: Long
    ) : ConnectionState()

    /** True when the socket is open and subscriptions can be created. */
    val isLive: Boolean get() = this is Connected

    /** True when the engine is in a terminal state and no further work will happen. */
    val isTerminal: Boolean get() = this is Closed
}
