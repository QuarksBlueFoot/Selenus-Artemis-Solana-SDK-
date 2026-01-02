/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for Token-2022 (Token Extensions) Program instructions.
 * 
 * Extends base SPL Token functionality with extension-specific decoding
 * for transfer fees, confidential transfers, permanent delegate, etc.
 */
object Token2022ProgramDecoder : InstructionDecoder {
    
    // Token-2022 uses same base instructions as SPL Token
    // Plus extension-specific instructions starting at discriminator 25+
    
    // Extension instruction discriminators
    private const val INITIALIZE_MINT_CLOSE_AUTHORITY = 25
    private const val TRANSFER_FEE_EXTENSION = 26
    private const val WITHDRAW_WITHHELD_TOKENS = 27
    private const val CONFIDENTIAL_TRANSFER_EXTENSION = 28
    private const val DEFAULT_ACCOUNT_STATE_EXTENSION = 29
    private const val REALLOCATE = 30
    private const val MEMO_TRANSFER_EXTENSION = 31
    private const val CREATE_NATIVE_MINT = 32
    private const val INITIALIZE_NON_TRANSFERABLE_MINT = 33
    private const val INTEREST_BEARING_MINT_EXTENSION = 34
    private const val CPI_GUARD_EXTENSION = 35
    private const val INITIALIZE_PERMANENT_DELEGATE = 36
    private const val TRANSFER_HOOK_EXTENSION = 37
    private const val METADATA_POINTER_EXTENSION = 38
    private const val GROUP_POINTER_EXTENSION = 39
    private const val GROUP_MEMBER_POINTER_EXTENSION = 40
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (data.isEmpty()) return null
        
        val discriminator = data[0].toInt() and 0xFF
        
        // First check for extension-specific instructions
        return when (discriminator) {
            INITIALIZE_MINT_CLOSE_AUTHORITY -> decodeMintCloseAuthority(accounts, data, instructionIndex)
            TRANSFER_FEE_EXTENSION -> decodeTransferFeeInstruction(accounts, data, instructionIndex)
            WITHDRAW_WITHHELD_TOKENS -> decodeWithdrawWithheldTokens(accounts, data, instructionIndex)
            CONFIDENTIAL_TRANSFER_EXTENSION -> decodeConfidentialTransfer(accounts, data, instructionIndex)
            DEFAULT_ACCOUNT_STATE_EXTENSION -> decodeDefaultAccountState(accounts, data, instructionIndex)
            REALLOCATE -> decodeReallocate(accounts, data, instructionIndex)
            MEMO_TRANSFER_EXTENSION -> decodeMemoTransferExtension(accounts, data, instructionIndex)
            INITIALIZE_NON_TRANSFERABLE_MINT -> decodeNonTransferableMint(accounts, instructionIndex)
            INITIALIZE_PERMANENT_DELEGATE -> decodePermanentDelegate(accounts, data, instructionIndex)
            TRANSFER_HOOK_EXTENSION -> decodeTransferHook(accounts, data, instructionIndex)
            METADATA_POINTER_EXTENSION -> decodeMetadataPointer(accounts, data, instructionIndex)
            else -> {
                // Fall back to base SPL Token decoder for standard instructions
                val baseIntent = TokenProgramDecoder.decode(programId, accounts, data, instructionIndex)
                
                // Enhance with Token-2022 context
                baseIntent?.copy(
                    programName = "Token-2022",
                    programId = ProgramRegistry.TOKEN_2022_PROGRAM,
                    warnings = baseIntent.warnings + listOf("Token-2022 program - may have extensions enabled")
                )
            }
        }
    }
    
    private fun decodeMintCloseAuthority(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val closeAuthority = if (data.size > 1 && data[1].toInt() != 0) {
            // Has close authority
            accounts.getOrNull(1) ?: "embedded in data"
        } else null
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeMintCloseAuthority",
            summary = if (closeAuthority != null) {
                "Set mint close authority to ${closeAuthority.take(8)}..."
            } else {
                "Initialize mint without close authority"
            },
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf(
                "mint" to mint,
                "closeAuthority" to closeAuthority
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = if (closeAuthority != null) {
                listOf("⚠️ Close authority can permanently close this mint")
            } else {
                emptyList()
            }
        )
    }
    
    private fun decodeTransferFeeInstruction(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        // Sub-instruction discriminator at offset 1
        val subInstruction = if (data.size > 1) data[1].toInt() and 0xFF else 0
        
        return when (subInstruction) {
            0 -> decodeInitializeTransferFee(accounts, data, instructionIndex)
            1 -> decodeTransferCheckedWithFee(accounts, data, instructionIndex)
            2 -> decodeSetTransferFee(accounts, data, instructionIndex)
            3 -> decodeHarvestWithheldTokens(accounts, instructionIndex)
            else -> createUnknownTransferFeeIntent(subInstruction, instructionIndex)
        }
    }
    
    private fun decodeInitializeTransferFee(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        
        // Parse transfer fee config from data
        val transferFeeBasisPoints = if (data.size >= 6) {
            ByteBuffer.wrap(data.sliceArray(2..3))
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toInt() and 0xFFFF
        } else 0
        
        val maximumFee = if (data.size >= 14) {
            ByteBuffer.wrap(data.sliceArray(6..13))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val feePercentage = transferFeeBasisPoints / 100.0
        
        val warnings = mutableListOf<String>()
        if (feePercentage > 5.0) {
            warnings.add("⚠️ High transfer fee: $feePercentage%")
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeTransferFee",
            summary = "Initialize transfer fee: ${"%.2f".format(feePercentage)}% (max: $maximumFee)",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf(
                "mint" to mint,
                "transferFeeBasisPoints" to transferFeeBasisPoints,
                "maximumFee" to maximumFee,
                "feePercentage" to feePercentage
            ),
            riskLevel = if (feePercentage > 10.0) RiskLevel.HIGH else RiskLevel.MEDIUM,
            warnings = warnings + listOf("Transfers of this token will include fees")
        )
    }
    
    private fun decodeTransferCheckedWithFee(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val source = accounts.getOrNull(0) ?: "unknown"
        val mint = accounts.getOrNull(1) ?: "unknown"
        val destination = accounts.getOrNull(2) ?: "unknown"
        val authority = accounts.getOrNull(3) ?: "unknown"
        
        // Amount at offset 2
        val amount = if (data.size >= 10) {
            ByteBuffer.wrap(data.sliceArray(2..9))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        // Fee at offset 10
        val fee = if (data.size >= 18) {
            ByteBuffer.wrap(data.sliceArray(10..17))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        val decimals = if (data.size >= 19) data[18].toInt() and 0xFF else 0
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "transferCheckedWithFee",
            summary = "Transfer $amount tokens (fee: $fee) to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(source, "Source", false, true),
                AccountRole(mint, "Mint", false, false),
                AccountRole(destination, "Destination", false, true),
                AccountRole(authority, "Authority", true, false)
            ),
            args = mapOf(
                "source" to source,
                "destination" to destination,
                "amount" to amount,
                "fee" to fee,
                "decimals" to decimals
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("Transfer fee of $fee will be withheld")
        )
    }
    
    private fun decodeSetTransferFee(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val authority = accounts.getOrNull(1) ?: "unknown"
        
        val newFeeBasisPoints = if (data.size >= 4) {
            ByteBuffer.wrap(data.sliceArray(2..3))
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toInt() and 0xFFFF
        } else 0
        
        val feePercentage = newFeeBasisPoints / 100.0
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "setTransferFee",
            summary = "Change transfer fee to ${"%.2f".format(feePercentage)}%",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true),
                AccountRole(authority, "Transfer Fee Config Authority", true, false)
            ),
            args = mapOf(
                "mint" to mint,
                "newFeeBasisPoints" to newFeeBasisPoints,
                "feePercentage" to feePercentage
            ),
            riskLevel = if (feePercentage > 10.0) RiskLevel.HIGH else RiskLevel.MEDIUM,
            warnings = listOf("Transfer fee will change for all future transfers")
        )
    }
    
    private fun decodeHarvestWithheldTokens(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val feeRecipient = accounts.getOrNull(1) ?: "unknown"
        val sourceAccounts = accounts.drop(2)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "harvestWithheldTokensToMint",
            summary = "Harvest withheld fees from ${sourceAccounts.size} accounts",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true),
                AccountRole(feeRecipient, "Fee Recipient", false, true)
            ) + sourceAccounts.mapIndexed { i, acc ->
                AccountRole(acc, "Source ${i + 1}", false, true)
            },
            args = mapOf(
                "mint" to mint,
                "feeRecipient" to feeRecipient,
                "sourceCount" to sourceAccounts.size
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeWithdrawWithheldTokens(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val destination = accounts.getOrNull(1) ?: "unknown"
        val authority = accounts.getOrNull(2) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "withdrawWithheldTokens",
            summary = "Withdraw withheld transfer fees to ${destination.take(8)}...",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true),
                AccountRole(destination, "Destination", false, true),
                AccountRole(authority, "Withdraw Authority", true, false)
            ),
            args = mapOf(
                "mint" to mint,
                "destination" to destination
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeConfidentialTransfer(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        // Sub-instruction
        val subInstruction = if (data.size > 1) data[1].toInt() and 0xFF else 0
        
        val methodName = when (subInstruction) {
            0 -> "initializeConfidentialMint"
            1 -> "updateConfidentialMint"
            2 -> "configureConfidentialAccount"
            3 -> "approveConfidentialAccount"
            4 -> "emptyConfidentialAccount"
            5 -> "depositConfidential"
            6 -> "withdrawConfidential"
            7 -> "confidentialTransfer"
            8 -> "applyPendingBalance"
            else -> "unknownConfidential($subInstruction)"
        }
        
        val account = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = methodName,
            summary = "Confidential transfer operation: $methodName",
            accounts = listOf(
                AccountRole(account, "Account", false, true)
            ),
            args = mapOf(
                "subInstruction" to subInstruction
            ),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("🔐 Confidential transfer - amounts are encrypted")
        )
    }
    
    private fun decodeDefaultAccountState(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        val defaultState = if (data.size > 1) {
            when (data[1].toInt() and 0xFF) {
                1 -> "Initialized"
                2 -> "Frozen"
                else -> "Uninitialized"
            }
        } else "Uninitialized"
        
        val isFrozenDefault = defaultState == "Frozen"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeDefaultAccountState",
            summary = "Set default account state: $defaultState",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf(
                "mint" to mint,
                "defaultState" to defaultState
            ),
            riskLevel = if (isFrozenDefault) RiskLevel.HIGH else RiskLevel.LOW,
            warnings = if (isFrozenDefault) {
                listOf("⚠️ New accounts will be frozen by default")
            } else {
                emptyList()
            }
        )
    }
    
    private fun decodeReallocate(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val payer = accounts.getOrNull(1) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "reallocate",
            summary = "Reallocate account space for extensions",
            accounts = listOf(
                AccountRole(account, "Token Account", false, true),
                AccountRole(payer, "Payer", true, true),
                AccountRole("11111111111111111111111111111111", "System Program", false, false)
            ),
            args = mapOf(
                "account" to account,
                "payer" to payer
            ),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun decodeMemoTransferExtension(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val account = accounts.getOrNull(0) ?: "unknown"
        val owner = accounts.getOrNull(1) ?: "unknown"
        
        val enable = data.size > 1 && data[1].toInt() != 0
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = if (enable) "enableRequiredMemoTransfers" else "disableRequiredMemoTransfers",
            summary = if (enable) {
                "Require memo for incoming transfers"
            } else {
                "Remove memo requirement for transfers"
            },
            accounts = listOf(
                AccountRole(account, "Token Account", false, true),
                AccountRole(owner, "Owner", true, false)
            ),
            args = mapOf(
                "account" to account,
                "requireMemo" to enable
            ),
            riskLevel = RiskLevel.LOW,
            warnings = if (enable) {
                listOf("Transfers without memo will be rejected")
            } else {
                emptyList()
            }
        )
    }
    
    private fun decodeNonTransferableMint(
        accounts: List<String>,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeNonTransferableMint",
            summary = "🔒 Make token non-transferable (soulbound)",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf("mint" to mint),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf(
                "🚨 IRREVERSIBLE: Tokens cannot be transferred after minting",
                "This creates soulbound/non-transferable tokens"
            )
        )
    }
    
    private fun decodePermanentDelegate(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializePermanentDelegate",
            summary = "⚠️ Set permanent delegate authority",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf("mint" to mint),
            riskLevel = RiskLevel.CRITICAL,
            warnings = listOf(
                "🚨 CRITICAL: Permanent delegate can transfer or burn tokens from ANY holder",
                "This authority cannot be revoked - use with extreme caution"
            )
        )
    }
    
    private fun decodeTransferHook(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeTransferHook",
            summary = "Configure transfer hook program",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf("mint" to mint),
            riskLevel = RiskLevel.HIGH,
            warnings = listOf(
                "⚠️ Transfer hook will execute custom program on each transfer",
                "Review the hook program carefully before proceeding"
            )
        )
    }
    
    private fun decodeMetadataPointer(
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val mint = accounts.getOrNull(0) ?: "unknown"
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "initializeMetadataPointer",
            summary = "Configure on-chain metadata pointer",
            accounts = listOf(
                AccountRole(mint, "Mint", false, true)
            ),
            args = mapOf("mint" to mint),
            riskLevel = RiskLevel.LOW,
            warnings = emptyList()
        )
    }
    
    private fun createUnknownTransferFeeIntent(
        subInstruction: Int,
        instructionIndex: Int
    ): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Token-2022",
            programId = ProgramRegistry.TOKEN_2022_PROGRAM,
            method = "transferFee.unknown($subInstruction)",
            summary = "Unknown transfer fee instruction",
            accounts = emptyList(),
            args = mapOf("subInstruction" to subInstruction),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
