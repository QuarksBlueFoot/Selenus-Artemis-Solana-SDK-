/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * ArtemisRuntime - KMP-safe central orchestration surface.
 *
 * The Android entrypoint [com.selenus.artemis.wallet.mwa.ArtemisMobile] wires
 * the full mobile stack (RPC, MWA wallet, transaction engine, realtime, DAS,
 * marketplace) in a single call. That class is Android-only because it needs
 * an `Activity` for the MWA intent handshake.
 *
 * [ArtemisRuntime] fills the same role for every non-Android target and for
 * apps that wire their own wallet adapter: it owns the cross-subsystem
 * lifecycle (start, stop, shared scope) and re-publishes the framework event
 * bus behind a strongly-typed API. JVM servers, iOS / native targets, and
 * headless backends can instantiate [ArtemisRuntime] directly.
 *
 * ```kotlin
 * val runtime = ArtemisRuntime(
 *     config = ArtemisRuntime.Config(
 *         appName = "MyApp",
 *         cluster = "solana:mainnet"
 *     )
 * )
 *
 * runtime.events
 *     .onEach { event -> log.info("artemis event: $event") }
 *     .launchIn(runtime.scope)
 *
 * runtime.start()
 * ```
 *
 * The runtime owns:
 *
 * - a [CoroutineScope] that all subsystems should share
 * - the [ArtemisEventBus] handle and a typed [events] stream
 * - a typed [state] StateFlow describing the aggregate lifecycle
 *
 * It does NOT own RPC or wallet instances directly. Those are constructed
 * per-target by the caller (via `ArtemisClient` on JVM or `ArtemisMobile.create`
 * on Android) and attached to the runtime using [attach]. Keeping the wiring
 * decoupled is what allows a single `ArtemisRuntime` to coordinate different
 * deployment shapes without dragging Android dependencies into KMP commonMain.
 */
class ArtemisRuntime(
    val config: Config = Config(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    /**
     * Runtime configuration.
     *
     * @param appName        Human-readable app name included in emitted events.
     * @param cluster        Solana cluster identifier ("solana:mainnet", etc.).
     * @param publishToBus   When true, every subsystem registered via [attach]
     *                       mirrors its lifecycle into [ArtemisEventBus].
     */
    data class Config(
        val appName: String = "Artemis",
        val cluster: String = "solana:mainnet",
        val publishToBus: Boolean = true
    )

    /** Aggregate runtime lifecycle. */
    sealed class State {
        data object Idle : State()
        data object Running : State()
        data class Stopped(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)

    /** Observable runtime state for supervisors and UI layers. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Shared framework event stream. */
    val events: SharedFlow<ArtemisEvent> = ArtemisEventBus.events

    /** Convenience filtered stream for wallet events. */
    val walletEvents: Flow<ArtemisEvent.Wallet> = events.filterIsInstance()

    /** Convenience filtered stream for transaction events. */
    val txEvents: Flow<ArtemisEvent.Tx> = events.filterIsInstance()

    /** Convenience filtered stream for realtime events. */
    val realtimeEvents: Flow<ArtemisEvent.Realtime> = events.filterIsInstance()

    private val subsystems = mutableListOf<Subsystem>()

    /**
     * Attach a subsystem to the runtime.
     *
     * Subsystems are lightweight adapter objects that expose `start` and `stop`
     * hooks. The runtime invokes them in registration order on [start] and in
     * reverse order on [stop]. This pattern lets the runtime drive any
     * subsystem (realtime websocket, blockhash cache refresh loop, portfolio
     * tracker, custom watchers) without needing compile-time knowledge of them.
     */
    fun attach(subsystem: Subsystem) {
        subsystems.add(subsystem)
    }

    /**
     * Start every attached subsystem on the shared [scope]. Transitions
     * [state] to [State.Running].
     */
    fun start() {
        if (_state.value is State.Running) return
        scope.launch {
            subsystems.forEach { runCatching { it.start(this@ArtemisRuntime) } }
        }
        _state.value = State.Running
    }

    /**
     * Stop every attached subsystem in reverse order. Transitions [state] to
     * [State.Stopped]. Does not cancel [scope]; long-lived apps can resume
     * by calling [start] again.
     */
    fun stop(reason: String = "user") {
        if (_state.value !is State.Running) return
        subsystems.asReversed().forEach { runCatching { it.stop() } }
        _state.value = State.Stopped(reason)
    }

    /**
     * Emit a custom event onto the framework bus.
     *
     * Apps that want to interleave their own signals with SDK events (banner
     * toggles, feature-flag flips, analytics markers) use this so their
     * events flow through the same [events] stream that Compose layers
     * already observe.
     */
    fun emit(tag: String, payload: Any? = null) {
        ArtemisEventBus.emit(ArtemisEvent.Custom(tag = tag, payload = payload))
    }

    /**
     * A component that participates in the runtime lifecycle.
     *
     * Implementations are usually thin wrappers around an existing subsystem.
     * For example, the realtime websocket adapter in artemis-ws provides a
     * `Subsystem` that calls `RealtimeEngine.connect()` in `start()` and
     * `RealtimeEngine.close()` in `stop()`.
     */
    interface Subsystem {
        /** Invoked once when the runtime starts. Must be idempotent. */
        suspend fun start(runtime: ArtemisRuntime) {}

        /** Invoked once when the runtime stops. Must be idempotent. */
        fun stop() {}
    }
}
