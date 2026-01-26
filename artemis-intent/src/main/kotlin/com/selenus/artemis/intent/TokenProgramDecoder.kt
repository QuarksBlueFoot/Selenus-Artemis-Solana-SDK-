/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for SPL Token Program instructions.
 * 
 * Decodes all standard SPL Token instructions into human-readable
 * intents with risk assessment.
 */
object TokenProgramDecoder : InstructionDecoder {
    
    // SPL Token instruction discriminators (single byte)
    private const val INITIALIZE_MINT = 0
    private const val INITIALIZE_ACCOUNT = 1
    private const val INITIALIZE_MULTISIG = 2
    private const val TRANSFER = 3
    private const val APPROVE = 4
    private const val REVOKE = 5
    private const val SET_AUTHORITY = 6
    private const val MINT_TO = 7
    private const val BURN = 8
    private const val CLOSE_ACCOUNT = 9
    private const val FREEZE_ACCOUNT = 10
    private const val THAW_ACCOUNT = 11
    private const val TRANSFER_CHECKED = 12
    private const val APPROVE_CHECKED = 13
    private const val MINT_TO_CHECKED = 14
    private const val BURN_CHECKED = 15
    private const val INITIALIZE_ACCOUNT_2 = 16
    private const val SYNC_NATIVE = 17
    private const val INITIALIZE_ACCOUNT_3 = 18
    private const val INITIALIZE_MULTISIG_2 = 19
    private const val INITIALIZE_MINT_2 = 20
    private const val GET_ACCOUNT_DATA_SIZE = 21
    private const val INITIALIZE_IMMUTABLE_OWNER = 22
    private const val AMOUNT_TO_UI_AMOUNT = 23
    private const val UI_AMOUNT_TO_AMOUNT = 24
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (data.isEmpty()) return null
        
        val discriminator = data[0].toInt() and 0xFF
        
        return when (discriminator) {
            INITIALIZE_MINT -> decodeInitializeMint(accounts, data, instructionIndex)
            INITIALIZE_ACCOUNT -> decodeInitializeAccount(accounts, data, instructionIndex)
            INITIALIZE_MULTISIG -> decodeInitializeMultisig(accounts, data, instructionIndex)
            TRANSFER -> decodeTransfer(accounts, data, instructionIndex)
            APPROVE -> decodeApprove(accounts, data, instructionIndex)
            REVOKE -> decodeRevoke(accounts, instructionIndex)
            SET_AUTHORITY -> decodeSetAuthority(accounts, data, instructionIndex)
            MINT_TO -> decodeMintTo(accounts, data, instructionIndex)
            BURN -> decodeBurn(accounts, data, instructionIndex)
            CLOSE_ACCOUNT -> decodeCloseAccount(accounts, instructionIndex)
            FREEZE_ACCOUNT -> decodeFreezeAccount(accounts, instructionIndex)
            THAW_ACCOUNT -> decodeThawAccount(accounts, instructionIndex)
            TRANSFER_CHECKED -> decodeTransferChecked(accounts, data, instructionIndex)
            APPROVE_CHECKED -> decodeApproveChecked(accounts, data, instructionIndex)
            MINT_TO_CHECKED -> decodeMintToChecked(accounts, data, instructionIndex)
            BURN_CHECKED -> decodeBurnChecked(accounts, data, instructionIndex)
            INITIALIZE_ACCOUNT_2 -> decodeInitializeAccount2(accounts, data, instructionIndex)
            SYNC_NATIVE -> decodeSyncNative(accounts, instructionIndex)
            INITIALIZE_ACCOUNT_3 -> decodeInitializeAccount3(accounts, data, instructionIndex)
            INITIALIZE_IMMUTABLE_OWNER -> decodeInitializeImmutableOwner(accounts, instructionIndex)
            else -> createUnknownIntent(discriminator, instructionIndex)
        }
    }
    
    private fun decodeInitializeMint(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val decimals = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val mintAuthority = accounts.getOrNull(0) ?: "unknown"
        val freezeAuthority = if (data.size > 34) accounts.getOrNull(1) else null
        
        val accountRoles = mutableListOf(
            AccountRole(
                pubkey = mintAuthority,
                role = "Mint",
                isSigner = false,
                isWritable = true
            ),
            AccountRole(
                pubkey = "SysvarRent111111111111111111111111111111111",
                role = "Rent Sysvar",
                isSigner = false,
                isWritable = false
            )
        )
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeMint",
            summary = "Create new token mint with $decimals decimals",
            accounts = accountRoles,
            args = mapOf(
                "decimals" to decimals,
                "mintAuthority" to mintAuthority,
                "freezeAuthority" to freezeAuthority
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeInitializeAccount(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val tokenAccount = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeAccount",
            summary = "Initialize token account for mint ${mint.take(8)}...",
            accounts = listOf(
                AccountRole(tokenAccount, "Token Account", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(owner, "Owner", false, false),
                AccountRole("SysvarRent111111111111111111111111111111111", "Rent Sysvar", false, false)
            ),
            args = mapOf(
                "account" to tokenAccount,
                "mint" to mint,
                "owner" to owner
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeInitializeMultisig(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val multisigAccount = accounts.getOrNull(0) ?: "unknown"
        val requiredSigners = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val signers = accounts.drop(2)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeMultisig",
            summary = "Create $requiredSigners-of-${signers.size} multisig account",
            accounts = listOf(
                AccountRole(multisigAccount, "Multisig Account", false, true)
            ) + signers.mapIndexed { i, signer ->
                AccountRole(signer, "Signer ${i + 1}", false, false)
            },
            args = mapOf(
                "multisig" to multisigAccount,
                "requiredSigners" to requiredSigners,
                "totalSigners" to signers.size
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("Multisig accounts require multiple signatures for transactions")
        )
    }
    
    private fun decodeTransfer(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val destination = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            amount > 1_000_000_000_000L -> {
                warnings.add("⚠️ Very large token transfer")
                RiskLevel.HIGH
            }
            amount > 100_000_000_000L -> {
                warnings.add("Large token amount")
                RiskLevel.MEDIUM
            }
            else -> RiskLevel.LOW
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "transfer",
            summary = "Transfer $amount tokens to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(source, "Source", false, true),
                AccountRole(destination, "Destination", false, true),
                AccountRole(owner, "Owner/Authority", true, false)
            ),
            args = mapOf(
                "source" to source,
                "destination" to destination,
                "amount" to amount
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeApprove(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val delegate = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val isUnlimitedApproval = amount == Long.MAX_VALUE || amount == ULong.MAX_VALUE.toLong()
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            isUnlimitedApproval -> {
                warnings.add("🚨 UNLIMITED approval - delegate can spend ALL your tokens")
                RiskLevel.CRITICAL
            }
            amount > 1_000_000_000_000L -> {
                warnings.add("⚠️ Very large approval amount")
                RiskLevel.HIGH
            }
            else -> {
                warnings.add("Delegate can spend up to $amount tokens on your behalf")
                RiskLevel.MEDIUM
            }
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "approve",
            summary = if (isUnlimitedApproval) {
                "⚠️ UNLIMITED approval to ${delegate.take(8)}..."
            } else {
                "Approve ${delegate.take(8)}... to spend $amount tokens"
            },
            accounts = listOf(
                AccountRole(source, "Token Account", false, true),
                AccountRole(delegate, "Delegate", false, false),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "source" to source,
                "delegate" to delegate,
                "amount" to amount,
                "isUnlimited" to isUnlimitedApproval
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeRevoke(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val owner = accounts.getOrNull(1) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "revoke",
            summary = "Revoke all delegated authority",
            accounts = listOf(
                AccountRole(source, "Token Account", false, true),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "source" to source,
                "owner" to owner
            ),
            riskLevel = RiskLevel.INFO,
            warnings = listOf("This removes any previously approved delegate")
        )
    }
    
    private fun decodeSetAuthority(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val currentAuthority = accounts.getOrNull(1) ?: "unknown"
        
        val authorityType = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val authorityTypeName = when (authorityType) {
            0 -> "MintTokens"
            1 -> "FreezeAccount"
            2 -> "AccountOwner"
            3 -> "CloseAccount"
            else -> "Unknown($authorityType)"
        }
        
        val hasNewAuthority = data.size > 2 && data[2].toInt() != 0
        val newAuthority = if (hasNewAuthority && data.size > 35) {
            // Read 32 bytes starting at offset 3
            data.sliceArray(3..34).joinToString("") { "%02x".format(it) }
        } else null
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            !hasNewAuthority -> {
                warnings.add("🚨 Authority will be permanently removed - IRREVERSIBLE")
                RiskLevel.CRITICAL
            }
            authorityType == 2 -> {
                warnings.add("⚠️ Account ownership will be transferred")
                RiskLevel.HIGH
            }
            else -> {
                warnings.add("Authority type '$authorityTypeName' will be changed")
                RiskLevel.MEDIUM
            }
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "setAuthority",
            summary = if (!hasNewAuthority) {
                "🚨 PERMANENTLY REMOVE $authorityTypeName authority"
            } else {
                "Change $authorityTypeName authority"
            },
            accounts = listOf(
                AccountRole(account, "Account", false, true),
                AccountRole(currentAuthority, "Current Authority", true, false)
            ),
            args = mapOf(
                "account" to account,
                "authorityType" to authorityTypeName,
                "newAuthority" to newAuthority
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeMintTo(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val destination = accounts.getOrNull(1) ?: "unknown"
        val mintAuthority = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "mintTo",
            summary = "Mint $amount tokens to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true),
                AccountRole(destination, "Destination", false, true),
                AccountRole(mintAuthority, "Mint Authority", true, false)
            ),
            args = mapOf(
                "mint" to mint,
                "destination" to destination,
                "amount" to amount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("New tokens will be created and added to supply")
        )
    }
    
    private fun decodeBurn(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "burn",
            summary = "🔥 Burn $amount tokens permanently",
            accounts = listOf(
                AccountRole(source, "Token Account", false, true),
                AccountRole(mint, "Mint", false, true),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "source" to source,
                "mint" to mint,
                "amount" to amount
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Burned tokens are permanently destroyed and cannot be recovered")
        )
    }
    
    private fun decodeCloseAccount(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val destination = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "closeAccount",
            summary = "Close token account and recover rent",
            accounts = listOf(
                AccountRole(account, "Account to Close", false, true),
                AccountRole(destination, "Rent Recipient", false, true),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "account" to account,
                "destination" to destination,
                "owner" to owner
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("Account must have zero token balance to close")
        )
    }
    
    private fun decodeFreezeAccount(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val freezeAuthority = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "freezeAccount",
            summary = "❄️ Freeze token account - transfers disabled",
            accounts = listOf(
                AccountRole(account, "Account to Freeze", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(freezeAuthority, "Freeze Authority", true, false)
            ),
            args = mapOf(
                "account" to account,
                "mint" to mint
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Frozen accounts cannot send or receive tokens until thawed")
        )
    }
    
    private fun decodeThawAccount(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val freezeAuthority = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "thawAccount",
            summary = "🔓 Unfreeze token account - transfers enabled",
            accounts = listOf(
                AccountRole(account, "Account to Thaw", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(freezeAuthority, "Freeze Authority", true, false)
            ),
            args = mapOf(
                "account" to account,
                "mint" to mint
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeTransferChecked(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val destination = accounts.getOrNull(2) ?: "unknown"
        val owner = accounts.getOrNull(3) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val decimals = if (data.size >= 10) data[9].toInt() and 0xFF else 0
        
        // Calculate human-readable amount
        val humanAmount = amount.toDouble() / Math.pow(10.0, decimals.toDouble())
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            humanAmount > 1_000_000 -> {
                warnings.add("⚠️ Very large token transfer")
                RiskLevel.HIGH
            }
            humanAmount > 10_000 -> {
                warnings.add("Large token amount")
                RiskLevel.MEDIUM
            }
            else -> RiskLevel.LOW
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "transferChecked",
            summary = "Transfer ${"%.${decimals}f".format(humanAmount)} tokens to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(source, "Source", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(destination, "Destination", false, true),
                AccountRole(owner, "Owner/Authority", true, false)
            ),
            args = mapOf(
                "source" to source,
                "mint" to mint,
                "destination" to destination,
                "amount" to amount,
                "decimals" to decimals,
                "humanReadableAmount" to humanAmount
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeApproveChecked(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val delegate = accounts.getOrNull(2) ?: "unknown"
        val owner = accounts.getOrNull(3) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val decimals = if (data.size >= 10) data[9].toInt() and 0xFF else 0
        val humanAmount = amount.toDouble() / Math.pow(10.0, decimals.toDouble())
        
        val isUnlimited = amount == Long.MAX_VALUE
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            isUnlimited -> {
                warnings.add("🚨 UNLIMITED approval - delegate can spend ALL your tokens")
                RiskLevel.CRITICAL
            }
            humanAmount > 1_000_000 -> {
                warnings.add("⚠️ Very large approval amount")
                RiskLevel.HIGH
            }
            else -> {
                warnings.add("Delegate can spend up to ${"%.${decimals}f".format(humanAmount)} tokens")
                RiskLevel.MEDIUM
            }
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "approveChecked",
            summary = if (isUnlimited) {
                "⚠️ UNLIMITED approval to ${delegate.take(8)}..."
            } else {
                "Approve ${"%.${decimals}f".format(humanAmount)} tokens to ${delegate.take(8)}..."
            },
            accounts = listOf(
                AccountRole(source, "Token Account", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(delegate, "Delegate", false, false),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "source" to source,
                "delegate" to delegate,
                "amount" to amount,
                "decimals" to decimals,
                "humanReadableAmount" to humanAmount
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeMintToChecked(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val destination = accounts.getOrNull(1) ?: "unknown"
        val mintAuthority = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val decimals = if (data.size >= 10) data[9].toInt() and 0xFF else 0
        val humanAmount = amount.toDouble() / Math.pow(10.0, decimals.toDouble())
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "mintToChecked",
            summary = "Mint ${"%.${decimals}f".format(humanAmount)} tokens to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true),
                AccountRole(destination, "Destination", false, true),
                AccountRole(mintAuthority, "Mint Authority", true, false)
            ),
            args = mapOf(
                "mint" to mint,
                "destination" to destination,
                "amount" to amount,
                "decimals" to decimals,
                "humanReadableAmount" to humanAmount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("New tokens will be created and added to supply")
        )
    }
    
    private fun decodeBurnChecked(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val owner = accounts.getOrNull(2) ?: "unknown"
        
        val amount = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val decimals = if (data.size >= 10) data[9].toInt() and 0xFF else 0
        val humanAmount = amount.toDouble() / Math.pow(10.0, decimals.toDouble())
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "burnChecked",
            summary = "🔥 Burn ${"%.${decimals}f".format(humanAmount)} tokens permanently",
            accounts = listOf(
                AccountRole(source, "Token Account", false, true),
                AccountRole(mint, "Mint", false, true),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "source" to source,
                "mint" to mint,
                "amount" to amount,
                "decimals" to decimals,
                "humanReadableAmount" to humanAmount
            ),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf("⚠️ Burned tokens are permanently destroyed and cannot be recovered")
        )
    }
    
    private fun decodeInitializeAccount2(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val tokenAccount = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        
        // Owner is embedded in instruction data at offset 1 (32 bytes)
        val ownerBytes = if (data.size >= 33) data.sliceArray(1..32) else null
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeAccount2",
            summary = "Initialize token account (v2) for mint ${mint.take(8)}...",
            accounts = listOf(
                AccountRole(tokenAccount, "Token Account", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole("SysvarRent111111111111111111111111111111111", "Rent Sysvar", false, false)
            ),
            args = mapOf(
                "account" to tokenAccount,
                "mint" to mint
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeSyncNative(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val nativeTokenAccount = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "syncNative",
            summary = "Sync wrapped SOL balance with lamports",
            accounts = listOf(
                AccountRole(nativeTokenAccount, "Native Token Account", false, true)
            ),
            args = mapOf(
                "account" to nativeTokenAccount
            ),
            riskLevel = RiskLevel.INFO,
            warnings = emptyList()
        )
    }
    
    private fun decodeInitializeAccount3(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val tokenAccount = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeAccount3",
            summary = "Initialize token account (v3) for mint ${mint.take(8)}...",
            accounts = listOf(
                AccountRole(tokenAccount, "Token Account", false, true),
                AccountRole(mint, "Mint", false, false)
            ),
            args = mapOf(
                "account" to tokenAccount,
                "mint" to mint
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeInitializeImmutableOwner(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val tokenAccount = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "initializeImmutableOwner",
            summary = "Make token account owner immutable",
            accounts = listOf(
                AccountRole(tokenAccount, "Token Account", false, true)
            ),
            args = mapOf(
                "account" to tokenAccount
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("Account owner cannot be changed after this operation")
        )
    }
    
    private fun createUnknownIntent(discriminator: Int, instructionIndex: Int): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "SPL Token",
            programId = ProgramRegistry.TOKEN_PROGRAM,
            method = "unknown($discriminator)",
            summary = "Unknown SPL Token instruction",
            accounts = emptyList(),
            args = mapOf("discriminator" to discriminator),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
