/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * NlpExecutor - Execute NLP intents as real Solana transactions.
 */
package com.selenus.artemis.nlp

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.tx.Transaction
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.programs.TokenProgram
import com.selenus.artemis.programs.AssociatedTokenProgram
import com.selenus.artemis.programs.StakeProgram
import com.selenus.artemis.programs.MemoProgram
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.wallet.WalletAdapter
import com.selenus.artemis.wallet.SignTxRequest
import kotlinx.coroutines.*
import java.util.Base64

// Wrapped SOL mint address
private val WRAPPED_SOL_MINT = Pubkey.fromBase58("So11111111111111111111111111111111111111112")

/**
 * NLP Executor - Converts intents to executed transactions.
 */
class NlpExecutor(
    private val rpc: RpcApi,
    private val wallet: WalletAdapter,
    private val config: NlpExecutorConfig = NlpExecutorConfig()
) {
    
    /**
     * Execute an NLP intent.
     */
    suspend fun execute(intent: TransactionIntent): ExecutionResult {
        return try {
            // Handle non-transaction intents first
            when (intent.type) {
                IntentType.CHECK_BALANCE -> return executeBalanceCheck(intent)
                IntentType.AIRDROP -> return executeAirdrop(intent)
                else -> {}
            }
            
            val instructions = buildInstructions(intent)
            if (instructions.isEmpty()) {
                return ExecutionResult.Failed(
                    error = "No instructions generated for intent: ${intent.type}",
                    intent = intent,
                    recoverable = false
                )
            }
            
            val transaction = buildTransaction(instructions)
            val signedTx = signTransaction(transaction)
            val signature = submitTransaction(signedTx)
            
            ExecutionResult.Success(
                signature = signature,
                intent = intent,
                explorerUrl = "${config.explorerBaseUrl}/tx/$signature?cluster=${config.cluster}"
            )
        } catch (e: Exception) {
            ExecutionResult.Failed(
                error = e.message ?: "Unknown error",
                intent = intent,
                recoverable = isRecoverable(e)
            )
        }
    }
    
    /**
     * Simulate an intent without submitting.
     */
    suspend fun simulate(intent: TransactionIntent): SimulationResult {
        return try {
            val instructions = buildInstructions(intent)
            val transaction = buildTransaction(instructions)
            
            // Get the message bytes for simulation
            val message = transaction.compileMessage()
            val msgBytes = message.serialize()
            val base64Msg = Base64.getEncoder().encodeToString(msgBytes)
            
            // Simulate with replaceRecentBlockhash=true to skip signature verification
            val simResult = rpc.simulateTransaction(base64Msg, sigVerify = false, replaceRecentBlockhash = true)
            
            val value = simResult["value"]?.let { 
                if (it is kotlinx.serialization.json.JsonNull) null 
                else it as? kotlinx.serialization.json.JsonObject 
            }
            val error = value?.get("err")
            val logs = value?.get("logs")?.let { 
                if (it is kotlinx.serialization.json.JsonArray) {
                    it.map { log -> log.toString().trim('"') }
                } else emptyList()
            } ?: emptyList()
            val unitsConsumed = value?.get("unitsConsumed")?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLongOrNull() else null
            } ?: 0L
            
            if (error != null && error !is kotlinx.serialization.json.JsonNull) {
                SimulationResult.Failed(
                    error = error.toString(),
                    logs = logs,
                    intent = intent
                )
            } else {
                SimulationResult.Success(
                    unitsConsumed = unitsConsumed,
                    logs = logs,
                    estimatedFee = estimateFee(unitsConsumed),
                    intent = intent
                )
            }
        } catch (e: Exception) {
            SimulationResult.Failed(
                error = e.message ?: "Simulation failed",
                logs = emptyList(),
                intent = intent
            )
        }
    }
    
    // ===========================================
    // Instruction Building
    // ===========================================
    
    private suspend fun buildInstructions(intent: TransactionIntent): List<Instruction> {
        return when (intent.type) {
            IntentType.TRANSFER_SOL -> buildTransferSolInstructions(intent)
            IntentType.TRANSFER_TOKEN -> buildTransferTokenInstructions(intent)
            IntentType.SWAP -> emptyList() // Swaps require Jupiter or other DEX integration
            IntentType.STAKE -> buildStakeInstructions(intent)
            IntentType.UNSTAKE -> buildUnstakeInstructions(intent)
            IntentType.DELEGATE -> buildDelegateInstructions(intent)
            IntentType.CREATE_TOKEN -> emptyList() // Token creation requires mint keypair
            IntentType.MINT_TOKEN -> buildMintInstructions(intent)
            IntentType.BURN_TOKEN -> buildBurnInstructions(intent)
            IntentType.CLOSE_ACCOUNT -> buildCloseAccountInstructions(intent)
            IntentType.TRANSFER_NFT -> buildNftTransferInstructions(intent)
            IntentType.BURN_NFT -> buildNftBurnInstructions(intent)
            IntentType.CREATE_ACCOUNT -> emptyList() // Account creation requires new keypair
            IntentType.CREATE_ATA -> buildCreateAtaInstructions(intent)
            IntentType.APPROVE_DELEGATE -> buildApproveInstructions(intent)
            IntentType.REVOKE_DELEGATE -> buildRevokeInstructions(intent)
            IntentType.WRAP_SOL -> buildWrapSolInstructions(intent)
            IntentType.UNWRAP_SOL -> buildUnwrapSolInstructions(intent)
            IntentType.MEMO -> buildMemoInstructions(intent)
            IntentType.FREEZE_ACCOUNT, IntentType.THAW_ACCOUNT, IntentType.SET_AUTHORITY -> emptyList()
            IntentType.UNKNOWN, IntentType.CHECK_BALANCE, IntentType.AIRDROP -> emptyList()
        }
    }
    
    // ===========================================
    // SOL Transfer
    // ===========================================
    
    private fun buildTransferSolInstructions(intent: TransactionIntent): List<Instruction> {
        val amount = intent.getAmount()
        val recipient = intent.getRecipient()
        val lamports = (amount * 1_000_000_000).toLong()
        
        return listOf(
            SystemProgram.transfer(
                from = wallet.publicKey,
                to = recipient,
                lamports = lamports
            )
        )
    }
    
    // ===========================================
    // Token Transfer
    // ===========================================
    
    private suspend fun buildTransferTokenInstructions(intent: TransactionIntent): List<Instruction> {
        val amount = intent.getAmount()
        val recipient = intent.getRecipient()
        val mint = intent.getMint()
        val decimals = intent.getDecimals() ?: getTokenDecimals(mint)
        
        val sourceAta = deriveAta(wallet.publicKey, mint)
        val destinationAta = deriveAta(recipient, mint)
        
        val instructions = mutableListOf<Instruction>()
        
        // Create destination ATA if it doesn't exist
        if (!accountExists(destinationAta)) {
            instructions.add(
                AssociatedTokenProgram.createAssociatedTokenAccount(
                    payer = wallet.publicKey,
                    ata = destinationAta,
                    owner = recipient,
                    mint = mint
                )
            )
        }
        
        val tokenAmount = (amount * Math.pow(10.0, decimals.toDouble())).toLong()
        
        instructions.add(
            TokenProgram.transferChecked(
                source = sourceAta,
                mint = mint,
                destination = destinationAta,
                owner = wallet.publicKey,
                amount = tokenAmount,
                decimals = decimals
            )
        )
        
        return instructions
    }
    
    // ===========================================
    // Staking
    // ===========================================
    
    private fun buildStakeInstructions(intent: TransactionIntent): List<Instruction> {
        val validator = intent.getValidator()
        val stakeAccount = intent.getStakeAccount()
        
        return listOf(
            StakeProgram.delegate(
                stakeAccount = stakeAccount,
                voteAccount = validator,
                authorizedStaker = wallet.publicKey
            )
        )
    }
    
    private fun buildUnstakeInstructions(intent: TransactionIntent): List<Instruction> {
        val stakeAccount = intent.getStakeAccount()
        
        return listOf(
            StakeProgram.deactivate(
                stakeAccount = stakeAccount,
                authorizedStaker = wallet.publicKey
            )
        )
    }
    
    private fun buildDelegateInstructions(intent: TransactionIntent): List<Instruction> {
        val validator = intent.getValidator()
        val stakeAccount = intent.getStakeAccount()
        
        return listOf(
            StakeProgram.delegate(
                stakeAccount = stakeAccount,
                voteAccount = validator,
                authorizedStaker = wallet.publicKey
            )
        )
    }
    
    // ===========================================
    // Token Minting
    // ===========================================
    
    private suspend fun buildMintInstructions(intent: TransactionIntent): List<Instruction> {
        val amount = intent.getAmount()
        val mint = intent.getMint()
        val decimals = intent.getDecimals() ?: getTokenDecimals(mint)
        
        val destination = deriveAta(wallet.publicKey, mint)
        
        val instructions = mutableListOf<Instruction>()
        
        // Create ATA if needed
        if (!accountExists(destination)) {
            instructions.add(
                AssociatedTokenProgram.createAssociatedTokenAccount(
                    payer = wallet.publicKey,
                    ata = destination,
                    owner = wallet.publicKey,
                    mint = mint
                )
            )
        }
        
        val tokenAmount = (amount * Math.pow(10.0, decimals.toDouble())).toLong()
        
        instructions.add(
            TokenProgram.mintTo(
                mint = mint,
                destination = destination,
                mintAuthority = wallet.publicKey,
                amount = tokenAmount
            )
        )
        
        return instructions
    }
    
    // ===========================================
    // Token Burning
    // ===========================================
    
    private suspend fun buildBurnInstructions(intent: TransactionIntent): List<Instruction> {
        val amount = intent.getAmount()
        val mint = intent.getMint()
        val decimals = intent.getDecimals() ?: getTokenDecimals(mint)
        
        val tokenAccount = deriveAta(wallet.publicKey, mint)
        val tokenAmount = (amount * Math.pow(10.0, decimals.toDouble())).toLong()
        
        return listOf(
            TokenProgram.burn(
                account = tokenAccount,
                mint = mint,
                owner = wallet.publicKey,
                amount = tokenAmount
            )
        )
    }
    
    // ===========================================
    // Close Account
    // ===========================================
    
    private fun buildCloseAccountInstructions(intent: TransactionIntent): List<Instruction> {
        val tokenAccount = intent.getTokenAccount()
        
        return listOf(
            TokenProgram.closeAccount(
                account = tokenAccount,
                destination = wallet.publicKey,
                owner = wallet.publicKey
            )
        )
    }
    
    // ===========================================
    // NFT Operations
    // ===========================================
    
    private suspend fun buildNftTransferInstructions(intent: TransactionIntent): List<Instruction> {
        val recipient = intent.getRecipient()
        val mint = intent.getMint()
        
        val sourceAta = deriveAta(wallet.publicKey, mint)
        val destinationAta = deriveAta(recipient, mint)
        
        val instructions = mutableListOf<Instruction>()
        
        // Create destination ATA if needed
        if (!accountExists(destinationAta)) {
            instructions.add(
                AssociatedTokenProgram.createAssociatedTokenAccount(
                    payer = wallet.publicKey,
                    ata = destinationAta,
                    owner = recipient,
                    mint = mint
                )
            )
        }
        
        // NFT transfer = transfer 1 token
        instructions.add(
            TokenProgram.transfer(
                source = sourceAta,
                destination = destinationAta,
                owner = wallet.publicKey,
                amount = 1
            )
        )
        
        return instructions
    }
    
    private suspend fun buildNftBurnInstructions(intent: TransactionIntent): List<Instruction> {
        val mint = intent.getMint()
        val tokenAccount = deriveAta(wallet.publicKey, mint)
        
        return listOf(
            TokenProgram.burn(
                account = tokenAccount,
                mint = mint,
                owner = wallet.publicKey,
                amount = 1
            )
        )
    }
    
    // ===========================================
    // Create ATA
    // ===========================================
    
    private fun buildCreateAtaInstructions(intent: TransactionIntent): List<Instruction> {
        val owner = intent.getAtaOwner() ?: wallet.publicKey
        val mint = intent.getMint()
        val ata = deriveAta(owner, mint)
        
        return listOf(
            AssociatedTokenProgram.createAssociatedTokenAccount(
                payer = wallet.publicKey,
                ata = ata,
                owner = owner,
                mint = mint
            )
        )
    }
    
    // ===========================================
    // Approve/Revoke Delegation
    // ===========================================
    
    private suspend fun buildApproveInstructions(intent: TransactionIntent): List<Instruction> {
        val delegate = intent.getDelegate()
        val amount = intent.getAmount()
        val mint = intent.getMint()
        val decimals = intent.getDecimals() ?: getTokenDecimals(mint)
        
        val tokenAccount = deriveAta(wallet.publicKey, mint)
        val tokenAmount = (amount * Math.pow(10.0, decimals.toDouble())).toLong()
        
        return listOf(
            TokenProgram.approve(
                source = tokenAccount,
                delegate = delegate,
                owner = wallet.publicKey,
                amount = tokenAmount
            )
        )
    }
    
    private suspend fun buildRevokeInstructions(intent: TransactionIntent): List<Instruction> {
        val mint = intent.getMint()
        val tokenAccount = deriveAta(wallet.publicKey, mint)
        
        return listOf(
            TokenProgram.revoke(
                source = tokenAccount,
                owner = wallet.publicKey
            )
        )
    }
    
    // ===========================================
    // SOL Wrapping/Unwrapping
    // ===========================================
    
    private suspend fun buildWrapSolInstructions(intent: TransactionIntent): List<Instruction> {
        val amount = intent.getAmount()
        val lamports = (amount * 1_000_000_000).toLong()
        val wsolAta = deriveAta(wallet.publicKey, WRAPPED_SOL_MINT)
        
        val instructions = mutableListOf<Instruction>()
        
        // Create wSOL ATA if needed
        if (!accountExists(wsolAta)) {
            instructions.add(
                AssociatedTokenProgram.createAssociatedTokenAccount(
                    payer = wallet.publicKey,
                    ata = wsolAta,
                    owner = wallet.publicKey,
                    mint = WRAPPED_SOL_MINT
                )
            )
        }
        
        // Transfer SOL to the wSOL account
        instructions.add(
            SystemProgram.transfer(
                from = wallet.publicKey,
                to = wsolAta,
                lamports = lamports
            )
        )
        
        // Sync native to update the wSOL balance
        instructions.add(TokenProgram.syncNative(wsolAta))
        
        return instructions
    }
    
    private suspend fun buildUnwrapSolInstructions(intent: TransactionIntent): List<Instruction> {
        val wsolAta = deriveAta(wallet.publicKey, WRAPPED_SOL_MINT)
        
        // Close the wSOL account to unwrap
        return listOf(
            TokenProgram.closeAccount(
                account = wsolAta,
                destination = wallet.publicKey,
                owner = wallet.publicKey
            )
        )
    }
    
    // ===========================================
    // Memo
    // ===========================================
    
    private fun buildMemoInstructions(intent: TransactionIntent): List<Instruction> {
        val message = intent.getMemo()
        return listOf(MemoProgram.memo(message))
    }
    
    // ===========================================
    // Non-Transaction Operations
    // ===========================================
    
    private suspend fun executeBalanceCheck(intent: TransactionIntent): ExecutionResult {
        return try {
            val address = intent.getBalanceAddress() ?: wallet.publicKey
            val balanceResult = rpc.getBalance(address.toBase58())
            val solBalance = balanceResult.lamports / 1_000_000_000.0
            
            ExecutionResult.BalanceResult(
                address = address.toBase58(),
                balance = solBalance,
                token = "SOL"
            )
        } catch (e: Exception) {
            ExecutionResult.Failed(
                error = "Failed to get balance: ${e.message}",
                intent = intent,
                recoverable = true
            )
        }
    }
    
    private suspend fun executeAirdrop(intent: TransactionIntent): ExecutionResult {
        return try {
            if (config.cluster != "devnet" && config.cluster != "testnet") {
                return ExecutionResult.Failed(
                    error = "Airdrops only available on devnet/testnet",
                    intent = intent,
                    recoverable = false
                )
            }
            
            val amount = intent.getAirdropAmount() ?: 1.0
            val lamports = (amount * 1_000_000_000).toLong()
            val address = wallet.publicKey.toBase58()
            
            val signature = rpc.requestAirdrop(address, lamports)
            
            ExecutionResult.Success(
                signature = signature,
                intent = intent,
                explorerUrl = "${config.explorerBaseUrl}/tx/$signature?cluster=${config.cluster}"
            )
        } catch (e: Exception) {
            ExecutionResult.Failed(
                error = "Airdrop failed: ${e.message}",
                intent = intent,
                recoverable = true
            )
        }
    }
    
    // ===========================================
    // Transaction Building & Signing
    // ===========================================
    
    private suspend fun buildTransaction(instructions: List<Instruction>): Transaction {
        val blockhash = rpc.getLatestBlockhash()
        
        val tx = Transaction(
            feePayer = wallet.publicKey,
            recentBlockhash = blockhash.blockhash,
            instructions = instructions
        )
        
        return tx
    }
    
    private suspend fun signTransaction(transaction: Transaction): ByteArray {
        val message = transaction.compileMessage()
        val messageBytes = message.serialize()
        
        val request = SignTxRequest(purpose = "NLP Transaction")
        val signedMessage = wallet.signMessage(messageBytes, request)
        
        // The wallet returns the full signed transaction or just the signature
        // For local signers, we need to construct the full transaction
        return if (signedMessage.size == 64) {
            // Just a signature - construct full signed tx
            val sigCount = byteArrayOf(1)
            sigCount + signedMessage + messageBytes
        } else {
            // Full signed transaction
            signedMessage
        }
    }
    
    private suspend fun submitTransaction(signedTx: ByteArray): String {
        return rpc.sendRawTransaction(
            signedTx,
            skipPreflight = config.skipPreflight,
            maxRetries = config.maxRetries
        )
    }
    
    // ===========================================
    // Helpers
    // ===========================================
    
    /**
     * Derive an Associated Token Account address.
     */
    private fun deriveAta(owner: Pubkey, mint: Pubkey): Pubkey {
        val seeds = listOf(
            owner.bytes,
            ProgramIds.TOKEN_PROGRAM.bytes,
            mint.bytes
        )
        return Pda.findProgramAddress(seeds, ProgramIds.ASSOCIATED_TOKEN_PROGRAM).address
    }
    
    private suspend fun accountExists(address: Pubkey): Boolean {
        return try {
            val info = rpc.getAccountInfoBase64(address.toBase58())
            info != null
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun getTokenDecimals(mint: Pubkey): Int {
        val info = rpc.getMintInfoParsed(mint.toBase58())
        return info?.decimals ?: 9
    }
    
    private fun estimateFee(unitsConsumed: Long): Long {
        // 5000 lamports base + priority fee estimate
        return 5000 + (unitsConsumed / 1000)
    }
    
    private fun isRecoverable(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("blockhash") || 
               msg.contains("timeout") || 
               msg.contains("rate limit") ||
               msg.contains("network")
    }
}

// ===========================================
// Configuration
// ===========================================

data class NlpExecutorConfig(
    val cluster: String = "devnet",
    val explorerBaseUrl: String = "https://explorer.solana.com",
    val skipPreflight: Boolean = false,
    val maxRetries: Int? = 3,
    val confirmationTimeout: Long = 30_000
)

// ===========================================
// Results
// ===========================================

sealed class ExecutionResult {
    data class Success(
        val signature: String,
        val intent: TransactionIntent,
        val explorerUrl: String
    ) : ExecutionResult()
    
    data class Failed(
        val error: String,
        val intent: TransactionIntent,
        val recoverable: Boolean
    ) : ExecutionResult()
    
    data class BalanceResult(
        val address: String,
        val balance: Double,
        val token: String
    ) : ExecutionResult()
}

sealed class SimulationResult {
    data class Success(
        val unitsConsumed: Long,
        val logs: List<String>,
        val estimatedFee: Long,
        val intent: TransactionIntent
    ) : SimulationResult()
    
    data class Failed(
        val error: String,
        val logs: List<String>,
        val intent: TransactionIntent
    ) : SimulationResult()
}

// ===========================================
// Intent Extensions
// ===========================================

private fun TransactionIntent.getAmount(): Double {
    val entity = entities["amount"] ?: return 0.0
    return entity.resolvedValue.toDoubleOrNull() ?: 0.0
}

private fun TransactionIntent.getRecipient(): Pubkey {
    val entity = entities["recipient"] ?: entities["to"] ?: throw IllegalArgumentException("No recipient specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getMint(): Pubkey {
    val entity = entities["mint"] ?: entities["token"] ?: throw IllegalArgumentException("No mint specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getDecimals(): Int? {
    val entity = entities["decimals"] ?: return null
    return entity.resolvedValue.toIntOrNull()
}

private fun TransactionIntent.getValidator(): Pubkey {
    val entity = entities["validator"] ?: throw IllegalArgumentException("No validator specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getStakeAccount(): Pubkey {
    val entity = entities["stakeAccount"] ?: throw IllegalArgumentException("No stake account specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getTokenAccount(): Pubkey {
    val entity = entities["tokenAccount"] ?: throw IllegalArgumentException("No token account specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getAtaOwner(): Pubkey? {
    val entity = entities["ataOwner"] ?: return null
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getDelegate(): Pubkey {
    val entity = entities["delegate"] ?: throw IllegalArgumentException("No delegate specified")
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getMemo(): String {
    val entity = entities["memo"] ?: entities["message"] ?: return ""
    return entity.resolvedValue
}

private fun TransactionIntent.getBalanceAddress(): Pubkey? {
    val entity = entities["address"] ?: return null
    return Pubkey.fromBase58(entity.resolvedValue)
}

private fun TransactionIntent.getAirdropAmount(): Double? {
    val entity = entities["amount"] ?: return null
    return entity.resolvedValue.toDoubleOrNull()
}
