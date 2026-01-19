package com.selenus.artemis.txpresets

import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-tx-presets module.
 * Tests TxComposerPresets and related utilities.
 */
class TxPresetsModuleTest {

    private val testPayer = Pubkey(ByteArray(32) { 1 })
    private val testOwner = Pubkey(ByteArray(32) { 2 })
    private val testMint = Pubkey(ByteArray(32) { 3 })

    // ===== TxComposerPresets Tests =====

    @Test
    fun testTxComposerPresetsExists() {
        assertNotNull(TxComposerPresets)
    }

    // ===== AtaIntent Tests =====

    @Test
    fun testAtaIntentCreation() {
        val intent = TxComposerPresets.AtaIntent(
            owner = testOwner,
            mint = testMint
        )
        
        assertEquals(testOwner, intent.owner)
        assertEquals(testMint, intent.mint)
        assertEquals(ProgramIds.TOKEN_PROGRAM, intent.tokenProgram)
    }

    @Test
    fun testAtaIntentWithToken2022() {
        val intent = TxComposerPresets.AtaIntent(
            owner = testOwner,
            mint = testMint,
            tokenProgram = ProgramIds.TOKEN_2022_PROGRAM
        )
        
        assertEquals(ProgramIds.TOKEN_2022_PROGRAM, intent.tokenProgram)
    }

    @Test
    fun testAtaIntentAddress() {
        val intent = TxComposerPresets.AtaIntent(
            owner = testOwner,
            mint = testMint
        )
        
        val ataAddress = intent.ataAddress()
        
        assertNotNull(ataAddress)
        assertEquals(32, ataAddress.bytes.size)
    }

    @Test
    fun testAtaIntentAddressDeterministic() {
        val intent = TxComposerPresets.AtaIntent(testOwner, testMint)
        
        val ata1 = intent.ataAddress()
        val ata2 = intent.ataAddress()
        
        assertEquals(ata1, ata2)
    }

    @Test
    fun testAtaIntentAddressDifferentOwners() {
        val owner1 = Pubkey(ByteArray(32) { 1 })
        val owner2 = Pubkey(ByteArray(32) { 2 })
        
        val ata1 = TxComposerPresets.AtaIntent(owner1, testMint).ataAddress()
        val ata2 = TxComposerPresets.AtaIntent(owner2, testMint).ataAddress()
        
        assertTrue(!ata1.bytes.contentEquals(ata2.bytes))
    }

    // ===== ResendConfig Tests =====

    @Test
    fun testResendConfigDefaults() {
        val config = TxComposerPresets.ResendConfig()
        
        assertEquals(2, config.maxResends)
        assertEquals(30, config.confirmMaxAttempts)
        assertEquals(500L, config.confirmSleepMs)
        assertTrue(config.skipPreflightOnResend)
    }

    @Test
    fun testResendConfigCustom() {
        val config = TxComposerPresets.ResendConfig(
            maxResends = 5,
            confirmMaxAttempts = 60,
            confirmSleepMs = 1000L,
            skipPreflightOnResend = false
        )
        
        assertEquals(5, config.maxResends)
        assertEquals(60, config.confirmMaxAttempts)
        assertEquals(1000L, config.confirmSleepMs)
        assertEquals(false, config.skipPreflightOnResend)
    }

    // ===== ComposeResult Tests =====

    @Test
    fun testComposeResultCreation() {
        val result = TxComposerPresets.ComposeResult(
            signature = "abc123",
            notes = listOf("ATA created", "Priority fee added"),
            warnings = listOf("High network congestion")
        )
        
        assertEquals("abc123", result.signature)
        assertEquals(2, result.notes.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun testComposeResultEmptyNotesAndWarnings() {
        val result = TxComposerPresets.ComposeResult(
            signature = "sig",
            notes = emptyList(),
            warnings = emptyList()
        )
        
        assertTrue(result.notes.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    // ===== TxPresetDescriptors Tests =====

    @Test
    fun testTxPresetDescriptorsExists() {
        assertNotNull(TxPresetDescriptors)
    }

    @Test
    fun testSendWithAtaAndPriorityPreset() {
        val preset = TxPresetDescriptors.SendWithAtaAndPriority
        
        assertEquals("tx.send_with_ata_and_priority.v59", preset.id)
        assertEquals("Send with ATA + priority", preset.name)
        assertTrue(preset.description.isNotEmpty())
    }

    @Test
    fun testTxPresetDescriptorsProvider() {
        val provider = TxPresetDescriptors.provider
        assertNotNull(provider)
    }

    // ===== Multiple AtaIntents Tests =====

    @Test
    fun testMultipleAtaIntents() {
        val intents = (1..5).map { i ->
            TxComposerPresets.AtaIntent(
                owner = testOwner,
                mint = Pubkey(ByteArray(32) { i.toByte() })
            )
        }
        
        assertEquals(5, intents.size)
        
        // All ATAs should be unique
        val atas = intents.map { it.ataAddress().bytes.toList() }.toSet()
        assertEquals(5, atas.size)
    }

    // ===== Token Program Compatibility =====

    @Test
    fun testAtaWithStandardToken() {
        val intent = TxComposerPresets.AtaIntent(
            owner = testOwner,
            mint = testMint,
            tokenProgram = ProgramIds.TOKEN_PROGRAM
        )
        
        assertEquals(ProgramIds.TOKEN_PROGRAM, intent.tokenProgram)
    }

    @Test
    fun testAtaWithToken2022() {
        val intent = TxComposerPresets.AtaIntent(
            owner = testOwner,
            mint = testMint,
            tokenProgram = ProgramIds.TOKEN_2022_PROGRAM
        )
        
        assertEquals(ProgramIds.TOKEN_2022_PROGRAM, intent.tokenProgram)
    }

    // ===== Retry Configuration Tests =====

    @Test
    fun testAggressiveRetryConfig() {
        val config = TxComposerPresets.ResendConfig(
            maxResends = 10,
            confirmMaxAttempts = 120,
            confirmSleepMs = 250L,
            skipPreflightOnResend = true
        )
        
        assertEquals(10, config.maxResends)
        assertTrue(config.confirmMaxAttempts > 100)
    }

    @Test
    fun testNoRetryConfig() {
        val config = TxComposerPresets.ResendConfig(
            maxResends = 0,
            confirmMaxAttempts = 1,
            confirmSleepMs = 100L,
            skipPreflightOnResend = false
        )
        
        assertEquals(0, config.maxResends)
        assertEquals(1, config.confirmMaxAttempts)
    }
}
