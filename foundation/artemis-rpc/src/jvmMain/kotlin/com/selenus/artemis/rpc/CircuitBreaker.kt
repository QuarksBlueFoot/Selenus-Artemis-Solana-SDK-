package com.selenus.artemis.rpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Three-state circuit breaker.
 *
 *  - **Closed**: normal operation, calls flow through.
 *  - **Open**: fast-fail without dispatching the call.
 *  - **HalfOpen**: a single probe is allowed; on success the circuit
 *    closes, on failure it opens again with the same cooldown.
 *
 * Use directly to wrap any call that talks to an unreliable upstream:
 *
 * ```
 * val breaker = CircuitBreaker(name = "helius")
 * val result = breaker.execute { rpc.getBalance(pubkey) }
 * ```
 *
 * Or pass a list of breakers to [RpcEndpointPool] to share circuit
 * state across the pool's failover routing.
 *
 * Thread-safe through atomic counters; the breaker keeps no per-call
 * coroutine context, so it is safe to share between threads / coroutines.
 *
 * @property name Human-readable identifier surfaced through metrics
 *   and the [stateFlow]. Endpoint pools use the URL.
 * @property config Failure threshold + cooldown policy.
 * @property clock Wall-clock provider; defaults to system time. Tests
 *   inject a deterministic clock to advance through cooldown deterministically.
 */
class CircuitBreaker(
    val name: String,
    private val config: Config = Config(),
    private val clock: () -> Long = { currentTimeMillisSafe() }
) {
    /**
     * Tunable policy for the breaker. Defaults match the production
     * pool's behaviour: trip after 3 consecutive failures, hold open
     * for 30 seconds, then half-open for one probe.
     *
     * @property failureThreshold Consecutive failures that trip the breaker.
     * @property cooldownMs Wall-clock ms the breaker stays open before
     *   transitioning to half-open. The next call is the probe.
     * @property successThresholdInHalfOpen Consecutive successes
     *   required while half-open before fully closing. Defaults to 1
     *   (one good call closes the breaker); higher values demand more
     *   evidence before declaring the upstream healthy again.
     */
    data class Config(
        val failureThreshold: Int = 3,
        val cooldownMs: Long = 30_000L,
        val successThresholdInHalfOpen: Int = 1
    ) {
        init {
            require(failureThreshold > 0) { "failureThreshold must be positive" }
            require(cooldownMs > 0) { "cooldownMs must be positive" }
            require(successThresholdInHalfOpen > 0) {
                "successThresholdInHalfOpen must be positive"
            }
        }
    }

    /**
     * Three-state lattice. [Open] carries the wall-clock time when it
     * was tripped so callers can render a "retry in X seconds" UI;
     * [HalfOpen] is reached automatically when the cooldown elapses
     * and the next call probes.
     */
    sealed class State {
        object Closed : State() { override fun toString() = "Closed" }
        data class Open(val openedAtMs: Long) : State()
        object HalfOpen : State() { override fun toString() = "HalfOpen" }
    }

    private val consecutiveFailures = AtomicInteger(0)
    private val halfOpenSuccesses = AtomicInteger(0)
    private val openedAtMs = AtomicLong(0L)
    private val _state = MutableStateFlow<State>(State.Closed)

    /**
     * Live state observable. Multiple collectors fan out from the same
     * underlying [MutableStateFlow]; transitions are deduplicated.
     */
    val stateFlow: StateFlow<State> = _state.asStateFlow()

    /** Snapshot of the current state. */
    val state: State get() = transitionStateIfNeeded()

    /** Convenience predicate. True when the breaker is currently fast-failing. */
    val isOpen: Boolean get() = state is State.Open

    /**
     * Run [block] under the breaker. Behaviour:
     *  - Closed: invoke immediately. Success records a success;
     *    exception (other than [CancellationException]) records a
     *    failure and may trip the breaker.
     *  - Open: throw [CircuitBreakerOpenException] without invoking
     *    the block. The exception carries the remaining cooldown.
     *  - HalfOpen: invoke as the probe. Success closes; failure
     *    re-opens with the same cooldown.
     *
     * [CancellationException] passes through unchanged and does NOT
     * count as a failure (cancellation is the caller's choice, not
     * upstream unreliability).
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        when (val cur = transitionStateIfNeeded()) {
            is State.Open -> {
                val remaining = config.cooldownMs - (clock() - cur.openedAtMs)
                throw CircuitBreakerOpenException(
                    name = name,
                    cooldownRemainingMs = remaining.coerceAtLeast(0L)
                )
            }
            is State.Closed, is State.HalfOpen -> Unit
        }
        try {
            val result = block()
            recordSuccess()
            return result
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            recordFailure()
            throw t
        }
    }

    /**
     * Manually report a successful call. Use when wrapping the breaker
     * around a non-suspend callsite (e.g., synchronous Java APIs)
     * where [execute] doesn't fit.
     */
    fun recordSuccess() {
        val s = transitionStateIfNeeded()
        when (s) {
            is State.HalfOpen -> {
                val n = halfOpenSuccesses.incrementAndGet()
                if (n >= config.successThresholdInHalfOpen) {
                    consecutiveFailures.set(0)
                    halfOpenSuccesses.set(0)
                    _state.value = State.Closed
                }
            }
            is State.Closed -> {
                consecutiveFailures.set(0)
            }
            is State.Open -> {
                // Spurious success after open (raced with transition);
                // ignore, the breaker stays open until cooldown elapses.
            }
        }
    }

    /** Manually report a failed call. */
    fun recordFailure() {
        val n = consecutiveFailures.incrementAndGet()
        if (n >= config.failureThreshold || _state.value is State.HalfOpen) {
            // Trip (from closed) or re-open (from half-open after a
            // failed probe). Either way, reset the half-open counter.
            halfOpenSuccesses.set(0)
            val now = clock()
            openedAtMs.set(now)
            _state.value = State.Open(now)
        }
    }

    /**
     * Force the breaker back to [State.Closed]. Use to bypass the
     * cooldown when an external signal (e.g., a manual health probe)
     * has confirmed the upstream is healthy.
     */
    fun reset() {
        consecutiveFailures.set(0)
        halfOpenSuccesses.set(0)
        openedAtMs.set(0L)
        _state.value = State.Closed
    }

    private fun transitionStateIfNeeded(): State {
        val cur = _state.value
        if (cur !is State.Open) return cur
        val elapsed = clock() - cur.openedAtMs
        if (elapsed < config.cooldownMs) return cur
        // Atomically flip Open -> HalfOpen so two concurrent probes
        // don't both think they're "the" probe.
        if (_state.compareAndSet(cur, State.HalfOpen)) {
            return State.HalfOpen
        }
        return _state.value
    }
}

/**
 * Thrown by [CircuitBreaker.execute] when the breaker is open.
 * Distinct from any upstream error so callers can branch on
 * "fast-failed locally" vs. "upstream returned an error".
 */
class CircuitBreakerOpenException(
    val name: String,
    val cooldownRemainingMs: Long
) : RuntimeException(
    "circuit breaker `$name` is open; retry in ${cooldownRemainingMs}ms"
)

internal fun currentTimeMillisSafe(): Long = System.currentTimeMillis()
