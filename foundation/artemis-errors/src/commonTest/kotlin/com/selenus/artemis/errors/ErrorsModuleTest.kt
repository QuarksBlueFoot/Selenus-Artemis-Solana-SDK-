package com.selenus.artemis.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for artemis-errors module.
 * Tests ArtemisError types and error handling patterns.
 */
class ErrorsModuleTest {

    // ===== Network Errors =====

    @Test
    fun testNetworkTimeout() {
        val error = ArtemisError.NetworkTimeout()
        
        assertEquals("network_timeout", error.message)
        assertTrue(error is ArtemisError)
        assertTrue(error is RuntimeException)
    }

    @Test
    fun testNetworkTimeoutWithCause() {
        val cause = RuntimeException("Connection timed out")
        val error = ArtemisError.NetworkTimeout(cause)
        
        assertEquals("network_timeout", error.message)
        assertNotNull(error.cause)
        assertEquals("Connection timed out", error.cause?.message)
    }

    @Test
    fun testNetworkUnavailable() {
        val error = ArtemisError.NetworkUnavailable()
        
        assertEquals("network_unavailable", error.message)
    }

    @Test
    fun testRateLimited() {
        val error = ArtemisError.RateLimited()
        
        assertEquals("rate_limited", error.message)
    }

    @Test
    fun testNodeUnhealthy() {
        val error = ArtemisError.NodeUnhealthy()
        
        assertEquals("node_unhealthy", error.message)
    }

    // ===== Blockhash Errors =====

    @Test
    fun testBlockhashExpired() {
        val error = ArtemisError.BlockhashExpired()
        
        assertEquals("blockhash_expired", error.message)
    }

    @Test
    fun testBlockhashNotFound() {
        val error = ArtemisError.BlockhashNotFound()
        
        assertEquals("blockhash_not_found", error.message)
    }

    // ===== Transaction Errors =====

    @Test
    fun testSimulationFailed() {
        val logs = listOf(
            "Program log: Error processing Instruction 0",
            "Program log: custom program error: 0x1"
        )
        val error = ArtemisError.SimulationFailed(logs)
        
        assertEquals("simulation_failed", error.message)
        assertEquals(2, error.logs.size)
        assertTrue(error.logs[0].contains("Instruction 0"))
    }

    @Test
    fun testSimulationFailedEmptyLogs() {
        val error = ArtemisError.SimulationFailed()
        
        assertEquals("simulation_failed", error.message)
        assertTrue(error.logs.isEmpty())
    }

    @Test
    fun testTransactionRejected() {
        val error = ArtemisError.TransactionRejected()
        
        assertEquals("transaction_rejected", error.message)
    }

    @Test
    fun testInsufficientFunds() {
        val error = ArtemisError.InsufficientFunds()
        
        assertEquals("insufficient_funds", error.message)
    }

    // ===== Wallet Errors =====

    @Test
    fun testUserRejected() {
        val error = ArtemisError.UserRejected()
        
        assertEquals("user_rejected", error.message)
    }

    @Test
    fun testWalletUnavailable() {
        val error = ArtemisError.WalletUnavailable()
        
        assertEquals("wallet_unavailable", error.message)
    }

    // ===== Unknown Error =====

    @Test
    fun testUnknownError() {
        val error = ArtemisError.Unknown()
        
        assertEquals("unknown_error", error.message)
    }

    @Test
    fun testUnknownErrorWithCause() {
        val cause = IllegalStateException("Something went wrong")
        val error = ArtemisError.Unknown(cause)
        
        assertEquals("unknown_error", error.message)
        assertNotNull(error.cause)
    }

    // ===== Error Hierarchy Tests =====

    @Test
    fun testAllErrorsExtendArtemisError() {
        val errors = listOf(
            ArtemisError.NetworkTimeout(),
            ArtemisError.NetworkUnavailable(),
            ArtemisError.RateLimited(),
            ArtemisError.NodeUnhealthy(),
            ArtemisError.BlockhashExpired(),
            ArtemisError.BlockhashNotFound(),
            ArtemisError.SimulationFailed(),
            ArtemisError.ProgramError(
                DecodedSolanaError(
                    category = ArtemisErrorCategory.PROGRAM,
                    summary = "program_error code=0x1",
                    message = "custom program error: 0x1",
                    customProgramErrorCode = 1,
                    customProgramErrorCodeHex = "0x1"
                )
            ),
            ArtemisError.TransactionRejected(),
            ArtemisError.InsufficientFunds(),
            ArtemisError.UserRejected(),
            ArtemisError.WalletUnavailable(),
            ArtemisError.Unknown()
        )
        
        for (error in errors) {
            assertTrue(error is ArtemisError)
            assertTrue(error is RuntimeException)
        }
    }

    @Test
    fun testErrorsAreThrowable() {
        try {
            throw ArtemisError.InsufficientFunds()
        } catch (e: ArtemisError) {
            assertEquals("insufficient_funds", e.message)
        }
    }

    @Test
    fun testErrorCatchHierarchy() {
        var caughtAsArtemis = false
        var caughtAsRuntime = false
        
        try {
            throw ArtemisError.UserRejected()
        } catch (e: ArtemisError) {
            caughtAsArtemis = true
        }
        
        try {
            throw ArtemisError.UserRejected()
        } catch (e: RuntimeException) {
            caughtAsRuntime = true
        }
        
        assertTrue(caughtAsArtemis)
        assertTrue(caughtAsRuntime)
    }

    // ===== ErrorMappers Tests =====

    @Test
    fun testErrorMappersExists() {
        // Verify ErrorMappers object exists and is accessible
        assertNotNull(ErrorMappers)
    }

    @Test
    fun `SolanaErrorDecoder extracts program id instruction and custom code from logs`() {
        val programId = "11111111111111111111111111111111"
        val logs = listOf(
            "Program $programId invoke [1]",
            "Program log: AnchorError thrown in instruction: custom program error: 0x1770",
            "Program $programId failed: custom program error: 0x1770"
        )

        val decoded = SolanaErrorDecoder.decodeSimulation(
            message = "Transaction simulation failed: Error processing Instruction 2: custom program error: 0x1770",
            logs = logs
        )

        assertEquals(ArtemisErrorCategory.PROGRAM, decoded.category)
        assertEquals(2, decoded.instructionIndex)
        assertEquals(programId, decoded.programId)
        assertEquals(6000, decoded.customProgramErrorCode)
        assertEquals("0x1770", decoded.customProgramErrorCodeHex)
        assertFalse(decoded.retryable)
        assertTrue(decoded.summary.contains("instruction=2"))
    }

    @Test
    fun `SolanaErrorDecoder extracts custom code from InstructionError text`() {
        val decoded = SolanaErrorDecoder.decodeRpc(
            message = "sendTransaction failed: InstructionError(3, Custom(42))"
        )

        assertEquals(ArtemisErrorCategory.PROGRAM, decoded.category)
        assertEquals(3, decoded.instructionIndex)
        assertEquals(42, decoded.customProgramErrorCode)
        assertEquals("0x2a", decoded.customProgramErrorCodeHex)
    }

    @Test
    fun `SolanaErrorDecoder classifies retryable rpc errors`() {
        val rateLimited = SolanaErrorDecoder.decodeRpc(
            rpcCode = 429,
            message = "Too many requests"
        )
        val blockhash = SolanaErrorDecoder.decodeRpc(
            message = "Blockhash not found"
        )

        assertEquals(ArtemisErrorCategory.RATE_LIMIT, rateLimited.category)
        assertTrue(rateLimited.retryable)
        assertEquals(ArtemisErrorCategory.BLOCKHASH, blockhash.category)
        assertTrue(blockhash.retryable)
    }

    @Test
    fun `ErrorMappers map rpc program errors to structured ProgramError`() {
        val mapped = ErrorMappers.mapRpc(
            RuntimeException("Transaction simulation failed: Error processing Instruction 1: custom program error: 0x1"),
            logs = listOf("Program 11111111111111111111111111111111 failed: custom program error: 0x1")
        )

        assertTrue(mapped is ArtemisError.ProgramError)
        assertEquals(1, (mapped as ArtemisError.ProgramError).decoded.instructionIndex)
        assertEquals(1, mapped.decoded.customProgramErrorCode)
    }
}
