package com.selenus.artemis.rpc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for RpcEndpointPool - health scoring, circuit breaker, and failover logic.
 */
class EndpointPoolTest {

    private val ep1 = "https://rpc1.example.com"
    private val ep2 = "https://rpc2.example.com"
    private val ep3 = "https://rpc3.example.com"

    @Test
    fun `pool requires at least one endpoint`() {
        assertFailsWith<IllegalArgumentException> {
            RpcEndpointPool(emptyList())
        }
    }

    @Test
    fun `single endpoint pool always returns that endpoint`() = runBlocking {
        val pool = RpcEndpointPool(listOf(ep1))
        assertEquals(ep1, pool.selectEndpoint())
    }

    @Test
    fun `healthy count starts at full`() {
        val pool = RpcEndpointPool(listOf(ep1, ep2, ep3))
        assertEquals(3, pool.healthyCount())
    }

    @Test
    fun `success report resets failure count`() = runBlocking {
        val pool = RpcEndpointPool(listOf(ep1, ep2))
        pool.reportFailure(ep1)
        pool.reportFailure(ep1)
        pool.reportSuccess(ep1, 50)
        // ep1 should be healthy again
        assertEquals(2, pool.healthyCount())
    }

    @Test
    fun `circuit opens after threshold failures`() = runBlocking {
        val config = RpcEndpointPool.PoolConfig(failureThreshold = 2, cooldownMs = 60_000)
        val pool = RpcEndpointPool(listOf(ep1, ep2), config)

        // Trip ep1
        pool.reportFailure(ep1)
        pool.reportFailure(ep1)

        // ep1 is now circuit-open, pool should select ep2
        assertEquals(ep2, pool.selectEndpoint())
        assertEquals(1, pool.healthyCount())
    }

    @Test
    fun `prefers lower latency endpoint`() = runBlocking {
        val pool = RpcEndpointPool(listOf(ep1, ep2))

        // Report ep2 as faster
        pool.reportSuccess(ep1, 500)
        pool.reportSuccess(ep2, 50)

        // Pool should prefer ep2 (lower latency)
        assertEquals(ep2, pool.selectEndpoint())
    }

    @Test
    fun `all endpoints tripped selects oldest failure for half-open probe`() = runBlocking {
        val config = RpcEndpointPool.PoolConfig(failureThreshold = 1, cooldownMs = 60_000)
        val pool = RpcEndpointPool(listOf(ep1, ep2), config)

        // Trip ep1 first, then ep2
        pool.reportFailure(ep1)
        Thread.sleep(10) // ensure different timestamps
        pool.reportFailure(ep2)

        // Both open - should pick ep1 (oldest failure = half-open candidate)
        assertEquals(ep1, pool.selectEndpoint())
    }

    @Test
    fun `endpoints returns all URLs`() {
        val pool = RpcEndpointPool(listOf(ep1, ep2, ep3))
        assertEquals(listOf(ep1, ep2, ep3), pool.endpoints())
    }

    @Test
    fun `latency EMA updates on success`() = runBlocking {
        val pool = RpcEndpointPool(listOf(ep1))
        // Default avg is 500ms. Report a fast response.
        pool.reportSuccess(ep1, 100)
        // EMA: 0.7*500 + 0.3*100 = 380
        val state = pool.states.first()
        assertEquals(380L, state.avgLatencyMs.get())
    }

    @Test
    fun `JsonRpcClient pool constructor compiles`() {
        val pool = RpcEndpointPool(listOf(ep1, ep2))
        val client = JsonRpcClient(pool)
        // Should not throw - just verifying construction
        assertTrue(true)
    }

    @Test
    fun `Connection pool constructor compiles`() {
        val pool = RpcEndpointPool(listOf(ep1, ep2))
        val conn = Connection(pool, Commitment.CONFIRMED)
        assertEquals(Commitment.CONFIRMED, conn.defaultCommitment)
    }
}
