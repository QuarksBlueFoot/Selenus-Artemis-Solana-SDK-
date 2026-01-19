package com.selenus.artemis.compute

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

/**
 * Comprehensive tests for artemis-compute module v1.2.0 enhancements
 * 
 * Tests ComputeOptimizer with per-program profiling, presets,
 * and intelligent compute unit allocation
 */
class ComputeModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== ComputeOptimizer Core Tests ====================

    @Test
    fun `ComputeOptimizer - create instance with default config`() {
        runBlocking {
            val optimizer = ComputeOptimizer(testScope)
            assertNotNull(optimizer)
        }
    }

    @Test
    fun `ComputeOptimizer - create instance with custom config`() {
        runBlocking {
            val optimizer = ComputeOptimizer(
                testScope,
                ComputeOptimizer.Config(
                    defaultComputeUnits = 300_000,
                    minComputeUnits = 100_000,
                    maxComputeUnits = 1_200_000,
                    safetyMarginPercent = 25,
                    historyWindowSize = 100,
                    enableProfiling = true
                )
            )
            assertNotNull(optimizer)
        }
    }

    // ==================== Config Tests ====================

    @Test
    fun `Config - default values`() {
        val config = ComputeOptimizer.Config()

        assertEquals(200_000, config.defaultComputeUnits)
        assertEquals(50_000, config.minComputeUnits)
        assertEquals(1_400_000, config.maxComputeUnits)
        assertEquals(20, config.safetyMarginPercent)
        assertEquals(50, config.historyWindowSize)
        assertTrue(config.enableProfiling)
    }

    @Test
    fun `Config - custom values`() {
        val config = ComputeOptimizer.Config(
            defaultComputeUnits = 150_000,
            minComputeUnits = 25_000,
            maxComputeUnits = 1_000_000,
            safetyMarginPercent = 30,
            historyWindowSize = 25,
            enableProfiling = false
        )

        assertEquals(150_000, config.defaultComputeUnits)
        assertEquals(25_000, config.minComputeUnits)
        assertEquals(1_000_000, config.maxComputeUnits)
        assertEquals(30, config.safetyMarginPercent)
        assertEquals(25, config.historyWindowSize)
        assertFalse(config.enableProfiling)
    }

    // ==================== ComputeBudget Tests ====================

    @Test
    fun `ComputeBudget - create budget`() {
        val budget = ComputeOptimizer.ComputeBudget(
            computeUnits = 200_000,
            microLamportsPerUnit = 1000L,
            estimatedTotalLamports = 200_000_000L,
            source = ComputeOptimizer.BudgetSource.ESTIMATED,
            confidence = 0.85f,
            breakdown = emptyList()
        )

        assertNotNull(budget)
        assertEquals(200_000, budget.computeUnits)
        assertEquals(1000L, budget.microLamportsPerUnit)
        assertEquals(ComputeOptimizer.BudgetSource.ESTIMATED, budget.source)
        assertEquals(0.85f, budget.confidence)
    }

    @Test
    fun `ComputeBudget - toInstructions produces two instructions`() {
        val budget = ComputeOptimizer.ComputeBudget(
            computeUnits = 150_000,
            microLamportsPerUnit = 5000L,
            estimatedTotalLamports = 750_000_000L,
            source = ComputeOptimizer.BudgetSource.PROFILED,
            confidence = 0.95f,
            breakdown = emptyList()
        )

        val instructions = budget.toInstructions()

        assertNotNull(instructions)
        assertEquals(2, instructions.size)  // SetComputeUnitLimit + SetComputeUnitPrice
    }

    // ==================== BudgetSource Tests ====================

    @Test
    fun `BudgetSource - all values available`() {
        val sources = ComputeOptimizer.BudgetSource.values()

        assertTrue(sources.contains(ComputeOptimizer.BudgetSource.PROFILED))
        assertTrue(sources.contains(ComputeOptimizer.BudgetSource.SIMULATED))
        assertTrue(sources.contains(ComputeOptimizer.BudgetSource.ESTIMATED))
        assertTrue(sources.contains(ComputeOptimizer.BudgetSource.DEFAULT))
    }

    // ==================== InstructionEstimate Tests ====================

    @Test
    fun `InstructionEstimate - create estimate`() {
        val programId = Pubkey.fromBase58("11111111111111111111111111111111")

        val estimate = ComputeOptimizer.InstructionEstimate(
            programId = programId,
            instructionType = "transfer",
            estimatedCU = 450,
            confidence = 0.9f
        )

        assertNotNull(estimate)
        assertEquals(programId, estimate.programId)
        assertEquals("transfer", estimate.instructionType)
        assertEquals(450, estimate.estimatedCU)
        assertEquals(0.9f, estimate.confidence)
    }

    @Test
    fun `InstructionEstimate - null instruction type`() {
        val programId = Keypair.generate().publicKey

        val estimate = ComputeOptimizer.InstructionEstimate(
            programId = programId,
            instructionType = null,
            estimatedCU = 10_000,
            confidence = 0.5f
        )

        assertTrue(estimate.instructionType == null)
        assertEquals(10_000, estimate.estimatedCU)
    }

    // ==================== ComputeBudgetProgram Tests ====================

    @Test
    fun `ComputeBudgetProgram - setComputeUnitLimit`() {
        val instruction = ComputeBudgetProgram.setComputeUnitLimit(200_000)

        assertNotNull(instruction)
        assertEquals(
            Pubkey.fromBase58("ComputeBudget111111111111111111111111111111"),
            instruction.programId
        )
    }

    @Test
    fun `ComputeBudgetProgram - setComputeUnitPrice`() {
        val instruction = ComputeBudgetProgram.setComputeUnitPrice(5000L)

        assertNotNull(instruction)
        assertEquals(
            Pubkey.fromBase58("ComputeBudget111111111111111111111111111111"),
            instruction.programId
        )
    }

    // ==================== ComputeBudgetBuilder Tests ====================

    @Test
    fun `ComputeBudgetBuilder - build with all options`() {
        val builder = ComputeBudgetBuilder()
            .withComputeUnitLimit(200_000)
            .withComputeUnitPriceMicroLamports(1000L)

        val instructions = builder.buildInstructions()

        assertNotNull(instructions)
        assertEquals(2, instructions.size)  // limit + price
    }

    @Test
    fun `ComputeBudgetBuilder - buildParams produces valid Params`() {
        val builder = ComputeBudgetBuilder()
            .withComputeUnitLimit(150_000)
            .withComputeUnitPriceMicroLamports(500L)

        val params = builder.buildParams()

        assertNotNull(params)
        assertEquals(150_000, params.computeUnitLimit)
        assertEquals(500L, params.computeUnitPriceMicroLamports)
    }

    @Test
    fun `ComputeBudgetBuilder - forGameAction sets priority`() {
        val builder = ComputeBudgetBuilder()
            .forGameAction(ComputeBudgetBuilder.GamePriority.COMBAT_CRITICAL)
            .withComputeUnitLimit(200_000)

        val params = builder.buildParams()

        assertNotNull(params)
        // COMBAT_CRITICAL has priority 85, which affects price heuristic
        assertTrue(params.computeUnitPriceMicroLamports > 0)
    }

    @Test
    fun `ComputeBudgetBuilder - withDesiredPriority sets priority`() {
        val builder = ComputeBudgetBuilder()
            .withDesiredPriority(75)
            .withComputeUnitLimit(100_000)

        val params = builder.buildParams()

        assertNotNull(params)
        assertTrue(params.computeUnitLimit == 100_000)
    }

    // ==================== StateFlow Tests ====================

    @Test
    fun `ComputeOptimizer - optimization initial state`() {
        runBlocking {
            val optimizer = ComputeOptimizer(testScope)

            val current = optimizer.optimization.value
            assertTrue(current == null)
        }
    }

    // ==================== Estimate Tests ====================

    @Test
    fun `ComputeOptimizer - estimate with empty instructions`() {
        runBlocking {
            val optimizer = ComputeOptimizer(testScope)

            val budget = optimizer.estimate(emptyList())

            assertNotNull(budget)
            assertEquals(ComputeOptimizer.BudgetSource.DEFAULT, budget.source)
        }
    }

    @Test
    fun `ComputeOptimizer - estimate with single transfer`() {
        runBlocking {
            val seed = Base58.decode(testSeed)
            val keypair = Keypair.fromSeed(seed)
            val recipient = Keypair.generate().publicKey

            val optimizer = ComputeOptimizer(testScope)

            val transferIx = SystemProgram.transfer(keypair.publicKey, recipient, 1000L)
            val budget = optimizer.estimate(listOf(transferIx))

            assertNotNull(budget)
            assertTrue(budget.computeUnits > 0)
            assertTrue(budget.confidence > 0)
        }
    }

    @Test
    fun `ComputeOptimizer - estimate with priority fee`() {
        runBlocking {
            val seed = Base58.decode(testSeed)
            val keypair = Keypair.fromSeed(seed)
            val recipient = Keypair.generate().publicKey

            val optimizer = ComputeOptimizer(testScope)

            val transferIx = SystemProgram.transfer(keypair.publicKey, recipient, 1000L)
            val budget = optimizer.estimate(listOf(transferIx), priorityFee = 5000L)

            assertNotNull(budget)
            assertTrue(budget.microLamportsPerUnit >= 0)
        }
    }

    // ==================== Integration Tests ====================

    @Test
    fun `ComputeOptimizer Integration - estimate for devnet transfer`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)
            val recipient = Keypair.generate().publicKey

            val optimizer = ComputeOptimizer(testScope)

            val transferIx = SystemProgram.transfer(keypair.publicKey, recipient, 1000L)
            val budget = optimizer.estimate(listOf(transferIx))

            println("Compute Optimization Test:")
            println("  Compute Units: ${budget.computeUnits}")
            println("  Priority Fee: ${budget.microLamportsPerUnit} µL/CU")
            println("  Source: ${budget.source}")
            println("  Confidence: ${budget.confidence}")

            assertNotNull(budget)
            assertTrue(budget.computeUnits > 0)
        }
    }
}
