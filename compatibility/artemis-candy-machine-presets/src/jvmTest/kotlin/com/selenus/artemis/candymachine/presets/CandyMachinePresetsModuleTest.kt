package com.selenus.artemis.candymachine.presets

import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-candy-machine-presets module.
 * Tests CandyMachineMintPresets and related utilities.
 */
class CandyMachinePresetsModuleTest {

    private val testWallet = Pubkey(ByteArray(32) { 1 })
    private val testMint = Pubkey(ByteArray(32) { 2 })

    // ===== CandyMachineMintPresets Tests =====

    @Test
    fun testCandyMachineMintPresetsExists() {
        assertNotNull(CandyMachineMintPresets)
    }

    // ===== MintResult Tests =====

    @Test
    fun testMintResultCreation() {
        val result = CandyMachineMintPresets.MintResult(
            signature = "abc123",
            notes = listOf("Minted successfully"),
            warnings = emptyList(),
            mintedMint = testMint
        )
        
        assertEquals("abc123", result.signature)
        assertEquals(1, result.notes.size)
        assertTrue(result.warnings.isEmpty())
        assertEquals(testMint, result.mintedMint)
    }

    @Test
    fun testMintResultWithWarnings() {
        val result = CandyMachineMintPresets.MintResult(
            signature = "sig123",
            notes = emptyList(),
            warnings = listOf("Guard condition met", "Priority fee applied")
        )
        
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun testMintResultDefaults() {
        val result = CandyMachineMintPresets.MintResult(signature = "test")
        
        assertEquals("test", result.signature)
        assertTrue(result.notes.isEmpty())
        assertTrue(result.warnings.isEmpty())
        assertEquals(null, result.mintedMint)
    }

    // ===== NewMintSeed Tests =====

    @Test
    fun testNewMintSeedCreation() {
        val seedResult = CandyMachineMintPresets.NewMintSeed(
            seed = "mint-seed-123",
            mint = testMint
        )
        
        assertEquals("mint-seed-123", seedResult.seed)
        assertEquals(testMint, seedResult.mint)
    }

    // ===== deriveNewMintWithSeed Tests =====

    @Test
    fun testDeriveNewMintWithSeed() {
        val result = CandyMachineMintPresets.deriveNewMintWithSeed(
            wallet = testWallet,
            seed = "test-seed",
            tokenProgram = ProgramIds.TOKEN_PROGRAM
        )
        
        assertNotNull(result)
        assertEquals("test-seed", result.seed)
        assertNotNull(result.mint)
        assertEquals(32, result.mint.bytes.size)
    }

    @Test
    fun testDeriveNewMintWithSeedDeterministic() {
        val result1 = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, "seed1")
        val result2 = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, "seed1")
        
        assertEquals(result1.mint, result2.mint)
    }

    @Test
    fun testDeriveNewMintWithDifferentSeeds() {
        val result1 = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, "seed-a")
        val result2 = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, "seed-b")
        
        assertTrue(!result1.mint.bytes.contentEquals(result2.mint.bytes))
    }

    @Test
    fun testDeriveNewMintWithToken2022() {
        val result = CandyMachineMintPresets.deriveNewMintWithSeed(
            wallet = testWallet,
            seed = "t22-mint",
            tokenProgram = ProgramIds.TOKEN_2022_PROGRAM
        )
        
        assertNotNull(result.mint)
    }

    // ===== CandyMachinePresetDescriptors Tests =====

    @Test
    fun testPresetDescriptorsExists() {
        assertNotNull(CandyMachinePresetDescriptors)
    }

    // ===== Seed Generation Patterns =====

    @Test
    fun testSeedWithTimestamp() {
        val timestamp = System.currentTimeMillis()
        val seed = "mint-$timestamp"
        
        val result = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, seed)
        
        assertNotNull(result)
        assertTrue(result.seed.startsWith("mint-"))
    }

    @Test
    fun testSeedWithUUID() {
        val uuid = java.util.UUID.randomUUID().toString().take(8)
        val seed = "nft-$uuid"
        
        val result = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, seed)
        
        assertNotNull(result)
    }

    @Test
    fun testSeedLengthLimit() {
        // Seeds must be <= 32 bytes
        val shortSeed = "a".repeat(32)
        val result = CandyMachineMintPresets.deriveNewMintWithSeed(testWallet, shortSeed)
        
        assertNotNull(result)
    }
}
