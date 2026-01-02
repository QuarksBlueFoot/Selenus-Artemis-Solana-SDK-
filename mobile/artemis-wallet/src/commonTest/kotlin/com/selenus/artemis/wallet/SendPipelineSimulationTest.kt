package com.selenus.artemis.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for SendPipeline simulation-first feature.
 */
class SendPipelineSimulationTest {

    @Test
    fun `config simulateFirst defaults to false`() {
        val config = SendPipeline.Config()
        assertEquals(false, config.simulateFirst)
    }

    @Test
    fun `config simulateFirst can be enabled`() {
        val config = SendPipeline.Config(simulateFirst = true)
        assertTrue(config.simulateFirst)
    }

    @Test
    fun `SimulationFailedException carries reason`() {
        val ex = SendPipeline.SimulationFailedException("Insufficient funds")
        assertEquals("Insufficient funds", ex.reason)
        assertTrue(ex.message!!.contains("Simulation failed"))
        assertTrue(ex.message!!.contains("Insufficient funds"))
    }

    @Test
    fun `SimulationFailedException is a RuntimeException`() {
        val ex = SendPipeline.SimulationFailedException("test error")
        assertTrue(ex is RuntimeException)
    }

    @Test
    fun `config defaults are sensible`() {
        val config = SendPipeline.Config()
        assertEquals(3, config.maxAttempts)
        assertEquals(35, config.desiredPriority0to100)
        assertTrue(config.allowReSign)
        assertTrue(config.allowRetry)
    }

    @Test
    fun `Result carries all fields`() {
        val result = SendPipeline.Result(
            value = "sig123",
            attempts = 2,
            usedReSign = true,
            notes = listOf("Compute budget: 200k CU")
        )
        assertEquals("sig123", result.value)
        assertEquals(2, result.attempts)
        assertTrue(result.usedReSign)
        assertEquals(1, result.notes.size)
    }
}
