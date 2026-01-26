/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * System Program instruction decoder.
 * 
 * Decodes all System Program instructions into human-readable intents.
 * Reference: https://docs.solana.com/developing/runtime-facilities/programs#system-program
 */
object SystemProgramDecoder : InstructionDecoder {
    
    // System Program instruction discriminators (first 4 bytes as u32 LE)
    private const val CREATE_ACCOUNT = 0
    private const val ASSIGN = 1
    private const val TRANSFER = 2
    private const val CREATE_ACCOUNT_WITH_SEED = 3
    private const val ADVANCE_NONCE_ACCOUNT = 4
    private const val WITHDRAW_NONCE_ACCOUNT = 5
    private const val INITIALIZE_NONCE_ACCOUNT = 6
    private const val AUTHORIZE_NONCE_ACCOUNT = 7
    private const val ALLOCATE = 8
    private const val ALLOCATE_WITH_SEED = 9
    private const val ASSIGN_WITH_SEED = 10
    private const val TRANSFER_WITH_SEED = 11
    private const val UPGRADE_NONCE_ACCOUNT = 12
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (data.size < 4) return null
        
        val discriminator = ByteBuffer.wrap(data.sliceArray(0..3))
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
        
        return when (discriminator) {
            CREATE_ACCOUNT -> decodeCreateAccount(accounts, data, instructionIndex)
            ASSIGN -> decodeAssign(accounts, data, instructionIndex)
            TRANSFER -> decodeTransfer(accounts, data, instructionIndex)
            CREATE_ACCOUNT_WITH_SEED -> decodeCreateAccountWithSeed(accounts, data, instructionIndex)
            ADVANCE_NONCE_ACCOUNT -> decodeAdvanceNonce(accounts, instructionIndex)
            WITHDRAW_NONCE_ACCOUNT -> decodeWithdrawNonce(accounts, data, instructionIndex)
            INITIALIZE_NONCE_ACCOUNT -> decodeInitializeNonce(accounts, instructionIndex)
            AUTHORIZE_NONCE_ACCOUNT -> decodeAuthorizeNonce(accounts, data, instructionIndex)
            ALLOCATE -> decodeAllocate(accounts, data, instructionIndex)
            else -> createUnknownIntent(discriminator, instructionIndex)
        }
    }
    
    private fun decodeTransfer(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 2 || data.size < 12) return null
        
        val lamports = ByteBuffer.wrap(data.sliceArray(4..11))
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        
        val from = accounts[0]
        val to = accounts[1]
        val solAmount = lamports.toDouble() / 1_000_000_000.0
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            solAmount > 100 -> {
                warnings.add("⚠️ Large SOL transfer")
                RiskLevel.HIGH
            }
            solAmount > 10 -> {
                warnings.add("Significant SOL transfer")
                RiskLevel.MEDIUM
            }
            else -> RiskLevel.LOW
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "transfer",
            summary = "Transfer ${"%.4f".format(solAmount)} SOL to ${to.take(8)}...",
            accounts = listOf(
                AccountRole(from, "Source", true, true),
                AccountRole(to, "Destination", false, true)
            ),
            args = mapOf(
                "source" to from,
                "destination" to to,
                "lamports" to lamports,
                "solAmount" to solAmount
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeCreateAccount(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 2 || data.size < 52) return null
        
        val lamports = ByteBuffer.wrap(data.sliceArray(4..11))
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        
        val space = ByteBuffer.wrap(data.sliceArray(12..19))
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        
        val ownerBytes = data.sliceArray(20..51)
        val owner = com.selenus.artemis.runtime.Base58.encode(ownerBytes)
        
        val payer = accounts[0]
        val newAccount = accounts[1]
        val solAmount = lamports.toDouble() / 1_000_000_000.0
        
        val ownerName = ProgramRegistry.getProgramName(owner)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "createAccount",
            summary = "Create account owned by $ownerName",
            accounts = listOf(
                AccountRole(payer, "Payer", true, true),
                AccountRole(newAccount, "New Account", true, true)
            ),
            args = mapOf(
                "payer" to payer,
                "newAccount" to newAccount,
                "lamports" to lamports,
                "space" to space,
                "owner" to owner,
                "ownerName" to ownerName
            ),
            riskLevel = RiskLevel.LOW,
            warnings = listOf("Creating new account with ${"%.4f".format(solAmount)} SOL rent deposit")
        )
    }
    
    private fun decodeAssign(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.isEmpty() || data.size < 36) return null
        
        val ownerBytes = data.sliceArray(4..35)
        val newOwner = com.selenus.artemis.runtime.Base58.encode(ownerBytes)
        val account = accounts[0]
        
        val ownerName = ProgramRegistry.getProgramName(newOwner)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "assign",
            summary = "Assign account ownership to $ownerName",
            accounts = listOf(
                AccountRole(account, "Account", true, true)
            ),
            args = mapOf(
                "account" to account,
                "newOwner" to newOwner,
                "ownerName" to ownerName
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Account ownership will be transferred")
        )
    }
    
    private fun decodeCreateAccountWithSeed(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 2) return null
        
        val payer = accounts[0]
        val newAccount = accounts[1]
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "createAccountWithSeed",
            summary = "Create derived account from seed",
            accounts = listOf(
                AccountRole(payer, "Payer", true, true),
                AccountRole(newAccount, "New Account", false, true)
            ),
            args = mapOf(
                "payer" to payer,
                "newAccount" to newAccount
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeAdvanceNonce(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 3) return null
        
        val nonceAccount = accounts[0]
        val nonceAuthority = accounts[2]
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "advanceNonceAccount",
            summary = "Advance durable transaction nonce",
            accounts = listOf(
                AccountRole(nonceAccount, "Nonce Account", false, true),
                AccountRole("SysvarRecentBlockhashes11111111111111111111", "RecentBlockhashes Sysvar", false, false),
                AccountRole(nonceAuthority, "Nonce Authority", true, false)
            ),
            args = mapOf(
                "nonceAccount" to nonceAccount,
                "authority" to nonceAuthority
            ),
            riskLevel = RiskLevel.INFO,
            warnings = listOf("Used for durable transactions")
        )
    }
    
    private fun decodeWithdrawNonce(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 5 || data.size < 12) return null
        
        val lamports = ByteBuffer.wrap(data.sliceArray(4..11))
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        
        val nonceAccount = accounts[0]
        val destination = accounts[1]
        val nonceAuthority = accounts[4]
        val solAmount = lamports.toDouble() / 1_000_000_000.0
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "withdrawNonceAccount",
            summary = "Withdraw ${"%.4f".format(solAmount)} SOL from nonce account",
            accounts = listOf(
                AccountRole(nonceAccount, "Nonce Account", false, true),
                AccountRole(destination, "Destination", false, true),
                AccountRole("SysvarRecentBlockhashes11111111111111111111", "RecentBlockhashes Sysvar", false, false),
                AccountRole("SysvarRent111111111111111111111111111111111", "Rent Sysvar", false, false),
                AccountRole(nonceAuthority, "Nonce Authority", true, false)
            ),
            args = mapOf(
                "nonceAccount" to nonceAccount,
                "destination" to destination,
                "lamports" to lamports
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = emptyList()
        )
    }
    
    private fun decodeInitializeNonce(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 3) return null
        
        val nonceAccount = accounts[0]
        val nonceAuthority = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "initializeNonceAccount",
            summary = "Initialize durable nonce account",
            accounts = listOf(
                AccountRole(nonceAccount, "Nonce Account", false, true),
                AccountRole("SysvarRecentBlockhashes11111111111111111111", "RecentBlockhashes Sysvar", false, false),
                AccountRole("SysvarRent111111111111111111111111111111111", "Rent Sysvar", false, false)
            ),
            args = mapOf(
                "nonceAccount" to nonceAccount,
                "authority" to nonceAuthority
            ),
            riskLevel = RiskLevel.LOW,
            warnings = listOf("Creates account for durable transactions")
        )
    }
    
    private fun decodeAuthorizeNonce(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.size < 2 || data.size < 36) return null
        
        val newAuthorityBytes = data.sliceArray(4..35)
        val newAuthority = com.selenus.artemis.runtime.Base58.encode(newAuthorityBytes)
        
        val nonceAccount = accounts[0]
        val currentAuthority = accounts[1]
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "authorizeNonceAccount",
            summary = "Change nonce authority to ${newAuthority.take(8)}...",
            accounts = listOf(
                AccountRole(nonceAccount, "Nonce Account", false, true),
                AccountRole(currentAuthority, "Current Authority", true, false)
            ),
            args = mapOf(
                "nonceAccount" to nonceAccount,
                "currentAuthority" to currentAuthority,
                "newAuthority" to newAuthority
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Nonce authority will be transferred - verify carefully")
        )
    }
    
    private fun decodeAllocate(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (accounts.isEmpty() || data.size < 12) return null
        
        val space = ByteBuffer.wrap(data.sliceArray(4..11))
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        
        val account = accounts[0]
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "allocate",
            summary = "Allocate $space bytes for account",
            accounts = listOf(
                AccountRole(account, "Account", true, true)
            ),
            args = mapOf(
                "account" to account,
                "space" to space
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun createUnknownIntent(
        discriminator: Int,
        instructionIndex: Int
    ): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "System Program",
            programId = ProgramRegistry.SYSTEM_PROGRAM,
            method = "unknown($discriminator)",
            summary = "Unknown System Program instruction",
            accounts = emptyList(),
            args = mapOf("discriminator" to discriminator),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
