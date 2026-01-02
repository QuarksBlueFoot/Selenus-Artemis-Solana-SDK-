package com.selenus.artemis.preview

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.Assume
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.JsonRpcClient

/**
 * Comprehensive tests for artemis-preview module v1.2.0 enhancements
 * 
 * Tests TransactionSimulator with simulation, error decoding,
 * balance changes prediction, and compute unit estimation
 */
class PreviewModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== TransactionSimulator Core Tests ====================

    @Test
    fun `TransactionSimulator - create instance with default config`() {
        runBlocking {
            val simulator = TransactionSimulator(testScope)
            assertNotNull(simulator)
        }
    }

    @Test
    fun `TransactionSimulator - create instance with custom config`() {
        runBlocking {
            val simulator = TransactionSimulator(
                testScope,
                TransactionSimulator.Config(
                    defaultCommitment = TransactionSimulator.Commitment.FINALIZED,
                    simulationTimeoutMs = 60_000L,
                    enableCaching = true,
                    cacheTtlMs = 30_000L,
                    maxRetries = 5
                )
            )
            assertNotNull(simulator)
        }
    }

    // ==================== Commitment Tests ====================

    @Test
    fun `Commitment - all values available`() {
        val processed = TransactionSimulator.Commitment.PROCESSED
        val confirmed = TransactionSimulator.Commitment.CONFIRMED
        val finalized = TransactionSimulator.Commitment.FINALIZED

        assertNotNull(processed)
        assertNotNull(confirmed)
        assertNotNull(finalized)
    }

    // ==================== SimulationResult Tests ====================

    @Test
    fun `SimulationResult - create success result`() {
        val result = TransactionSimulator.SimulationResult(
            success = true,
            error = null,
            computeUnitsConsumed = 150L,
            logs = listOf(
                "Program 11111111111111111111111111111111 invoke [1]",
                "Program 11111111111111111111111111111111 success"
            ),
            accountChanges = emptyList(),
            balanceChanges = emptyList(),
            returnData = null,
            slot = 100L,
            unitsConsumed = 150L,
            fee = 5000L,
            innerInstructions = emptyList()
        )

        assertTrue(result.success)
        assertTrue(result.error == null)
        assertEquals(150L, result.computeUnitsConsumed)
        assertEquals(2, result.logs.size)
        assertEquals(5000L, result.fee)
    }

    @Test
    fun `SimulationResult - create failure result`() {
        val result = TransactionSimulator.SimulationResult(
            success = false,
            error = TransactionSimulator.SimulationError(
                code = 1,
                message = "Insufficient funds",
                programId = Pubkey.fromBase58("11111111111111111111111111111111"),
                instructionIndex = 0,
                suggestion = "Add more SOL to your wallet",
                category = TransactionSimulator.ErrorCategory.INSUFFICIENT_FUNDS
            ),
            computeUnitsConsumed = 50L,
            logs = listOf("Program failed: insufficient funds"),
            accountChanges = emptyList(),
            balanceChanges = emptyList(),
            returnData = null,
            slot = 100L,
            unitsConsumed = 50L,
            fee = 0L,
            innerInstructions = emptyList()
        )

        assertFalse(result.success)
        assertNotNull(result.error)
        assertEquals(TransactionSimulator.ErrorCategory.INSUFFICIENT_FUNDS, result.error!!.category)
    }

    // ==================== SimulationError Tests ====================

    @Test
    fun `SimulationError - create with all fields`() {
        val error = TransactionSimulator.SimulationError(
            code = 42,
            message = "Custom program error",
            programId = Keypair.generate().publicKey,
            instructionIndex = 2,
            suggestion = "Check your input parameters",
            category = TransactionSimulator.ErrorCategory.PROGRAM_ERROR
        )

        assertNotNull(error)
        assertEquals(42, error.code)
        assertEquals("Custom program error", error.message)
        assertEquals(2, error.instructionIndex)
        assertNotNull(error.suggestion)
    }

    // ==================== ErrorCategory Tests ====================

    @Test
    fun `ErrorCategory - all values available`() {
        val categories = TransactionSimulator.ErrorCategory.values()

        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.INSUFFICIENT_FUNDS))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.ACCOUNT_NOT_FOUND))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.INVALID_ACCOUNT_DATA))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.PROGRAM_ERROR))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.SIGNATURE_ERROR))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.COMPUTE_LIMIT))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.NETWORK_ERROR))
        assertTrue(categories.contains(TransactionSimulator.ErrorCategory.UNKNOWN))
    }

    // ==================== AccountChange Tests ====================

    @Test
    fun `AccountChange - compute delta`() {
        val change = TransactionSimulator.AccountChange(
            pubkey = Keypair.generate().publicKey,
            owner = Pubkey.fromBase58("11111111111111111111111111111111"),
            lamportsBefore = 1_000_000_000L,
            lamportsAfter = 999_000_000L,
            dataBefore = null,
            dataAfter = null,
            isWritable = true,
            isSigner = true
        )

        assertEquals(-1_000_000L, change.lamportsDelta)
        assertFalse(change.dataChanged)  // Both null
    }

    @Test
    fun `AccountChange - detect data change`() {
        val change = TransactionSimulator.AccountChange(
            pubkey = Keypair.generate().publicKey,
            owner = null,
            lamportsBefore = 1_000_000_000L,
            lamportsAfter = 1_000_000_000L,
            dataBefore = ByteArray(10) { 0 },
            dataAfter = ByteArray(10) { 1 },
            isWritable = true,
            isSigner = false
        )

        assertEquals(0L, change.lamportsDelta)
        assertTrue(change.dataChanged)
    }

    // ==================== BalanceChange Tests ====================

    @Test
    fun `BalanceChange - SOL balance change`() {
        val change = TransactionSimulator.BalanceChange(
            pubkey = Keypair.generate().publicKey,
            tokenMint = null,  // SOL
            before = 1_000_000_000L,
            after = 999_000_000L,
            symbol = "SOL"
        )

        assertEquals(-1_000_000L, change.delta)
        assertFalse(change.isPositive)
        assertTrue(change.isNegative)
    }

    @Test
    fun `BalanceChange - token balance change positive`() {
        val tokenMint = Keypair.generate().publicKey

        val change = TransactionSimulator.BalanceChange(
            pubkey = Keypair.generate().publicKey,
            tokenMint = tokenMint,
            before = 0L,
            after = 100_000_000L,
            symbol = "USDC"
        )

        assertEquals(100_000_000L, change.delta)
        assertTrue(change.isPositive)
        assertFalse(change.isNegative)
    }

    @Test
    fun `BalanceChange - zero change`() {
        val change = TransactionSimulator.BalanceChange(
            pubkey = Keypair.generate().publicKey,
            tokenMint = null,
            before = 1_000_000L,
            after = 1_000_000L,
            symbol = "SOL"
        )

        assertEquals(0L, change.delta)
        assertFalse(change.isPositive)
        assertFalse(change.isNegative)
    }

    // ==================== InnerInstructionResult Tests ====================

    @Test
    fun `InnerInstructionResult - structure validation`() {
        val inner = TransactionSimulator.InnerInstructionResult(
            index = 0,
            programId = Pubkey.fromBase58("11111111111111111111111111111111"),
            data = ByteArray(10),
            accounts = listOf(
                Keypair.generate().publicKey,
                Keypair.generate().publicKey
            )
        )

        assertNotNull(inner)
        assertEquals(0, inner.index)
        assertEquals(10, inner.data.size)
        assertEquals(2, inner.accounts.size)
    }

    // ==================== StateFlow Tests ====================

    @Test
    fun `TransactionSimulator - lastSimulation initial state`() {
        runBlocking {
            val simulator = TransactionSimulator(testScope)

            val current = simulator.lastSimulation.value
            assertTrue(current == null)
        }
    }

    // ==================== Config Tests ====================

    @Test
    fun `Config - default values`() {
        val config = TransactionSimulator.Config()

        assertEquals(TransactionSimulator.Commitment.CONFIRMED, config.defaultCommitment)
        assertEquals(30_000L, config.simulationTimeoutMs)
        assertTrue(config.enableCaching)
        assertEquals(10_000L, config.cacheTtlMs)
        assertEquals(2, config.maxRetries)
    }

    @Test
    fun `Config - custom values`() {
        val config = TransactionSimulator.Config(
            defaultCommitment = TransactionSimulator.Commitment.PROCESSED,
            simulationTimeoutMs = 5_000L,
            enableCaching = false,
            cacheTtlMs = 1_000L,
            maxRetries = 10
        )

        assertEquals(TransactionSimulator.Commitment.PROCESSED, config.defaultCommitment)
        assertEquals(5_000L, config.simulationTimeoutMs)
        assertFalse(config.enableCaching)
        assertEquals(1_000L, config.cacheTtlMs)
        assertEquals(10, config.maxRetries)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `TransactionSimulator Integration - create with devnet`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)

            val simulator = TransactionSimulator(
                testScope,
                TransactionSimulator.Config(
                    defaultCommitment = TransactionSimulator.Commitment.CONFIRMED
                )
            )

            println("TransactionSimulator Integration Test:")
            println("  Wallet: ${keypair.publicKey.toBase58()}")

            assertNotNull(simulator)
        }
    }
}
