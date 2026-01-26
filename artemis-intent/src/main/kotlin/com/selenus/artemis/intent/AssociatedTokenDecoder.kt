/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

/**
 * Decoder for Associated Token Account Program instructions.
 * 
 * The ATA program only has a few instructions for creating and
 * recovering associated token accounts.
 */
object AssociatedTokenDecoder : InstructionDecoder {
    
    // ATA instruction discriminators
    private const val CREATE = 0
    private const val CREATE_IDEMPOTENT = 1
    private const val RECOVER_NESTED = 2
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        // ATA program can have empty data (create) or single byte discriminator
        val discriminator = if (data.isEmpty()) CREATE else data[0].toInt() and 0xFF
        
        return when (discriminator) {
            CREATE -> decodeCreate(accounts, instructionIndex, idempotent = false)
            CREATE_IDEMPOTENT -> decodeCreate(accounts, instructionIndex, idempotent = true)
            RECOVER_NESTED -> decodeRecoverNested(accounts, instructionIndex)
            else -> createUnknownIntent(discriminator, instructionIndex)
        }
    }
    
    private fun decodeCreate(
        accounts: List<String>,
        instructionIndex: Int,
        idempotent: Boolean
    ): TransactionIntent {
        val payer = accounts.getOrNull(0) ?: "unknown"
        val associatedTokenAccount = accounts.getOrNull(1) ?: "unknown"
        val walletAddress = accounts.getOrNull(2) ?: "unknown"
        val splTokenMint = accounts.getOrNull(3) ?: "unknown"
        val systemProgram = accounts.getOrNull(4) ?: "11111111111111111111111111111111"
        val tokenProgram = accounts.getOrNull(5) ?: ProgramRegistry.TOKEN_PROGRAM
        
        val isToken2022 = tokenProgram == ProgramRegistry.TOKEN_2022_PROGRAM
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Associated Token Account",
            programId = ProgramRegistry.ASSOCIATED_TOKEN_PROGRAM,
            method = if (idempotent) "createIdempotent" else "create",
            summary = "Create token account for ${walletAddress.take(8)}...",
            accounts = listOf(
                AccountRole(payer, "Payer", true, true),
                AccountRole(associatedTokenAccount, "Token Account (ATA)", false, true),
                AccountRole(walletAddress, "Wallet Owner", false, false),
                AccountRole(splTokenMint, "Token Mint", false, false),
                AccountRole(systemProgram, "System Program", false, false),
                AccountRole(tokenProgram, if (isToken2022) "Token-2022 Program" else "Token Program", false, false)
            ),
            args = mapOf(
                "payer" to payer,
                "associatedTokenAccount" to associatedTokenAccount,
                "wallet" to walletAddress,
                "mint" to splTokenMint,
                "isIdempotent" to idempotent,
                "isToken2022" to isToken2022
            ),
            riskLevel = RiskLevel.LOW,
            warnings = if (idempotent) {
                emptyList()
            } else {
                listOf("Will fail if account already exists - use createIdempotent for safety")
            }
        )
    }
    
    private fun decodeRecoverNested(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val nestedAccount = accounts.getOrNull(0) ?: "unknown"
        val nestedMint = accounts.getOrNull(1) ?: "unknown"
        val destinationAccount = accounts.getOrNull(2) ?: "unknown"
        val ownerAccount = accounts.getOrNull(3) ?: "unknown"
        val ownerMint = accounts.getOrNull(4) ?: "unknown"
        val walletAddress = accounts.getOrNull(5) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Associated Token Account",
            programId = ProgramRegistry.ASSOCIATED_TOKEN_PROGRAM,
            method = "recoverNested",
            summary = "Recover tokens from nested ATA",
            accounts = listOf(
                AccountRole(nestedAccount, "Nested Token Account", false, true),
                AccountRole(nestedMint, "Nested Token Mint", false, false),
                AccountRole(destinationAccount, "Destination ATA", false, true),
                AccountRole(ownerAccount, "Owner Token Account", false, false),
                AccountRole(ownerMint, "Owner Token Mint", false, false),
                AccountRole(walletAddress, "Wallet", true, false)
            ),
            args = mapOf(
                "nestedAccount" to nestedAccount,
                "destinationAccount" to destinationAccount,
                "wallet" to walletAddress
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("Recovering tokens from incorrectly nested token account")
        )
    }
    
    private fun createUnknownIntent(
        discriminator: Int,
        instructionIndex: Int
    ): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Associated Token Account",
            programId = ProgramRegistry.ASSOCIATED_TOKEN_PROGRAM,
            method = "unknown($discriminator)",
            summary = "Unknown ATA instruction",
            accounts = emptyList(),
            args = mapOf("discriminator" to discriminator),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
