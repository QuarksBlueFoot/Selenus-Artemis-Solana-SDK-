package com.selenus.artemis.rpc

/**
 * Method-based RPC routing — directs specific RPC methods to designated endpoints.
 *
 * This is the "smart routing" layer that lets apps use optimal endpoints per method:
 * - Archive nodes for historical queries
 * - Low-latency endpoints for sendTransaction
 * - High-rate-limit endpoints for getAccountInfo
 *
 * ```kotlin
 * val router = RpcRouter.build {
 *     route("sendTransaction") to "https://quicknode-endpoint.com"
 *     route("getAccountInfo", "getBalance") to "https://helius-rpc.com"
 *     fallback("https://api.mainnet-beta.solana.com")
 * }
 *
 * val connection = Connection(router)
 * ```
 */
class RpcRouter private constructor(
    private val methodRoutes: Map<String, RpcEndpointPool>,
    private val fallbackPool: RpcEndpointPool,
    private val rateLimiter: RateLimiter?
) {
    /**
     * Select the best endpoint for a given RPC method.
     */
    suspend fun selectEndpoint(method: String): String {
        rateLimiter?.acquire(method)
        val pool = methodRoutes[method] ?: fallbackPool
        return pool.selectEndpoint()
    }

    /**
     * Report success for an endpoint (delegates to correct pool).
     */
    fun reportSuccess(method: String, endpoint: String, latencyMs: Long) {
        val pool = methodRoutes[method] ?: fallbackPool
        pool.reportSuccess(endpoint, latencyMs)
    }

    /**
     * Report failure for an endpoint.
     */
    fun reportFailure(method: String, endpoint: String) {
        val pool = methodRoutes[method] ?: fallbackPool
        pool.reportFailure(endpoint)
    }

    /**
     * Get the fallback pool (used for Connection construction).
     */
    fun fallbackPool(): RpcEndpointPool = fallbackPool

    companion object {
        fun build(block: Builder.() -> Unit): RpcRouter {
            val builder = Builder()
            builder.block()
            return builder.build()
        }
    }

    class Builder {
        private val routes = mutableMapOf<String, MutableList<String>>()
        private var fallbackEndpoints = mutableListOf<String>()
        private var poolConfig = RpcEndpointPool.PoolConfig()
        private var rateLimitConfig: RateLimiter.Config? = null

        /**
         * Start a route declaration for specific methods.
         */
        fun route(vararg methods: String): RouteTarget {
            return RouteTarget(methods.toList(), this)
        }

        /**
         * Set fallback endpoint(s) for methods without explicit routes.
         */
        fun fallback(vararg endpoints: String) {
            fallbackEndpoints.addAll(endpoints)
        }

        /**
         * Configure pool behavior for all endpoint groups.
         */
        fun poolConfig(config: RpcEndpointPool.PoolConfig) {
            this.poolConfig = config
        }

        /**
         * Enable per-method rate limiting.
         */
        fun rateLimit(config: RateLimiter.Config) {
            this.rateLimitConfig = config
        }

        internal fun addRoute(methods: List<String>, endpoints: List<String>) {
            for (method in methods) {
                routes.getOrPut(method) { mutableListOf() }.addAll(endpoints)
            }
        }

        internal fun build(): RpcRouter {
            require(fallbackEndpoints.isNotEmpty()) { "At least one fallback endpoint is required" }

            val fallbackPool = RpcEndpointPool(fallbackEndpoints, poolConfig)

            // Group methods by identical endpoint sets to share pools
            val endpointSetToPool = mutableMapOf<Set<String>, RpcEndpointPool>()
            val methodPools = mutableMapOf<String, RpcEndpointPool>()

            for ((method, endpoints) in routes) {
                val key = endpoints.toSet()
                val pool = endpointSetToPool.getOrPut(key) {
                    RpcEndpointPool(endpoints, poolConfig)
                }
                methodPools[method] = pool
            }

            val limiter = rateLimitConfig?.let { RateLimiter(it) }

            return RpcRouter(methodPools, fallbackPool, limiter)
        }
    }

    class RouteTarget(private val methods: List<String>, private val builder: Builder) {
        infix fun to(endpoint: String) {
            builder.addRoute(methods, listOf(endpoint))
        }

        infix fun to(endpoints: List<String>) {
            builder.addRoute(methods, endpoints)
        }
    }
}

/**
 * Simple token-bucket rate limiter for RPC methods.
 *
 * Prevents hammering endpoints beyond their rate limits.
 */
class RateLimiter(private val config: Config) {

    data class Config(
        /** Max requests per second across all methods. */
        val maxRequestsPerSecond: Int = 40,
        /** Per-method overrides. */
        val methodLimits: Map<String, Int> = emptyMap()
    )

    private val globalTokens = java.util.concurrent.atomic.AtomicInteger(config.maxRequestsPerSecond)
    private val methodTokens = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    @Volatile private var lastRefillMs = System.currentTimeMillis()

    /**
     * Acquire a token for the given method. Suspends briefly if rate limit is hit.
     */
    suspend fun acquire(method: String) {
        refillIfNeeded()

        val methodLimit = config.methodLimits[method]
        if (methodLimit != null) {
            val tokens = methodTokens.getOrPut(method) {
                java.util.concurrent.atomic.AtomicInteger(methodLimit)
            }
            while (tokens.get() <= 0) {
                kotlinx.coroutines.delay(50)
                refillIfNeeded()
            }
            tokens.decrementAndGet()
        }

        while (globalTokens.get() <= 0) {
            kotlinx.coroutines.delay(50)
            refillIfNeeded()
        }
        globalTokens.decrementAndGet()
    }

    private fun refillIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastRefillMs >= 1000) {
            lastRefillMs = now
            globalTokens.set(config.maxRequestsPerSecond)
            for ((method, limit) in config.methodLimits) {
                methodTokens[method]?.set(limit)
            }
        }
    }
}
