package com.selenus.artemis.rpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for BlockhashCache and RpcRouter.
 */
class BlockhashCacheAndRouterTest {

    // ════════════════════════════════════════════════════════════════════════
    // RpcRouter tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `router requires fallback endpoint`() {
        assertFailsWith<IllegalArgumentException> {
            RpcRouter.build {
                route("sendTransaction") to "https://fast.example.com"
                // no fallback
            }
        }
    }

    @Test
    fun `router builds successfully with fallback`() {
        val router = RpcRouter.build {
            fallback("https://fallback.example.com")
        }
        assertNotNull(router)
    }

    @Test
    fun `router directs method to correct endpoint`() = kotlinx.coroutines.runBlocking {
        val router = RpcRouter.build {
            route("sendTransaction") to "https://fast.example.com"
            route("getAccountInfo", "getBalance") to "https://read.example.com"
            fallback("https://fallback.example.com")
        }

        val sendEndpoint = router.selectEndpoint("sendTransaction")
        assertEquals("https://fast.example.com", sendEndpoint)

        val readEndpoint = router.selectEndpoint("getAccountInfo")
        assertEquals("https://read.example.com", readEndpoint)

        val balanceEndpoint = router.selectEndpoint("getBalance")
        assertEquals("https://read.example.com", balanceEndpoint)
    }

    @Test
    fun `router falls back for unknown methods`() = kotlinx.coroutines.runBlocking {
        val router = RpcRouter.build {
            route("sendTransaction") to "https://fast.example.com"
            fallback("https://fallback.example.com")
        }

        val endpoint = router.selectEndpoint("getSlot")
        assertEquals("https://fallback.example.com", endpoint)
    }

    @Test
    fun `router reports success and failure correctly`() = kotlinx.coroutines.runBlocking {
        val router = RpcRouter.build {
            route("sendTransaction") to "https://fast.example.com"
            fallback("https://fallback.example.com")
        }

        // Report success
        router.reportSuccess("sendTransaction", "https://fast.example.com", 50)

        // Report failure
        router.reportFailure("sendTransaction", "https://fast.example.com")

        // Should still be usable (not circuit-broken after 1 failure)
        val endpoint = router.selectEndpoint("sendTransaction")
        assertEquals("https://fast.example.com", endpoint)
    }

    @Test
    fun `router shares pool for methods with same endpoints`() = kotlinx.coroutines.runBlocking {
        val router = RpcRouter.build {
            route("getAccountInfo", "getBalance") to "https://read.example.com"
            fallback("https://fallback.example.com")
        }

        // Both methods should resolve to the same endpoint
        assertEquals(
            router.selectEndpoint("getAccountInfo"),
            router.selectEndpoint("getBalance")
        )
    }

    @Test
    fun `router supports multiple fallback endpoints`() = kotlinx.coroutines.runBlocking {
        val router = RpcRouter.build {
            fallback("https://primary.example.com", "https://secondary.example.com")
        }

        // Should get the primary (lowest latency by default)
        val endpoint = router.selectEndpoint("anything")
        assertTrue(
            endpoint == "https://primary.example.com" || endpoint == "https://secondary.example.com"
        )
    }

    @Test
    fun `router can be used with Connection`() {
        val router = RpcRouter.build {
            route("sendTransaction") to "https://fast.example.com"
            fallback("https://fallback.example.com")
        }

        // Should not throw
        val connection = Connection(router, Commitment.CONFIRMED)
        assertNotNull(connection)
    }

    @Test
    fun `router can be used with JsonRpcClient`() {
        val router = RpcRouter.build {
            fallback("https://fallback.example.com")
        }

        val client = JsonRpcClient(router)
        assertNotNull(client)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RateLimiter tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `rate limiter allows requests within limit`() = kotlinx.coroutines.runBlocking {
        val limiter = RateLimiter(RateLimiter.Config(maxRequestsPerSecond = 100))

        // Should not block for a few requests
        for (i in 1..5) {
            limiter.acquire("getBalance") // Should complete quickly
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // BlockhashCache tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `cached blockhash detects staleness`() {
        val fresh = BlockhashCache.CachedBlockhash(
            blockhash = "abc",
            lastValidBlockHeight = 100,
            fetchedAtMs = System.currentTimeMillis()
        )
        assertTrue(!fresh.isStale(60_000))

        val stale = BlockhashCache.CachedBlockhash(
            blockhash = "abc",
            lastValidBlockHeight = 100,
            fetchedAtMs = System.currentTimeMillis() - 120_000
        )
        assertTrue(stale.isStale(60_000))
    }

    @Test
    fun `BlockhashCache config has sensible defaults`() {
        val config = BlockhashCache.Config()
        assertEquals(30_000, config.refreshIntervalMs)
        assertEquals("finalized", config.commitment)
        assertEquals(60_000, config.maxAgeMs)
    }
}
