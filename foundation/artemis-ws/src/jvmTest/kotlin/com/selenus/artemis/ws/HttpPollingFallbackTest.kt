package com.selenus.artemis.ws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for HttpPollingFallback.
 *
 * These tests verify the configuration, key parsing, and structural behavior
 * without requiring a live RPC endpoint.
 */
class HttpPollingFallbackTest {

    @Test
    fun `default config has sensible values`() {
        val config = HttpPollingFallback.Config()
        assertEquals(2_000L, config.pollIntervalMs)
        assertEquals(20, config.maxBatchSize)
    }

    @Test
    fun `custom config is applied`() {
        val config = HttpPollingFallback.Config(
            pollIntervalMs = 5_000L,
            maxBatchSize = 50
        )
        assertEquals(5_000L, config.pollIntervalMs)
        assertEquals(50, config.maxBatchSize)
    }

    @Test
    fun `fallback can be instantiated`() {
        val fallback = HttpPollingFallback(
            rpcEndpoint = "https://api.devnet.solana.com",
            config = HttpPollingFallback.Config(pollIntervalMs = 1_000)
        )
        assertNotNull(fallback)
    }

    @Test
    fun `fallback implements WsFallback interface`() {
        val fallback = HttpPollingFallback("https://api.devnet.solana.com")
        assertTrue(fallback is SolanaWsClient.WsFallback)
    }

    @Test
    fun `poll with empty keys does not emit`() = kotlinx.coroutines.runBlocking {
        val fallback = HttpPollingFallback("https://api.devnet.solana.com")
        val emitted = mutableListOf<WsEvent>()

        fallback.poll(emptyList()) { event ->
            emitted.add(event)
        }

        assertTrue(emitted.isEmpty())
    }
}
