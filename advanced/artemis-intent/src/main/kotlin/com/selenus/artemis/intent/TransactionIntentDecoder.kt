/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Transaction
import com.selenus.artemis.tx.VersionedTransaction
import com.selenus.artemis.tx.CompiledInstruction
import com.selenus.artemis.tx.ShortVec
import java.nio.ByteBuffer

/**
 * Main entry point for the Transaction Intent Protocol.
 * 
 * Decodes entire transactions into human-readable intents suitable
 * for mobile wallet approval dialogs.
 * 
 * ## Usage
 * ```kotlin
 * val analysis = TransactionIntentDecoder.decode(transaction)
 * 
 * // Display to user
 * println("Summary: ${analysis.summary}")
 * println("Risk: ${analysis.overallRisk.emoji} ${analysis.overallRisk.name}")
 * 
 * for (intent in analysis.intents) {
 *     println("- ${intent.summary}")
 * }
 * 
 * for (warning in analysis.warnings) {
 *     println("⚠️ $warning")
 * }
 * ```
 */
object TransactionIntentDecoder {
    
    /**
     * Decode raw transaction bytes into human-readable intents.
     * 
     * Supports both legacy and versioned transaction formats.
     */
    fun decodeFromBytes(bytes: ByteArray): TransactionIntentAnalysis {
        if (bytes.isEmpty()) {
            return TransactionIntentAnalysis(
                summary = "Empty transaction",
                intents = emptyList(),
                overallRisk = RiskLevel.INFO,
                warnings = listOf("Transaction data is empty"),
                programsInvolved = emptyList(),
                accountsInvolved = emptyList(),
                estimatedFee = null
            )
        }
        
        // Try to parse as legacy or versioned transaction
        return try {
            // Skip signature section and parse message
            val buffer = ByteBuffer.wrap(bytes)
            
            // Read signature count
            val (sigCount, sigCountBytes) = ShortVec.decodeLen(bytes)
            var offset = sigCountBytes
            
            // Skip signatures (each is 64 bytes)
            offset += sigCount * 64
            
            // Check if this is a versioned transaction (first byte of message has version)
            val versionByte = bytes[offset].toInt() and 0xFF
            
            if (versionByte >= 0x80) {
                // Versioned transaction (v0 = 0x80)
                decodeVersionedMessageBytes(bytes, offset)
            } else {
                // Legacy transaction
                decodeLegacyMessageBytes(bytes, offset)
            }
        } catch (e: Exception) {
            TransactionIntentAnalysis(
                summary = "Unable to decode transaction",
                intents = emptyList(),
                overallRisk = RiskLevel.MEDIUM,
                warnings = listOf("Transaction parsing failed: ${e.message}"),
                programsInvolved = emptyList(),
                accountsInvolved = emptyList(),
                estimatedFee = null
            )
        }
    }
    
    private fun decodeLegacyMessageBytes(bytes: ByteArray, startOffset: Int): TransactionIntentAnalysis {
        var offset = startOffset
        
        // Read message header (3 bytes)
        val numRequiredSignatures = bytes[offset++].toInt() and 0xFF
        val numReadonlySigned = bytes[offset++].toInt() and 0xFF
        val numReadonlyUnsigned = bytes[offset++].toInt() and 0xFF
        
        // Read account keys
        val (numAccounts, accountLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
        offset += accountLenBytes
        
        val accountKeys = mutableListOf<String>()
        repeat(numAccounts) {
            val pubkeyBytes = bytes.sliceArray(offset until offset + 32)
            accountKeys.add(Base58.encode(pubkeyBytes))
            offset += 32
        }
        
        // Skip recent blockhash (32 bytes)
        offset += 32
        
        // Read instructions
        val (numInstructions, instructionLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
        offset += instructionLenBytes
        
        val intents = mutableListOf<TransactionIntent>()
        
        repeat(numInstructions) { instructionIndex ->
            // Program ID index
            val programIdIndex = bytes[offset++].toInt() and 0xFF
            val programId = accountKeys.getOrNull(programIdIndex) ?: "unknown"
            
            // Account indexes
            val (numAccountIndexes, accountIndexLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
            offset += accountIndexLenBytes
            
            val accounts = mutableListOf<String>()
            repeat(numAccountIndexes) {
                val accountIndex = bytes[offset++].toInt() and 0xFF
                accounts.add(accountKeys.getOrNull(accountIndex) ?: "unknown")
            }
            
            // Instruction data
            val (dataLen, dataLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
            offset += dataLenBytes
            
            val data = bytes.sliceArray(offset until offset + dataLen)
            offset += dataLen
            
            val intent = decodeInstruction(programId, accounts, data, instructionIndex)
            intents.add(intent)
        }
        
        return analyze(intents)
    }
    
    private fun decodeVersionedMessageBytes(bytes: ByteArray, startOffset: Int): TransactionIntentAnalysis {
        var offset = startOffset
        
        // Skip version byte (0x80 for v0)
        offset++
        
        // Read message header (3 bytes)
        val numRequiredSignatures = bytes[offset++].toInt() and 0xFF
        val numReadonlySigned = bytes[offset++].toInt() and 0xFF
        val numReadonlyUnsigned = bytes[offset++].toInt() and 0xFF
        
        // Read static account keys
        val (numAccounts, accountLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
        offset += accountLenBytes
        
        val accountKeys = mutableListOf<String>()
        repeat(numAccounts) {
            val pubkeyBytes = bytes.sliceArray(offset until offset + 32)
            accountKeys.add(Base58.encode(pubkeyBytes))
            offset += 32
        }
        
        // Skip recent blockhash (32 bytes)
        offset += 32
        
        // Read instructions
        val (numInstructions, instructionLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
        offset += instructionLenBytes
        
        val intents = mutableListOf<TransactionIntent>()
        
        repeat(numInstructions) { instructionIndex ->
            // Program ID index
            val programIdIndex = bytes[offset++].toInt() and 0xFF
            val programId = accountKeys.getOrNull(programIdIndex) ?: "unknown"
            
            // Account indexes
            val (numAccountIndexes, accountIndexLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
            offset += accountIndexLenBytes
            
            val accounts = mutableListOf<String>()
            repeat(numAccountIndexes) {
                val accountIndex = bytes[offset++].toInt() and 0xFF
                // For versioned transactions, index might refer to lookup table
                // For now, use static accounts or mark as lookup
                accounts.add(accountKeys.getOrNull(accountIndex) ?: "lookup:$accountIndex")
            }
            
            // Instruction data
            val (dataLen, dataLenBytes) = ShortVec.decodeLen(bytes.sliceArray(offset until bytes.size))
            offset += dataLenBytes
            
            val data = bytes.sliceArray(offset until offset + dataLen)
            offset += dataLen
            
            val intent = decodeInstruction(programId, accounts, data, instructionIndex)
            intents.add(intent)
        }
        
        // Note: We skip address lookup tables for now
        // Full implementation would resolve them for complete account list
        
        return analyze(intents).copy(
            warnings = analyze(intents).warnings + 
                if (intents.any { intent -> intent.accounts.any { it.pubkey.startsWith("lookup:") } }) {
                    listOf("Some accounts are from lookup tables and may not be fully resolved")
                } else {
                    emptyList()
                }
        )
    }
    
    /**
     * Decode a legacy transaction into human-readable intents.
     * 
     * The Transaction class stores instructions directly with Pubkey references,
     * so we can decode each instruction without compiling to message format first.
     */
    fun decode(transaction: Transaction): TransactionIntentAnalysis {
        val intents = mutableListOf<TransactionIntent>()
        
        transaction.instructions.forEachIndexed { index, instruction ->
            // Extract program ID and account pubkeys as Base58 strings
            val programId = instruction.programId.toBase58()
            val accounts = instruction.accounts.map { accountMeta ->
                accountMeta.pubkey.toBase58()
            }
            
            val intent = decodeInstruction(programId, accounts, instruction.data, index)
            intents.add(intent)
        }
        
        return analyze(intents)
    }
    
    /**
     * Decode a versioned transaction (v0) into human-readable intents.
     * 
     * VersionedTransaction has a VersionedMessage with accountKeys and compiled instructions.
     * We resolve account indexes from static accountKeys, marking lookup table addresses
     * when they fall outside the static key range.
     */
    fun decode(transaction: VersionedTransaction): TransactionIntentAnalysis {
        val staticAccountKeys = transaction.message.accountKeys.map { it.toBase58() }
        val intents = mutableListOf<TransactionIntent>()
        
        // For v0 transactions, we need to handle address lookup tables
        // For now, decode what we can from static accounts
        transaction.message.instructions.forEachIndexed { index, instruction ->
            val programId = staticAccountKeys.getOrNull(instruction.programIdIndex) ?: return@forEachIndexed
            val accounts = instruction.accountIndexes.map { accountIndex ->
                val idx = accountIndex.toInt() and 0xFF
                staticAccountKeys.getOrNull(idx) ?: "lookup_table:$idx"
            }
            
            val intent = decodeInstruction(programId, accounts, instruction.data, index)
            intents.add(intent)
        }
        
        return analyze(intents)
    }
    
    /**
     * Decode a single instruction.
     */
    fun decodeInstruction(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        // Check if program is known to be suspicious
        if (ProgramRegistry.isSuspicious(programId)) {
            return TransactionIntent(
                instructionIndex = instructionIndex,
                programName = "⚠️ SUSPICIOUS PROGRAM",
                programId = programId,
                method = "unknown",
                summary = "🚨 INTERACTION WITH SUSPICIOUS PROGRAM",
                accounts = accounts.mapIndexed { i, acc ->
                    AccountRole(acc, "Account $i", false, false)
                },
                args = emptyMap(),
                riskLevel = RiskLevel.CRITICAL,
                warnings = listOf(
                    "🚨 This program has been flagged as suspicious",
                    "Do NOT approve this transaction unless you are absolutely certain"
                )
            )
        }
        
        // Try to get a decoder for this program
        val decoder = ProgramRegistry.getDecoder(programId)
        
        return if (decoder != null) {
            decoder.decode(programId, accounts, data, instructionIndex) ?: createUnknownIntent(
                programId = programId,
                accounts = accounts,
                data = data,
                instructionIndex = instructionIndex
            )
        } else {
            createUnknownIntent(
                programId = programId,
                accounts = accounts,
                data = data,
                instructionIndex = instructionIndex
            )
        }
    }
    
    /**
     * Decode raw instruction components (for use with raw RPC data).
     */
    fun decodeRaw(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int = 0
    ): TransactionIntent {
        return decodeInstruction(programId, accounts, data, instructionIndex)
    }
    
    /**
     * Decode multiple instructions into a full analysis.
     */
    fun decodeInstructions(
        instructions: List<RawInstruction>
    ): TransactionIntentAnalysis {
        val intents = instructions.mapIndexed { index, instruction ->
            decodeInstruction(
                instruction.programId,
                instruction.accounts,
                instruction.data,
                index
            )
        }
        return analyze(intents)
    }
    
    private fun analyze(intents: List<TransactionIntent>): TransactionIntentAnalysis {
        // Determine overall risk level (highest among all intents)
        val overallRisk = intents.maxOfOrNull { it.riskLevel } ?: RiskLevel.INFO
        
        // Collect all warnings
        val allWarnings = intents.flatMap { it.warnings }.distinct()
        
        // Generate summary
        val summary = when {
            intents.isEmpty() -> "Empty transaction"
            intents.size == 1 -> intents.first().summary
            else -> {
                val primaryIntents = intents.filter { 
                    it.method !in listOf("setComputeUnitLimit", "setComputeUnitPrice", "requestHeapFrame")
                }
                when {
                    primaryIntents.isEmpty() -> "Configure transaction settings"
                    primaryIntents.size == 1 -> primaryIntents.first().summary
                    else -> "${primaryIntents.size} operations"
                }
            }
        }
        
        // Collect all programs involved
        val programsInvolved = intents.map { it.programName }.distinct()
        
        // Collect all unique accounts
        val allAccounts = intents.flatMap { it.accounts }.distinctBy { it.pubkey }
        
        // Check for common patterns
        val additionalWarnings = mutableListOf<String>()
        
        // Check for unlimited approvals
        if (intents.any { it.method in listOf("approve", "approveChecked") && 
                          (it.args["isUnlimited"] == true || it.args["amount"] == Long.MAX_VALUE) }) {
            additionalWarnings.add("🚨 Contains UNLIMITED token approval")
        }
        
        // Check for authority changes
        if (intents.any { it.method == "setAuthority" }) {
            additionalWarnings.add("⚠️ Modifies token authorities")
        }
        
        // Check for token burns
        if (intents.any { it.method in listOf("burn", "burnChecked") }) {
            additionalWarnings.add("🔥 Burns tokens permanently")
        }
        
        // Check for stake operations
        if (intents.any { it.programName == "Stake" }) {
            additionalWarnings.add("📊 Involves staking operations")
        }
        
        // Check for unknown programs
        val unknownPrograms = intents.count { it.method.startsWith("unknown") }
        if (unknownPrograms > 0) {
            additionalWarnings.add("⚠️ $unknownPrograms unknown program(s) - review carefully")
        }
        
        return TransactionIntentAnalysis(
            summary = summary,
            intents = intents,
            overallRisk = overallRisk,
            warnings = allWarnings + additionalWarnings,
            programsInvolved = programsInvolved,
            accountsInvolved = allAccounts,
            estimatedFee = null // Would need RPC call to estimate
        )
    }
    
    private fun createUnknownIntent(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val programName = ProgramRegistry.getProgramName(programId)
        val isKnown = ProgramRegistry.isKnownProgram(programId)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = programName,
            programId = programId,
            method = "unknown",
            summary = if (isKnown) {
                "Interact with $programName"
            } else {
                "Call unknown program ${programId.take(8)}..."
            },
            accounts = accounts.mapIndexed { i, acc ->
                AccountRole(
                    pubkey = acc,
                    role = "Account $i",
                    isSigner = false,
                    isWritable = false,
                    knownName = if (ProgramRegistry.isKnownProgram(acc)) {
                        ProgramRegistry.getProgramName(acc)
                    } else null
                )
            },
            args = mapOf(
                "dataLength" to data.size,
                "dataHex" to if (data.size <= 64) {
                    data.joinToString("") { "%02x".format(it) }
                } else {
                    "${data.take(32).joinToString("") { "%02x".format(it) }}...${data.takeLast(32).joinToString("") { "%02x".format(it) }}"
                }
            ),
            riskLevel = if (isKnown) RiskLevel.MEDIUM else RiskLevel.HIGH,
            warnings = if (isKnown) {
                listOf("Instruction not fully decoded - review program documentation")
            } else {
                listOf(
                    "⚠️ Unknown program - cannot decode instruction",
                    "Review the program address before approving"
                )
            }
        )
    }
}

/**
 * Raw instruction data for manual decoding.
 */
data class RawInstruction(
    val programId: String,
    val accounts: List<String>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawInstruction) return false
        return programId == other.programId &&
               accounts == other.accounts &&
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
