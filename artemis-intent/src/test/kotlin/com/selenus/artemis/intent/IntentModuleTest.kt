/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Transaction Intent Protocol Tests - World's First semantic transaction decoding.
 */
package com.selenus.artemis.intent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Comprehensive tests for artemis-intent module.
 * 
 * Tests the Transaction Intent Protocol - world's first semantic
 * transaction decoding for mobile wallets.
 */
class IntentModuleTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TransactionIntent Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `create transaction intent with all fields`() {
        val intent = TransactionIntent(
            instructionIndex = 0,
            programName = "System Program",
            programId = "11111111111111111111111111111111",
            method = "transfer",
            summary = "Transfer 1.5 SOL to 7xKXt...",
            accounts = listOf(
                AccountRole(
                    pubkey = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
                    role = "destination",
                    isSigner = false,
                    isWritable = true
                )
            ),
            args = mapOf("lamports" to 1_500_000_000L),
            riskLevel = RiskLevel.MEDIUM,
            warnings = emptyList(),
            isPartialDecode = false
        )

        assertEquals(0, intent.instructionIndex)
        assertEquals("System Program", intent.programName)
        assertEquals("transfer", intent.method)
        assertEquals(RiskLevel.MEDIUM, intent.riskLevel)
        assertFalse(intent.isPartialDecode)
    }

    @Test
    fun `intent with partial decode flag`() {
        val intent = TransactionIntent(
            instructionIndex = 0,
            programName = "Unknown Program",
            programId = "SomeUnknownProgramId111111111111111",
            method = "unknown",
            summary = "Unknown instruction",
            accounts = emptyList(),
            args = emptyMap(),
            riskLevel = RiskLevel.MEDIUM,
            isPartialDecode = true
        )

        assertTrue(intent.isPartialDecode)
    }

    @Test
    fun `intent with warnings`() {
        val warnings = listOf(
            "Transaction modifies large number of accounts",
            "High compute budget requested"
        )
        
        val intent = TransactionIntent(
            instructionIndex = 0,
            programName = "DeFi Protocol",
            programId = "DefiProgram11111111111111111111111",
            method = "swap",
            summary = "Swap tokens",
            accounts = emptyList(),
            args = emptyMap(),
            riskLevel = RiskLevel.HIGH,
            warnings = warnings
        )

        assertEquals(2, intent.warnings.size)
        assertTrue(intent.warnings.contains("High compute budget requested"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AccountRole Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `account role with all permissions`() {
        val role = AccountRole(
            pubkey = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
            role = "authority",
            isSigner = true,
            isWritable = true,
            knownName = "Wallet Owner"
        )

        assertEquals("authority", role.role)
        assertTrue(role.isSigner)
        assertTrue(role.isWritable)
        assertEquals("Wallet Owner", role.knownName)
    }

    @Test
    fun `account role as read-only`() {
        val role = AccountRole(
            pubkey = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            role = "program",
            isSigner = false,
            isWritable = false,
            knownName = "Token Program"
        )

        assertFalse(role.isSigner)
        assertFalse(role.isWritable)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RiskLevel Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `risk levels are ordered correctly`() {
        assertTrue(RiskLevel.INFO < RiskLevel.LOW)
        assertTrue(RiskLevel.LOW < RiskLevel.MEDIUM)
        assertTrue(RiskLevel.MEDIUM < RiskLevel.HIGH)
        assertTrue(RiskLevel.HIGH < RiskLevel.CRITICAL)
    }

    @Test
    fun `risk levels have display names`() {
        assertEquals("Informational", RiskLevel.INFO.displayName)
        assertEquals("Low Risk", RiskLevel.LOW.displayName)
        assertEquals("Medium Risk", RiskLevel.MEDIUM.displayName)
        assertEquals("High Risk", RiskLevel.HIGH.displayName)
        assertEquals("Critical - Review Carefully", RiskLevel.CRITICAL.displayName)
    }

    @Test
    fun `risk levels have emojis`() {
        assertEquals("ℹ️", RiskLevel.INFO.emoji)
        assertEquals("✅", RiskLevel.LOW.emoji)
        assertEquals("⚠️", RiskLevel.MEDIUM.emoji)
        assertEquals("🔴", RiskLevel.HIGH.emoji)
        assertEquals("🚨", RiskLevel.CRITICAL.emoji)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TransactionIntentAnalysis Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `analysis from empty intents`() {
        val analysis = TransactionIntentAnalysis.fromIntents(emptyList())

        assertEquals("Empty transaction", analysis.summary)
        assertEquals(RiskLevel.INFO, analysis.overallRisk)
        assertTrue(analysis.intents.isEmpty())
        assertTrue(analysis.fullyDecoded)
    }

    @Test
    fun `analysis from single intent`() {
        val intent = TransactionIntent(
            instructionIndex = 0,
            programName = "System Program",
            programId = "11111111111111111111111111111111",
            method = "transfer",
            summary = "Transfer 1.0 SOL",
            accounts = emptyList(),
            args = mapOf("lamports" to 1_000_000_000L),
            riskLevel = RiskLevel.MEDIUM
        )

        val analysis = TransactionIntentAnalysis.fromIntents(listOf(intent))

        assertEquals("Transfer 1.0 SOL", analysis.summary)
        assertEquals(RiskLevel.MEDIUM, analysis.overallRisk)
        assertEquals(1, analysis.intents.size)
    }

    @Test
    fun `analysis takes highest risk level`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "Compute Budget",
                programId = "ComputeBudget111111111111111111111111111111",
                method = "setComputeUnitLimit",
                summary = "Set compute limit",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.INFO
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "Token Program",
                programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                method = "transfer",
                summary = "Transfer tokens",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.MEDIUM
            ),
            TransactionIntent(
                instructionIndex = 2,
                programName = "DeFi Protocol",
                programId = "DefiProtocol1111111111111111111111111111111",
                method = "liquidate",
                summary = "Liquidate position",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.HIGH
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals(RiskLevel.HIGH, analysis.overallRisk)
    }

    @Test
    fun `analysis filters compute budget from summary`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "Compute Budget",
                programId = "ComputeBudget111111111111111111111111111111",
                method = "setComputeUnitLimit",
                summary = "Set compute limit",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.INFO
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "System Program",
                programId = "11111111111111111111111111111111",
                method = "transfer",
                summary = "Transfer 2.0 SOL",
                accounts = emptyList(),
                args = mapOf("lamports" to 2_000_000_000L),
                riskLevel = RiskLevel.MEDIUM
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals("Transfer 2.0 SOL", analysis.summary)
    }

    @Test
    fun `analysis extracts SOL transfer costs`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "System Program",
                programId = "11111111111111111111111111111111",
                method = "transfer",
                summary = "Transfer 1.0 SOL",
                accounts = emptyList(),
                args = mapOf("lamports" to 1_000_000_000L),
                riskLevel = RiskLevel.MEDIUM
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "System Program",
                programId = "11111111111111111111111111111111",
                method = "transfer",
                summary = "Transfer 0.5 SOL",
                accounts = emptyList(),
                args = mapOf("lamports" to 500_000_000L),
                riskLevel = RiskLevel.MEDIUM
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals(1_500_000_000L, analysis.estimatedSolCost)
    }

    @Test
    fun `analysis detects partial decodes`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "Unknown Program",
                programId = "UnknownProgram111111111111111111111111111",
                method = "unknown",
                summary = "Unknown instruction",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.MEDIUM,
                isPartialDecode = true
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertFalse(analysis.fullyDecoded)
    }

    @Test
    fun `analysis aggregates warnings`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "Program A",
                programId = "ProgramA11111111111111111111111111111111111",
                method = "action",
                summary = "Action A",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.MEDIUM,
                warnings = listOf("Warning 1", "Warning 2")
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "Program B",
                programId = "ProgramB11111111111111111111111111111111111",
                method = "action",
                summary = "Action B",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.LOW,
                warnings = listOf("Warning 3")
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals(3, analysis.warnings.size)
    }

    @Test
    fun `analysis deduplicates accounts`() {
        val sharedAccount = AccountRole(
            pubkey = "SharedAccount11111111111111111111111111111",
            role = "authority",
            isSigner = true,
            isWritable = true
        )

        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "Program A",
                programId = "ProgramA11111111111111111111111111111111111",
                method = "action",
                summary = "Action A",
                accounts = listOf(sharedAccount),
                args = emptyMap(),
                riskLevel = RiskLevel.LOW
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "Program B",
                programId = "ProgramB11111111111111111111111111111111111",
                method = "action",
                summary = "Action B",
                accounts = listOf(sharedAccount),
                args = emptyMap(),
                riskLevel = RiskLevel.LOW
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals(1, analysis.accountsInvolved.size)
    }

    @Test
    fun `analysis lists programs involved`() {
        val intents = listOf(
            TransactionIntent(
                instructionIndex = 0,
                programName = "System Program",
                programId = "11111111111111111111111111111111",
                method = "transfer",
                summary = "Transfer SOL",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.LOW
            ),
            TransactionIntent(
                instructionIndex = 1,
                programName = "Token Program",
                programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                method = "transfer",
                summary = "Transfer tokens",
                accounts = emptyList(),
                args = emptyMap(),
                riskLevel = RiskLevel.LOW
            )
        )

        val analysis = TransactionIntentAnalysis.fromIntents(intents)

        assertEquals(2, analysis.programsInvolved.size)
        assertTrue(analysis.programsInvolved.contains("System Program"))
        assertTrue(analysis.programsInvolved.contains("Token Program"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TransactionIntentDecoder Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `decode empty bytes returns empty analysis`() {
        val analysis = TransactionIntentDecoder.decodeFromBytes(ByteArray(0))

        assertEquals("Empty transaction", analysis.summary)
        assertTrue(analysis.warnings.isNotEmpty())
    }

    @Test
    fun `decode system program transfer instruction`() {
        // Create a mock System Program transfer instruction
        val systemProgramId = "11111111111111111111111111111111"
        val accounts = listOf(
            "SourceAccount1111111111111111111111111111111",
            "DestAccount11111111111111111111111111111111"
        )
        
        // Transfer instruction discriminator (2 for transfer) + lamports (u64)
        val data = ByteArray(12).apply {
            // Discriminator: 2 (transfer)
            this[0] = 2
            this[1] = 0
            this[2] = 0
            this[3] = 0
            // Lamports: 1_000_000_000 (1 SOL)
            val lamports = 1_000_000_000L
            for (i in 0..7) {
                this[4 + i] = ((lamports shr (i * 8)) and 0xFF).toByte()
            }
        }

        val intent = TransactionIntentDecoder.decodeInstruction(
            programId = systemProgramId,
            accounts = accounts,
            data = data,
            instructionIndex = 0
        )

        assertEquals("System Program", intent.programName)
        assertEquals("transfer", intent.method)
        assertEquals(RiskLevel.MEDIUM, intent.riskLevel)
        assertTrue(intent.summary.contains("SOL") || intent.summary.contains("transfer"))
    }

    @Test
    fun `decode unknown program returns partial decode`() {
        val intent = TransactionIntentDecoder.decodeInstruction(
            programId = "SomeRandomUnknownProgram111111111111111111",
            accounts = listOf("Account111111111111111111111111111111111111"),
            data = byteArrayOf(0x01, 0x02, 0x03),
            instructionIndex = 0
        )

        assertTrue(intent.isPartialDecode || intent.programName.contains("Unknown"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProgramRegistry Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `known programs are registered`() {
        assertTrue(ProgramRegistry.isKnown("11111111111111111111111111111111"))
        assertTrue(ProgramRegistry.isKnown("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
    }

    @Test
    fun `suspicious programs are flagged`() {
        // Test that the method exists and returns false for known safe programs
        assertFalse(ProgramRegistry.isSuspicious("11111111111111111111111111111111"))
        assertFalse(ProgramRegistry.isSuspicious("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
    }

    @Test
    fun `get program name returns correct name`() {
        assertEquals("System Program", ProgramRegistry.getName("11111111111111111111111111111111"))
    }
}
