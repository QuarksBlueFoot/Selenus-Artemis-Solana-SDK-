package com.selenus.artemis.rpc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * RPC endpoint pool with health scoring, circuit breaker, and automatic failover.
 *
 * Each endpoint is tracked independently. On failure, the pool circuit-breaks
 * the bad endpoint and routes subsequent calls to the next healthy one.
 * After a cooldown period, the circuit half-opens and allows a probe request.
 *
 * ```kotlin
 * val pool = RpcEndpointPool(listOf(
 *     "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
 *     "https://api.mainnet-beta.solana.com"
 * ))
 *
 * val connection = Connection(pool)
 * ```
 */
class RpcEndpointPool(
    endpoints: List<String>,
    private val config: PoolConfig = PoolConfig()
) {
    init {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required" }
    }

    data class PoolConfig(
        /** Number of consecutive failures before circuit opens. */
        val failureThreshold: Int = 3,
        /** Cooldown in ms before a tripped endpoint gets a probe request. */
        val cooldownMs: Long = 30_000,
        /** Weight bonus for low-latency endpoints (higher = prefer faster). */
        val latencyWeightFactor: Double = 1.0
    )

    internal data class EndpointState(
        val url: String,
        val consecutiveFailures: AtomicInteger = AtomicInteger(0),
        val lastFailureTime: AtomicLong = AtomicLong(0),
        /** Exponential moving average of response time in ms. */
        val avgLatencyMs: AtomicLong = AtomicLong(500)
    ) {
        fun isOpen(threshold: Int, cooldownMs: Long): Boolean {
            if (consecutiveFailures.get() < threshold) return false
            // Circuit is open - check if cooldown has elapsed (half-open)
            return System.currentTimeMillis() - lastFailureTime.get() < cooldownMs
        }
    }

    internal val states: List<EndpointState> = endpoints.map { EndpointState(it) }
    private val mutex = Mutex()

    /**
     * Select the best available endpoint.
     *
     * Skips circuit-open endpoints. Among healthy endpoints, picks the one
     * with the lowest scored cost, where cost = avgLatencyMs^[latencyWeightFactor].
     *
     * latencyWeightFactor meanings:
     *   `0.0` => ignore latency, pick the first healthy endpoint deterministically
     *           (useful for debugging or when all endpoints are equally loaded).
     *   `1.0` => linear preference for lower latency (default).
     *   `>1.0` => aggressively prefer the fastest endpoint; small latency gaps
     *           become large selection biases. Use when a primary endpoint is
     *           dramatically faster than fallbacks.
     *   `<1.0` => flatter distribution; spreads traffic more evenly across
     *           comparable endpoints.
     *
     * If all endpoints are circuit-open, picks the one whose cooldown
     * expired first (half-open probe).
     */
    suspend fun selectEndpoint(): String {
        mutex.withLock {
            val healthy = states.filter { !it.isOpen(config.failureThreshold, config.cooldownMs) }
            if (healthy.isNotEmpty()) {
                val factor = config.latencyWeightFactor
                return when {
                    factor <= 0.0 -> healthy.first().url
                    // Safe invariant: `healthy.isNotEmpty()` guarantees minByOrNull returns non-null.
                    factor == 1.0 -> healthy.minByOrNull { it.avgLatencyMs.get() }!!.url
                    // Safe invariant: same as above; `healthy` is non-empty.
                    else -> healthy.minByOrNull { st ->
                        val l = st.avgLatencyMs.get().toDouble().coerceAtLeast(1.0)
                        Math.pow(l, factor)
                    }!!.url
                }
            }
            // All circuits open - pick the one with oldest failure (half-open candidate).
            // Safe invariant: `states` is non-empty per the init `require(endpoints.isNotEmpty())` block above.
            return states.minByOrNull { it.lastFailureTime.get() }!!.url
        }
    }

    /**
     * Report a successful call to an endpoint. Resets failure count and updates latency.
     */
    fun reportSuccess(endpoint: String, latencyMs: Long) {
        val state = states.find { it.url == endpoint } ?: return
        state.consecutiveFailures.set(0)
        // EMA: new = 0.7 * old + 0.3 * sample
        val old = state.avgLatencyMs.get()
        state.avgLatencyMs.set((0.7 * old + 0.3 * latencyMs).toLong())
    }

    /**
     * Report a failed call to an endpoint. Increments failure count.
     */
    fun reportFailure(endpoint: String) {
        val state = states.find { it.url == endpoint } ?: return
        state.consecutiveFailures.incrementAndGet()
        state.lastFailureTime.set(System.currentTimeMillis())
    }

    /**
     * Get the number of currently healthy endpoints.
     */
    fun healthyCount(): Int =
        states.count { !it.isOpen(config.failureThreshold, config.cooldownMs) }

    /**
     * Get all endpoint URLs.
     */
    fun endpoints(): List<String> = states.map { it.url }
}
