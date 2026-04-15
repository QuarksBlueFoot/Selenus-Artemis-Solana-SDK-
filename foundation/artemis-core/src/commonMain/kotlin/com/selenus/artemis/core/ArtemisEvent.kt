package com.selenus.artemis.core

import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * ArtemisEvent — unified framework event surface.
 *
 * Every subsystem in the SDK (wallet, transactions, realtime, DAS) publishes
 * lifecycle events through [ArtemisEventBus]. Apps observe a single stream
 * and decide what to react to — no more per-module listener wiring.
 *
 * ```kotlin
 * ArtemisEventBus.events
 *     .onEach { event ->
 *         when (event) {
 *             is ArtemisEvent.Wallet.Connected      -> showBanner("connected")
 *             is ArtemisEvent.Tx.Confirmed          -> refreshBalances()
 *             is ArtemisEvent.Realtime.StateChanged -> banner.update(event.state)
 *             else -> Unit
 *         }
 *     }
 *     .launchIn(scope)
 * ```
 *
 * Observing a single subsystem is a one-liner:
 *
 * ```kotlin
 * ArtemisEventBus.wallet().onEach { ... }.launchIn(scope)
 * ArtemisEventBus.tx().onEach { ... }.launchIn(scope)
 * ```
 *
 * Each event carries a [timestamp] and a [source] tag so downstream analytics
 * can reason about ordering across subsystems without needing their own clock.
 */
sealed class ArtemisEvent {
    /** Wall-clock millis when the event was created. */
    abstract val timestamp: Long

    /** Stable subsystem tag — useful for filtering, metrics, and logging. */
    abstract val source: Source

    enum class Source { WALLET, TX, REALTIME, DAS, CUSTOM }

    // ─── Wallet ─────────────────────────────────────────────────────────────

    sealed class Wallet : ArtemisEvent() {
        final override val source: Source = Source.WALLET

        data class Connected(
            val publicKey: String,
            val walletName: String? = null,
            override val timestamp: Long = currentTimeMillis()
        ) : Wallet()

        data class Disconnected(
            val reason: String = "user",
            override val timestamp: Long = currentTimeMillis()
        ) : Wallet()

        data class SessionExpired(
            override val timestamp: Long = currentTimeMillis()
        ) : Wallet()

        data class AccountChanged(
            val previousKey: String?,
            val currentKey: String,
            override val timestamp: Long = currentTimeMillis()
        ) : Wallet()
    }

    // ─── Transactions ───────────────────────────────────────────────────────

    sealed class Tx : ArtemisEvent() {
        final override val source: Source = Source.TX

        data class Sent(
            val signature: String,
            override val timestamp: Long = currentTimeMillis()
        ) : Tx()

        data class Confirmed(
            val signature: String,
            val slot: Long? = null,
            override val timestamp: Long = currentTimeMillis()
        ) : Tx()

        data class Failed(
            val signature: String?,
            val message: String,
            override val timestamp: Long = currentTimeMillis()
        ) : Tx()

        data class Retrying(
            val signature: String?,
            val attempt: Int,
            val reason: String,
            override val timestamp: Long = currentTimeMillis()
        ) : Tx()
    }

    // ─── Realtime ──────────────────────────────────────────────────────────

    sealed class Realtime : ArtemisEvent() {
        final override val source: Source = Source.REALTIME

        /** Emitted on every [com.selenus.artemis.ws.ConnectionState] transition. */
        data class StateChanged(
            val stateName: String,
            val endpoint: String?,
            val epoch: Long,
            override val timestamp: Long = currentTimeMillis()
        ) : Realtime()

        data class AccountUpdated(
            val publicKey: String,
            val lamports: Long?,
            val slot: Long?,
            override val timestamp: Long = currentTimeMillis()
        ) : Realtime()

        data class SignatureObserved(
            val signature: String,
            val confirmed: Boolean,
            override val timestamp: Long = currentTimeMillis()
        ) : Realtime()
    }

    // ─── DAS ────────────────────────────────────────────────────────────────

    sealed class Das : ArtemisEvent() {
        final override val source: Source = Source.DAS

        data class ProviderFailover(
            val reason: String,
            override val timestamp: Long = currentTimeMillis()
        ) : Das()
    }

    /**
     * Custom event — escape hatch for apps that want to multiplex their own
     * events through the bus. Attach any tag you like.
     */
    data class Custom(
        val tag: String,
        val payload: Any? = null,
        override val timestamp: Long = currentTimeMillis()
    ) : ArtemisEvent() {
        override val source: Source = Source.CUSTOM
    }
}

/**
 * ArtemisEventBus — the single shared stream for every [ArtemisEvent].
 *
 * A [SharedFlow] sized to absorb bursts from wallet+realtime+tx without losing events.
 * Collectors never backpressure the emitter — instead, the bus drops the oldest event
 * when it can't keep up, which is the right tradeoff for a UI-facing event surface.
 */
object ArtemisEventBus {

    private val _events = MutableSharedFlow<ArtemisEvent>(
        replay = 0,
        extraBufferCapacity = 128
    )

    /** Hot stream of every event from every subsystem. */
    val events: SharedFlow<ArtemisEvent> = _events.asSharedFlow()

    /** Emit an event. Non-suspending; uses `tryEmit` for drop-oldest semantics. */
    fun emit(event: ArtemisEvent) {
        _events.tryEmit(event)
    }

    /** Wallet-only stream. */
    fun wallet(): Flow<ArtemisEvent.Wallet> = events.filterIsInstance()

    /** Transaction-only stream. */
    fun tx(): Flow<ArtemisEvent.Tx> = events.filterIsInstance()

    /** Realtime-only stream. */
    fun realtime(): Flow<ArtemisEvent.Realtime> = events.filterIsInstance()

    /** DAS-only stream. */
    fun das(): Flow<ArtemisEvent.Das> = events.filterIsInstance()

    /**
     * Filter by any subsystem tag. Useful when apps want to group custom
     * events with first-party ones under a common label.
     */
    fun bySource(source: ArtemisEvent.Source): Flow<ArtemisEvent> =
        events.filter { it.source == source }

    /** Stream of event timestamps for latency measurement. */
    fun timestamps(): Flow<Long> = events.map { it.timestamp }
}
