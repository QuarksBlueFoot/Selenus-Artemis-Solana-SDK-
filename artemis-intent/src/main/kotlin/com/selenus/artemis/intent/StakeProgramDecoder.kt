/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for Stake Program instructions.
 * 
 * Decodes staking operations including delegation, deactivation,
 * and stake account management.
 */
object StakeProgramDecoder : InstructionDecoder {
    
    // Stake instruction discriminators
    private const val INITIALIZE = 0
    private const val AUTHORIZE = 1
    private const val DELEGATE_STAKE = 2
    private const val SPLIT = 3
    private const val WITHDRAW = 4
    private const val DEACTIVATE = 5
    private const val SET_LOCKUP = 6
    private const val MERGE = 7
    private const val AUTHORIZE_WITH_SEED = 8
    private const val INITIALIZE_CHECKED = 9
    private const val AUTHORIZE_CHECKED = 10
    private const val AUTHORIZE_CHECKED_WITH_SEED = 11
    private const val SET_LOCKUP_CHECKED = 12
    private const val REDELEGATE = 13
    
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
            INITIALIZE -> decodeInitialize(accounts, data, instructionIndex)
            AUTHORIZE -> decodeAuthorize(accounts, data, instructionIndex)
            DELEGATE_STAKE -> decodeDelegateStake(accounts, instructionIndex)
            SPLIT -> decodeSplit(accounts, data, instructionIndex)
            WITHDRAW -> decodeWithdraw(accounts, data, instructionIndex)
            DEACTIVATE -> decodeDeactivate(accounts, instructionIndex)
            SET_LOCKUP -> decodeSetLockup(accounts, data, instructionIndex)
            MERGE -> decodeMerge(accounts, instructionIndex)
            REDELEGATE -> decodeRedelegate(accounts, instructionIndex)
            else -> createUnknownIntent(discriminator, instructionIndex)
        }
    }
    
    private fun decodeInitialize(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        
        // Authorized (staker, withdrawer) is embedded in instruction data
        // Format: staker (32 bytes), withdrawer (32 bytes)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "initialize",
            summary = "Initialize stake account",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole("SysvarRent111111111111111111111111111111111", "Rent Sysvar", false, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeAuthorize(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val stakeOrWithdrawAuthority = accounts.getOrNull(2) ?: "unknown"
        
        // Authorization type at offset 36 (after 32-byte new authority pubkey + 4-byte discriminator)
        val authType = if (data.size >= 37) data[36].toInt() and 0xFF else 0
        val authTypeName = when (authType) {
            0 -> "Staker"
            1 -> "Withdrawer"
            else -> "Unknown($authType)"
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "authorize",
            summary = "Change $authTypeName authority",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole(stakeOrWithdrawAuthority, "Current Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount,
                "authorityType" to authTypeName
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Stake authority will be changed - verify new authority carefully")
        )
    }
    
    private fun decodeDelegateStake(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val voteAccount = accounts.getOrNull(1) ?: "unknown"
        val stakeAuthority = accounts.getOrNull(5) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "delegateStake",
            summary = "Delegate stake to validator ${voteAccount.take(8)}...",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole(voteAccount, "Vote Account", false, false),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole("SysvarStakeHistory1111111111111111111111111", "Stake History", false, false),
                AccountRole("StakeConfig11111111111111111111111111111111", "Stake Config", false, false),
                AccountRole(stakeAuthority, "Stake Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount,
                "voteAccount" to voteAccount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf(
                "Stake will be delegated to this validator",
                "You can undelegate at any time (subject to cooldown)"
            )
        )
    }
    
    private fun decodeSplit(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val splitStakeAccount = accounts.getOrNull(1) ?: "unknown"
        val stakeAuthority = accounts.getOrNull(2) ?: "unknown"
        
        val lamports = if (data.size >= 12) {
            ByteBuffer.wrap(data.sliceArray(4..11))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val solAmount = lamports / 1_000_000_000.0
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "split",
            summary = "Split ${"%.4f".format(solAmount)} SOL into new stake account",
            accounts = listOf(
                AccountRole(stakeAccount, "Source Stake", false, true),
                AccountRole(splitStakeAccount, "New Stake Account", false, true),
                AccountRole(stakeAuthority, "Stake Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount,
                "splitStakeAccount" to splitStakeAccount,
                "lamports" to lamports,
                "solAmount" to solAmount
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeWithdraw(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val recipient = accounts.getOrNull(1) ?: "unknown"
        val withdrawAuthority = accounts.getOrNull(4) ?: "unknown"
        
        val lamports = if (data.size >= 12) {
            ByteBuffer.wrap(data.sliceArray(4..11))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val solAmount = lamports / 1_000_000_000.0
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            solAmount > 1000 -> {
                warnings.add("⚠️ Large stake withdrawal")
                RiskLevel.HIGH
            }
            solAmount > 100 -> {
                warnings.add("Significant stake withdrawal")
                RiskLevel.MEDIUM
            }
            else -> RiskLevel.LOW
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "withdraw",
            summary = "Withdraw ${"%.4f".format(solAmount)} SOL from stake",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole(recipient, "Recipient", false, true),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole("SysvarStakeHistory1111111111111111111111111", "Stake History", false, false),
                AccountRole(withdrawAuthority, "Withdraw Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount,
                "recipient" to recipient,
                "lamports" to lamports,
                "solAmount" to solAmount
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeDeactivate(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val stakeAuthority = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "deactivate",
            summary = "Deactivate stake (begin cooldown)",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole(stakeAuthority, "Stake Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf(
                "Stake will be deactivated",
                "Cooldown period required before withdrawal (usually 2-3 days)"
            )
        )
    }
    
    private fun decodeSetLockup(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val lockupAuthority = accounts.getOrNull(1) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "setLockup",
            summary = "Modify stake lockup period",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole(lockupAuthority, "Lockup Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Lockup changes can prevent withdrawal until lockup expires")
        )
    }
    
    private fun decodeMerge(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val destinationStake = accounts.getOrNull(0) ?: "unknown"
        val sourceStake = accounts.getOrNull(1) ?: "unknown"
        val stakeAuthority = accounts.getOrNull(4) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "merge",
            summary = "Merge stake accounts",
            accounts = listOf(
                AccountRole(destinationStake, "Destination Stake", false, true),
                AccountRole(sourceStake, "Source Stake", false, true),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole("SysvarStakeHistory1111111111111111111111111", "Stake History", false, false),
                AccountRole(stakeAuthority, "Stake Authority", true, false)
            ),
            args = mapOf(
                "destinationStake" to destinationStake,
                "sourceStake" to sourceStake
            ),
            riskLevel = RiskLevel.LOW,
            warnings = listOf("Source stake account will be closed after merge")
        )
    }
    
    private fun decodeRedelegate(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val stakeAccount = accounts.getOrNull(0) ?: "unknown"
        val newVoteAccount = accounts.getOrNull(1) ?: "unknown"
        val stakeAuthority = accounts.getOrNull(4) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "redelegate",
            summary = "Redelegate to validator ${newVoteAccount.take(8)}...",
            accounts = listOf(
                AccountRole(stakeAccount, "Stake Account", false, true),
                AccountRole(newVoteAccount, "New Vote Account", false, false),
                AccountRole("SysvarClock11111111111111111111111111111111", "Clock Sysvar", false, false),
                AccountRole("SysvarStakeHistory1111111111111111111111111", "Stake History", false, false),
                AccountRole(stakeAuthority, "Stake Authority", true, false)
            ),
            args = mapOf(
                "stakeAccount" to stakeAccount,
                "newVoteAccount" to newVoteAccount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf(
                "Stake will be moved to a new validator",
                "May affect staking rewards during transition"
            )
        )
    }
    
    private fun createUnknownIntent(
        discriminator: Int,
        instructionIndex: Int
    ): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Stake",
            programId = ProgramRegistry.STAKE_PROGRAM,
            method = "unknown($discriminator)",
            summary = "Unknown stake instruction",
            accounts = emptyList(),
            args = mapOf("discriminator" to discriminator),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
