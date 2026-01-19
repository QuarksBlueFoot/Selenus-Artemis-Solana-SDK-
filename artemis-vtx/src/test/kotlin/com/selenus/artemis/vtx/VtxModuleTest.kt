package com.selenus.artemis.vtx

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
 * Comprehensive tests for artemis-vtx module v1.2.0 enhancements
 * 
 * Tests VersionedTransactionBuilder, TransactionV0Extensions,
 * and DynamicPriorityFeeManager
 */
class VtxModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val testBlockhash = "11111111111111111111111111111111"

    // ==================== VersionedTransactionBuilder Tests ====================

    @Test
    fun `VersionedTransactionBuilder - create instance`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val builder = VersionedTransactionBuilder(keypair, testBlockhash)
        assertNotNull(builder)
    }

    @Test
    fun `VersionedTransactionBuilder - add single instruction`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)
        val recipient = Keypair.generate().publicKey

        val transferIx = SystemProgram.transfer(keypair.publicKey, recipient, 1000L)

        val builder = VersionedTransactionBuilder(keypair, testBlockhash)
            .addInstruction(transferIx)

        assertNotNull(builder)
    }

    @Test
    fun `VersionedTransactionBuilder - add multiple instructions vararg`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)
        val recipient1 = Keypair.generate().publicKey
        val recipient2 = Keypair.generate().publicKey

        val transfer1 = SystemProgram.transfer(keypair.publicKey, recipient1, 1000L)
        val transfer2 = SystemProgram.transfer(keypair.publicKey, recipient2, 2000L)

        val builder = VersionedTransactionBuilder(keypair, testBlockhash)
            .addInstructions(transfer1, transfer2)

        assertNotNull(builder)
    }

    @Test
    fun `VersionedTransactionBuilder - add multiple instructions list`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val instructions = (1..5).map { i ->
            SystemProgram.transfer(keypair.publicKey, Keypair.generate().publicKey, (i * 100).toLong())
        }

        val builder = VersionedTransactionBuilder(keypair, testBlockhash)
            .addInstructions(instructions)

        assertNotNull(builder)
    }

    @Test
    fun `VersionedTransactionBuilder - fluent API chaining`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val builder = VersionedTransactionBuilder(keypair, testBlockhash)
            .addInstruction(SystemProgram.transfer(keypair.publicKey, Keypair.generate().publicKey, 100L))
            .addInstruction(SystemProgram.transfer(keypair.publicKey, Keypair.generate().publicKey, 200L))
            .addInstruction(SystemProgram.transfer(keypair.publicKey, Keypair.generate().publicKey, 300L))

        assertNotNull(builder)
    }

    // ==================== DynamicPriorityFeeManager Tests ====================

    @Test
    fun `DynamicPriorityFeeManager - create instance with default config`() {
        val manager = DynamicPriorityFeeManager(testScope)
        assertNotNull(manager)
    }

    @Test
    fun `DynamicPriorityFeeManager - create instance with custom config`() {
        val manager = DynamicPriorityFeeManager(
            testScope,
            DynamicPriorityFeeManager.Config(
                defaultMicroLamports = 5000L,
                minMicroLamports = 500L,
                maxMicroLamports = 500_000L,
                historyWindowSize = 200,
                updateIntervalMs = 10_000L,
                percentileTarget = 90
            )
        )
        assertNotNull(manager)
    }

    // ==================== Config Tests ====================

    @Test
    fun `Config - default values`() {
        val config = DynamicPriorityFeeManager.Config()

        assertEquals(1000L, config.defaultMicroLamports)
        assertEquals(100L, config.minMicroLamports)
        assertEquals(100_000L, config.maxMicroLamports)
        assertEquals(100, config.historyWindowSize)
        assertEquals(5000L, config.updateIntervalMs)
        assertEquals(75, config.percentileTarget)
    }

    // ==================== FeeEstimate Tests ====================

    @Test
    fun `FeeEstimate - structure validation`() {
        val estimate = DynamicPriorityFeeManager.FeeEstimate(
            microLamportsPerUnit = 5000L,
            percentile = 75,
            sampleCount = 50,
            averageSlotDelta = 1.5f,
            confidence = 0.85f,
            source = DynamicPriorityFeeManager.FeeSource.RECENT_HISTORY
        )

        assertNotNull(estimate)
        assertEquals(5000L, estimate.microLamportsPerUnit)
        assertEquals(75, estimate.percentile)
        assertEquals(50, estimate.sampleCount)
        assertEquals(0.85f, estimate.confidence)
        assertEquals(DynamicPriorityFeeManager.FeeSource.RECENT_HISTORY, estimate.source)
    }

    // ==================== FeeSource Tests ====================

    @Test
    fun `FeeSource - all values available`() {
        val sources = DynamicPriorityFeeManager.FeeSource.values()

        assertTrue(sources.contains(DynamicPriorityFeeManager.FeeSource.RECENT_HISTORY))
        assertTrue(sources.contains(DynamicPriorityFeeManager.FeeSource.PROGRAM_PROFILE))
        assertTrue(sources.contains(DynamicPriorityFeeManager.FeeSource.NETWORK_SAMPLE))
        assertTrue(sources.contains(DynamicPriorityFeeManager.FeeSource.DEFAULT))
    }

    // ==================== FeeLevel Tests ====================

    @Test
    fun `FeeLevel - all standard levels`() {
        val minimum = DynamicPriorityFeeManager.FeeLevel.Minimum
        val low = DynamicPriorityFeeManager.FeeLevel.Low
        val medium = DynamicPriorityFeeManager.FeeLevel.Medium
        val high = DynamicPriorityFeeManager.FeeLevel.High
        val maximum = DynamicPriorityFeeManager.FeeLevel.Maximum

        assertNotNull(minimum)
        assertNotNull(low)
        assertNotNull(medium)
        assertNotNull(high)
        assertNotNull(maximum)
    }

    @Test
    fun `FeeLevel - custom level`() {
        val custom = DynamicPriorityFeeManager.FeeLevel.Custom(25_000L)

        assertNotNull(custom)
        assertEquals(25_000L, custom.microLamports)
    }

    // ==================== StateFlow Tests ====================

    @Test
    fun `DynamicPriorityFeeManager - currentEstimate initial state`() {
        val manager = DynamicPriorityFeeManager(testScope)

        val estimate = manager.currentEstimate.value

        assertNotNull(estimate)
        assertEquals(DynamicPriorityFeeManager.FeeSource.DEFAULT, estimate.source)
    }

    @Test
    fun `DynamicPriorityFeeManager - networkCongestion initial state`() {
        val manager = DynamicPriorityFeeManager(testScope)

        val congestion = manager.networkCongestion.value

        assertEquals(0f, congestion)
    }

    // ==================== VersionedTransaction Tests ====================

    @Test
    fun `MessageV0 - structure with header`() {
        val header = com.selenus.artemis.tx.MessageHeader(
            numRequiredSignatures = 1,
            numReadonlySigned = 0,
            numReadonlyUnsigned = 1
        )

        assertNotNull(header)
        assertEquals(1, header.numRequiredSignatures)
        assertEquals(0, header.numReadonlySigned)
        assertEquals(1, header.numReadonlyUnsigned)
    }

    // ==================== AddressLookupTable Tests ====================

    @Test
    fun `AddressLookupTable - create lookup table account`() {
        val tableKey = Keypair.generate().publicKey
        val addresses = (1..10).map { Keypair.generate().publicKey }

        val table = AddressLookupTableAccount(
            key = tableKey,
            addresses = addresses
        )

        assertNotNull(table)
        assertEquals(tableKey, table.key)
        assertEquals(10, table.addresses.size)
    }

    // ==================== AltOptimizer Tests ====================

    @Test
    fun `AltOptimizer - Mode enum values`() {
        val sizeMode = AltOptimizer.Mode.SIZE
        val computeMode = AltOptimizer.Mode.SIZE_AND_COMPUTE
        
        assertNotNull(sizeMode)
        assertNotNull(computeMode)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `VTX Integration - build transaction for devnet`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)
            val recipient = Keypair.generate().publicKey

            val builder = VersionedTransactionBuilder(keypair, testBlockhash)
                .addInstruction(SystemProgram.transfer(keypair.publicKey, recipient, 1000L))

            println("VTX Integration Test:")
            println("  Fee Payer: ${keypair.publicKey.toBase58()}")
            println("  Recipient: ${recipient.toBase58()}")

            assertNotNull(builder)
        }
    }

    @Test
    fun `DynamicPriorityFeeManager Integration - get current estimate`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val manager = DynamicPriorityFeeManager(testScope)
            val estimate = manager.currentEstimate.value

            println("Priority Fee Estimate:")
            println("  µL/CU: ${estimate.microLamportsPerUnit}")
            println("  Source: ${estimate.source}")
            println("  Confidence: ${estimate.confidence}")

            assertNotNull(estimate)
        }
    }
}
