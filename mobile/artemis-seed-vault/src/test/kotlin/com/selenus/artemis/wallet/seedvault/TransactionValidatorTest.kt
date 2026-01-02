/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.seedvault

import com.selenus.artemis.seedvault.TransactionValidator
import com.selenus.artemis.seedvault.TransactionCategory
import com.selenus.artemis.seedvault.DangerReason
import com.selenus.artemis.runtime.Pubkey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class TransactionValidatorTest {
    
    private lateinit var validator: TransactionValidator
    
    @BeforeEach
    fun setup() {
        validator = TransactionValidator()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Test Transaction Data - Real Solana transaction patterns
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Well-known program IDs
    private val SYSTEM_PROGRAM = "11111111111111111111111111111111"
    private val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    private val COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111"
    private val MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Safe Transaction Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `simple SOL transfer is safe`() {
        // A basic SOL transfer using System Program
        val programs = listOf(SYSTEM_PROGRAM)
        
        val result = validator.validatePrograms(programs)
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name == "System Program" })
        assertEquals(0, result.dangerReasons.size)
    }
    
    @Test
    fun `SPL token transfer is safe`() {
        val programs = listOf(TOKEN_PROGRAM, ASSOCIATED_TOKEN_PROGRAM)
        
        val result = validator.validatePrograms(programs)
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name == "Token Program" })
    }
    
    @Test
    fun `transaction with memo is safe`() {
        val programs = listOf(SYSTEM_PROGRAM, MEMO_PROGRAM)
        
        val result = validator.validatePrograms(programs)
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name == "Memo Program" })
    }
    
    @Test
    fun `compute budget with transfer is safe`() {
        val programs = listOf(COMPUTE_BUDGET_PROGRAM, SYSTEM_PROGRAM)
        
        val result = validator.validatePrograms(programs)
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name == "Compute Budget" })
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Dangerous Transaction Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `unknown program triggers warning`() {
        val unknownProgram = "UnknownXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        val programs = listOf(unknownProgram)
        
        val result = validator.validatePrograms(programs)
        
        assertFalse(result.isSafe)
        assertTrue(result.programs.any { !it.isKnown })
        assertTrue(result.dangerReasons.any { it == DangerReason.UNKNOWN_PROGRAM })
    }
    
    @Test
    fun `multiple unknown programs are flagged`() {
        val programs = listOf(
            "Unknown1XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            "Unknown2XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            "Unknown3XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        )
        
        val result = validator.validatePrograms(programs)
        
        assertFalse(result.isSafe)
        assertEquals(3, result.programs.count { !it.isKnown })
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Known Program Detection Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect Metaplex Token Metadata program`() {
        val metaplexTM = "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s"
        
        val result = validator.validatePrograms(listOf(metaplexTM))
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name.contains("Metaplex") })
    }
    
    @Test
    fun `detect Jupiter program`() {
        val jupiter = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"
        
        val result = validator.validatePrograms(listOf(jupiter))
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name.contains("Jupiter") })
    }
    
    @Test
    fun `detect Raydium programs`() {
        val raydiumAmm = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8"
        
        val result = validator.validatePrograms(listOf(raydiumAmm))
        
        assertTrue(result.isSafe)
        assertTrue(result.programs.any { it.name.contains("Raydium") })
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Transaction Summary Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `categorize SOL transfer`() {
        val programs = listOf(SYSTEM_PROGRAM)
        
        val summary = validator.summarizeTransaction(programs)
        
        assertEquals(TransactionCategory.TRANSFER, summary.category)
        assertFalse(summary.requiresExtraScrutiny)
    }
    
    @Test
    fun `categorize token swap`() {
        val programs = listOf(
            "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
            TOKEN_PROGRAM,
            COMPUTE_BUDGET_PROGRAM
        )
        
        val summary = validator.summarizeTransaction(programs)
        
        assertEquals(TransactionCategory.SWAP, summary.category)
    }
    
    @Test
    fun `categorize NFT mint`() {
        val programs = listOf(
            "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s",
            TOKEN_PROGRAM,
            SYSTEM_PROGRAM
        )
        
        val summary = validator.summarizeTransaction(programs)
        
        assertEquals(TransactionCategory.NFT, summary.category)
    }
    
    @Test
    fun `flag high-risk DeFi transactions`() {
        val programs = listOf(
            "UnknownDeFi1XXXXXXXXXXXXXXXXXXXXXXXXXX",
            TOKEN_PROGRAM,
            ASSOCIATED_TOKEN_PROGRAM
        )
        
        val summary = validator.summarizeTransaction(programs)
        
        assertTrue(summary.requiresExtraScrutiny)
        assertTrue(summary.warnings.any { it.contains("unknown") })
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Scam Pattern Detection Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect excessive compute budget as potential attack`() {
        // Some attacks request abnormally high compute
        val highComputeRequest = validator.analyzeComputeBudget(
            computeUnits = 2_000_000, // Max allowed
            priorityFee = 0
        )
        
        assertTrue(highComputeRequest.isHighRisk)
    }
    
    @Test
    fun `normal compute budget is fine`() {
        val normalCompute = validator.analyzeComputeBudget(
            computeUnits = 200_000,
            priorityFee = 1000
        )
        
        assertFalse(normalCompute.isHighRisk)
    }
    
    @Test
    fun `detect drainer pattern - token approval to unknown`() {
        val drainerCheck = validator.checkDrainerPattern(
            programId = TOKEN_PROGRAM,
            instruction = "approve", // Instruction name
            delegateAddress = "ScammerXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            amount = Long.MAX_VALUE // Max approval = red flag
        )
        
        assertTrue(drainerCheck.isPotentialDrainer)
        assertTrue(drainerCheck.reasons.any { it.contains("unlimited") || it.contains("approval") })
    }
    
    @Test
    fun `normal token approval is fine`() {
        val normalApproval = validator.checkDrainerPattern(
            programId = TOKEN_PROGRAM,
            instruction = "approve",
            delegateAddress = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4", // Jupiter is known
            amount = 1_000_000 // Reasonable amount
        )
        
        assertFalse(normalApproval.isPotentialDrainer)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `empty program list is invalid`() {
        val result = validator.validatePrograms(emptyList())
        
        assertFalse(result.isSafe)
        assertTrue(result.dangerReasons.any { it == DangerReason.EMPTY_TRANSACTION })
    }
    
    @Test
    fun `many programs triggers extra scrutiny`() {
        // Transactions with too many programs are suspicious
        val manyPrograms = List(15) { SYSTEM_PROGRAM }
        
        val summary = validator.summarizeTransaction(manyPrograms)
        
        assertTrue(summary.requiresExtraScrutiny)
    }
    
    @Test
    fun `validate real Jupiter swap programs`() {
        // Typical Jupiter swap involves multiple programs
        val jupiterSwap = listOf(
            COMPUTE_BUDGET_PROGRAM,
            "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
            TOKEN_PROGRAM,
            "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc", // Orca Whirlpool
            ASSOCIATED_TOKEN_PROGRAM
        )
        
        val result = validator.validatePrograms(jupiterSwap)
        
        assertTrue(result.isSafe)
        
        val summary = validator.summarizeTransaction(jupiterSwap)
        assertEquals(TransactionCategory.SWAP, summary.category)
    }
}
