package com.selenus.artemis.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Coverage for the standalone [CircuitBreaker] semantics:
 *  - Closed → Open on threshold of consecutive failures
 *  - Open → fast-fail with [CircuitBreakerOpenException]
 *  - Open → HalfOpen on cooldown elapse
 *  - HalfOpen → Closed on probe success
 *  - HalfOpen → Open on probe failure (no extra failures needed)
 *  - CancellationException doesn't count as a failure
 *  - reset() clears state regardless of current position
 */
class CircuitBreakerTest {

    private fun newBreaker(
        threshold: Int = 3,
        cooldownMs: Long = 1_000L,
        successThreshold: Int = 1,
        clock: AtomicLong = AtomicLong(0L)
    ): Pair<CircuitBreaker, AtomicLong> {
        val breaker = CircuitBreaker(
            name = "test",
            config = CircuitBreaker.Config(
                failureThreshold = threshold,
                cooldownMs = cooldownMs,
                successThresholdInHalfOpen = successThreshold
            ),
            clock = { clock.get() }
        )
        return breaker to clock
    }

    @Test
    fun `breaker starts Closed and stays Closed under successful calls`() = runBlocking {
        val (breaker, _) = newBreaker()
        repeat(10) { breaker.execute { "ok" } }
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `consecutive failures trip the breaker after threshold`() = runBlocking {
        val (breaker, _) = newBreaker(threshold = 3)
        // Two failures: still Closed.
        repeat(2) {
            try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        }
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
        // Third failure trips.
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        assertTrue(breaker.state is CircuitBreaker.State.Open)
    }

    @Test
    fun `open breaker fast-fails with CircuitBreakerOpenException`() = runBlocking<Unit> {
        val (breaker, _) = newBreaker(threshold = 1)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        assertTrue(breaker.state is CircuitBreaker.State.Open)
        try {
            breaker.execute<Unit> { fail("block must not run while open") }
        } catch (e: CircuitBreakerOpenException) {
            assertEquals("test", e.name)
            assertTrue(e.cooldownRemainingMs >= 0)
        }
    }

    @Test
    fun `cooldown transitions Open to HalfOpen and probe success closes`() = runBlocking {
        val (breaker, clock) = newBreaker(threshold = 1, cooldownMs = 500L)
        clock.set(1_000L)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        assertTrue(breaker.state is CircuitBreaker.State.Open)

        // Advance past cooldown, breaker should auto-flip to HalfOpen on next access.
        clock.set(1_600L)
        // Probe call succeeds, closes the breaker.
        val out = breaker.execute { "probe" }
        assertEquals("probe", out)
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `failed probe re-opens the breaker without needing a fresh threshold`() = runBlocking<Unit> {
        val (breaker, clock) = newBreaker(threshold = 1, cooldownMs = 500L)
        clock.set(1_000L)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        clock.set(1_600L)
        // Probe call fails. should immediately re-open.
        try { breaker.execute<Unit> { error("still bad") } } catch (_: IllegalStateException) {}
        assertTrue(breaker.state is CircuitBreaker.State.Open)
    }

    @Test
    fun `CancellationException does not count as a failure`() = runBlocking<Unit> {
        val (breaker, _) = newBreaker(threshold = 2)
        repeat(5) {
            try {
                breaker.execute<Unit> { throw kotlinx.coroutines.CancellationException("user") }
            } catch (_: kotlinx.coroutines.CancellationException) { /* expected */ }
        }
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `successThresholdInHalfOpen requires that many probes before closing`() = runBlocking {
        val (breaker, clock) = newBreaker(threshold = 1, cooldownMs = 100L, successThreshold = 2)
        clock.set(1_000L)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}

        clock.set(1_200L)
        breaker.execute { "probe-1" }
        // One success in half-open isn't enough yet.
        assertTrue(breaker.state is CircuitBreaker.State.HalfOpen)
        breaker.execute { "probe-2" }
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `reset forces the breaker back to Closed`() = runBlocking<Unit> {
        val (breaker, _) = newBreaker(threshold = 1)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        assertTrue(breaker.state is CircuitBreaker.State.Open)
        breaker.reset()
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `recordSuccess and recordFailure work for non-suspend callsites`() {
        val (breaker, _) = newBreaker(threshold = 2)
        breaker.recordSuccess()
        breaker.recordFailure()
        breaker.recordFailure()
        assertTrue(breaker.state is CircuitBreaker.State.Open)
        breaker.reset()
        assertTrue(breaker.state is CircuitBreaker.State.Closed)
    }

    @Test
    fun `stateFlow surfaces transitions to subscribers`() = runBlocking {
        val (breaker, _) = newBreaker(threshold = 1)
        val initial = breaker.stateFlow.value
        assertSame(CircuitBreaker.State.Closed, initial)
        try { breaker.execute<Unit> { error("boom") } } catch (_: IllegalStateException) {}
        // After the trip, stateFlow.value should be Open.
        val tripped = breaker.stateFlow.value
        assertTrue(tripped is CircuitBreaker.State.Open)
    }
}
