/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * TransactionValidator - Pre-signing transaction analysis and safety checks.
 * 
 * Addresses Seed Vault Issue #36: Validate incoming transactions/messages
 * 
 * Provides:
 * - Transaction parsing without external dependencies
 * - Known program detection
 * - Malicious pattern heuristics
 * - Clear warnings for users before signing
 * 
 * This is a critical security feature not available in the base Seed Vault SDK.
 */
package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transaction validation results.
 */
sealed class ValidationResult {
    /** Transaction appears safe */
    data class Safe(val summary: TransactionSummary) : ValidationResult()
    
    /** Transaction has warnings but can proceed */
    data class Warning(val summary: TransactionSummary, val warnings: List<TransactionWarning>) : ValidationResult()
    
    /** Transaction is dangerous and should be blocked */
    data class Dangerous(val summary: TransactionSummary, val reasons: List<DangerReason>) : ValidationResult()
    
    /** Could not parse transaction */
    data class ParseError(val message: String) : ValidationResult()
}

/**
 * Summary of parsed transaction.
 */
data class TransactionSummary(
    val numInstructions: Int,
    val programs: List<ProgramInfo>,
    val estimatedFee: Long?,
    val isVersioned: Boolean,
    val requiresSigners: Int
)

/**
 * Information about a program in the transaction.
 */
data class ProgramInfo(
    val programId: Pubkey,
    val name: String?,
    val isKnown: Boolean,
    val instructionCount: Int
)

/**
 * Warning about potential issues.
 */
data class TransactionWarning(
    val level: WarningLevel,
    val message: String,
    val programId: Pubkey?
)

enum class WarningLevel {
    INFO,
    CAUTION,
    HIGH_RISK
}

/**
 * Reasons why a transaction is considered dangerous.
 */
sealed class DangerReason {
    data class KnownScamProgram(val programId: Pubkey) : DangerReason()
    data class UnauthorizedTokenApproval(val mint: Pubkey, val spender: Pubkey) : DangerReason()
    data class ExcessiveTokenTransfer(val amount: Long, val mint: Pubkey) : DangerReason()
    data class SuspiciousPattern(val description: String) : DangerReason()
    data object DrainerSignature : DangerReason()
    /** Transaction references an unknown/unverified program. */
    data object UNKNOWN_PROGRAM : DangerReason()
    /** Transaction has an empty program list. */
    data object EMPTY_TRANSACTION : DangerReason()
}

/**
 * High-level transaction category.
 */
enum class TransactionCategory {
    TRANSFER,
    SWAP,
    NFT,
    DEFI,
    GOVERNANCE,
    STAKE,
    UNKNOWN
}

/**
 * Result of program-level validation (simplified API).
 */
data class ProgramValidationResult(
    val isSafe: Boolean,
    val programs: List<ProgramDetail>,
    val dangerReasons: List<DangerReason>
)

/**
 * Details about a program in a transaction (for simplified API).
 */
data class ProgramDetail(
    val id: String,
    val name: String,
    val isKnown: Boolean
)

/**
 * Transaction summary with category (for simplified API).
 */
data class TransactionSummaryResult(
    val category: TransactionCategory,
    val requiresExtraScrutiny: Boolean,
    val warnings: List<String>
)

/**
 * Compute budget analysis result.
 */
data class ComputeBudgetAnalysis(
    val computeUnits: Long,
    val priorityFee: Long,
    val isHighRisk: Boolean,
    val reason: String? = null
)

/**
 * Drainer pattern check result.
 */
data class DrainerCheckResult(
    val isPotentialDrainer: Boolean,
    val reasons: List<String>
)

/**
 * Transaction validator that analyzes transactions before signing.
 * 
 * Usage:
 * ```kotlin
 * val validator = TransactionValidator()
 * 
 * when (val result = validator.validate(messageBytes)) {
 *     is ValidationResult.Safe -> proceed()
 *     is ValidationResult.Warning -> showWarningsAndProceed(result.warnings)
 *     is ValidationResult.Dangerous -> blockTransaction(result.reasons)
 *     is ValidationResult.ParseError -> showError(result.message)
 * }
 * ```
 */
class TransactionValidator {
    
    companion object {
        // Known system programs
        val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
        val TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val TOKEN_2022_PROGRAM = Pubkey.fromBase58("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
        val ASSOCIATED_TOKEN_PROGRAM = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
        val MEMO_PROGRAM = Pubkey.fromBase58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
        val COMPUTE_BUDGET = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")
        
        // Known DeFi programs
        val JUPITER_V6 = Pubkey.fromBase58("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4")
        val RAYDIUM_AMM = Pubkey.fromBase58("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8")
        val MARINADE_FINANCE = Pubkey.fromBase58("MarBmsSgKXdrN1egZf5sqe1TMai9K1rChYNDJgjq7aD")
        
        // Program names
        private val PROGRAM_NAMES = mapOf(
            SYSTEM_PROGRAM to "System Program",
            TOKEN_PROGRAM to "Token Program",
            TOKEN_2022_PROGRAM to "Token-2022",
            ASSOCIATED_TOKEN_PROGRAM to "Associated Token",
            MEMO_PROGRAM to "Memo",
            COMPUTE_BUDGET to "Compute Budget",
            JUPITER_V6 to "Jupiter",
            RAYDIUM_AMM to "Raydium",
            MARINADE_FINANCE to "Marinade"
        )
        
        // Known scam program patterns (example addresses - real list would be larger)
        private val KNOWN_SCAM_PROGRAMS = setOf<String>(
            // These would be populated from a blocklist
        )
    }
    
    /**
     * Validate a transaction message before signing.
     */
    fun validate(messageBytes: ByteArray): ValidationResult {
        return try {
            val parsed = parseMessage(messageBytes)
            analyzeTransaction(parsed)
        } catch (e: Exception) {
            ValidationResult.ParseError("Failed to parse transaction: ${e.message}")
        }
    }
    
    /**
     * Parse a transaction message (legacy or versioned).
     */
    fun parseMessage(messageBytes: ByteArray): ParsedMessage {
        val buffer = ByteBuffer.wrap(messageBytes).order(ByteOrder.LITTLE_ENDIAN)
        
        // Check for versioned transaction (first byte has high bit set)
        val firstByte = buffer.get().toInt() and 0xFF
        val isVersioned = (firstByte and 0x80) != 0
        
        if (isVersioned) {
            return parseVersionedMessage(messageBytes)
        } else {
            buffer.position(0)
            return parseLegacyMessage(buffer)
        }
    }
    
    private fun parseLegacyMessage(buffer: ByteBuffer): ParsedMessage {
        // Header
        val numRequiredSignatures = buffer.get().toInt() and 0xFF
        val numReadonlySignedAccounts = buffer.get().toInt() and 0xFF
        val numReadonlyUnsignedAccounts = buffer.get().toInt() and 0xFF
        
        // Account keys
        val numAccounts = readCompactU16(buffer)
        val accounts = (0 until numAccounts).map {
            val keyBytes = ByteArray(32)
            buffer.get(keyBytes)
            Pubkey(keyBytes)
        }
        
        // Recent blockhash
        val blockhash = ByteArray(32)
        buffer.get(blockhash)
        
        // Instructions
        val numInstructions = readCompactU16(buffer)
        val instructions = (0 until numInstructions).map {
            parseInstruction(buffer, accounts)
        }
        
        return ParsedMessage(
            isVersioned = false,
            numRequiredSignatures = numRequiredSignatures,
            accounts = accounts,
            instructions = instructions
        )
    }
    
    private fun parseVersionedMessage(messageBytes: ByteArray): ParsedMessage {
        val buffer = ByteBuffer.wrap(messageBytes).order(ByteOrder.LITTLE_ENDIAN)
        
        // Version prefix
        val prefix = buffer.get().toInt() and 0xFF
        val version = prefix and 0x7F
        
        // Header
        val numRequiredSignatures = buffer.get().toInt() and 0xFF
        val numReadonlySignedAccounts = buffer.get().toInt() and 0xFF
        val numReadonlyUnsignedAccounts = buffer.get().toInt() and 0xFF
        
        // Static account keys
        val numStaticAccounts = readCompactU16(buffer)
        val accounts = (0 until numStaticAccounts).map {
            val keyBytes = ByteArray(32)
            buffer.get(keyBytes)
            Pubkey(keyBytes)
        }.toMutableList()
        
        // Recent blockhash
        val blockhash = ByteArray(32)
        buffer.get(blockhash)
        
        // Instructions
        val numInstructions = readCompactU16(buffer)
        val instructions = (0 until numInstructions).map {
            parseInstruction(buffer, accounts)
        }
        
        // Address lookup tables (skip for now, just note they exist)
        val numLookups = readCompactU16(buffer)
        
        return ParsedMessage(
            isVersioned = true,
            numRequiredSignatures = numRequiredSignatures,
            accounts = accounts,
            instructions = instructions,
            hasLookupTables = numLookups > 0
        )
    }
    
    private fun parseInstruction(buffer: ByteBuffer, accounts: List<Pubkey>): ParsedInstruction {
        val programIdIndex = buffer.get().toInt() and 0xFF
        
        val numAccountIndexes = readCompactU16(buffer)
        val accountIndexes = (0 until numAccountIndexes).map {
            buffer.get().toInt() and 0xFF
        }
        
        val dataLen = readCompactU16(buffer)
        val data = ByteArray(dataLen)
        buffer.get(data)
        
        return ParsedInstruction(
            programId = accounts.getOrNull(programIdIndex) ?: Pubkey(ByteArray(32)),
            accountIndexes = accountIndexes,
            data = data
        )
    }
    
    private fun readCompactU16(buffer: ByteBuffer): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = buffer.get().toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIMPLIFIED API - Program-level validation without raw bytes
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a list of program IDs (simplified API without raw transaction bytes).
     * 
     * @param programs List of program ID strings (base58)
     * @return Validation result with safety assessment
     */
    fun validatePrograms(programs: List<String>): ProgramValidationResult {
        if (programs.isEmpty()) {
            return ProgramValidationResult(
                isSafe = false,
                programs = emptyList(),
                dangerReasons = listOf(DangerReason.EMPTY_TRANSACTION)
            )
        }
        
        val details = programs.map { programId ->
            val pubkey = try { Pubkey.fromBase58(programId) } catch (e: Exception) { null }
            val name = pubkey?.let { PROGRAM_NAMES[it] } ?: "Unknown"
            val isKnown = pubkey != null && pubkey in PROGRAM_NAMES
            ProgramDetail(
                id = programId,
                name = name,
                isKnown = isKnown
            )
        }
        
        val dangers = mutableListOf<DangerReason>()
        val hasUnknown = details.any { !it.isKnown }
        if (hasUnknown) {
            dangers.add(DangerReason.UNKNOWN_PROGRAM)
        }
        
        return ProgramValidationResult(
            isSafe = dangers.isEmpty(),
            programs = details,
            dangerReasons = dangers
        )
    }
    
    /**
     * Categorize and summarize a transaction by its programs (simplified API).
     */
    fun summarizeTransaction(programs: List<String>): TransactionSummaryResult {
        val warnings = mutableListOf<String>()
        var requiresExtraScrutiny = false
        
        val knownProgramIds = programs.mapNotNull { id ->
            try { Pubkey.fromBase58(id) } catch (e: Exception) { null }
        }
        
        // Check for unknown programs
        val unknownCount = programs.count { id ->
            val pubkey = try { Pubkey.fromBase58(id) } catch (e: Exception) { null }
            pubkey == null || pubkey !in PROGRAM_NAMES
        }
        if (unknownCount > 0) {
            warnings.add("Transaction references $unknownCount unknown program(s)")
            requiresExtraScrutiny = true
        }
        
        // Many programs = suspicious
        if (programs.size > 10) {
            warnings.add("Unusually high number of programs (${programs.size})")
            requiresExtraScrutiny = true
        }
        
        // Categorize
        val category = categorizeTransaction(knownProgramIds)
        
        return TransactionSummaryResult(
            category = category,
            requiresExtraScrutiny = requiresExtraScrutiny,
            warnings = warnings
        )
    }
    
    /**
     * Analyze compute budget parameters for risk assessment.
     */
    fun analyzeComputeBudget(computeUnits: Long, priorityFee: Long): ComputeBudgetAnalysis {
        val isHighRisk = computeUnits >= 1_400_000 && priorityFee == 0L
        val reason = if (isHighRisk) "Max compute budget with zero priority fee" else null
        
        return ComputeBudgetAnalysis(
            computeUnits = computeUnits,
            priorityFee = priorityFee,
            isHighRisk = isHighRisk,
            reason = reason
        )
    }
    
    /**
     * Check for common drainer/scam patterns in token operations.
     */
    fun checkDrainerPattern(
        programId: String,
        instruction: String,
        delegateAddress: String,
        amount: Long
    ): DrainerCheckResult {
        val reasons = mutableListOf<String>()
        
        // Check for unlimited approvals
        if (instruction == "approve" && (amount == Long.MAX_VALUE || amount < 0)) {
            reasons.add("Unlimited token approval detected")
        }
        
        // Check if delegate is a known program (legitimate)
        val delegatePubkey = try { Pubkey.fromBase58(delegateAddress) } catch (e: Exception) { null }
        val isKnownDelegate = delegatePubkey != null && delegatePubkey in PROGRAM_NAMES
        
        if (instruction == "approve" && !isKnownDelegate && amount > 0) {
            reasons.add("Token approval to unknown address")
        }
        
        return DrainerCheckResult(
            isPotentialDrainer = reasons.isNotEmpty(),
            reasons = reasons
        )
    }
    
    private fun categorizeTransaction(programIds: List<Pubkey>): TransactionCategory {
        val hasJupiter = JUPITER_V6 in programIds
        val hasRaydium = RAYDIUM_AMM in programIds
        val hasMetaplex = programIds.any { it.toBase58().startsWith("metaqbxx") }
        val hasSystemOnly = programIds.all { it == SYSTEM_PROGRAM || it == COMPUTE_BUDGET }
        val hasTokenProgram = TOKEN_PROGRAM in programIds || TOKEN_2022_PROGRAM in programIds
        
        return when {
            hasJupiter || hasRaydium -> TransactionCategory.SWAP
            hasMetaplex -> TransactionCategory.NFT
            hasSystemOnly -> TransactionCategory.TRANSFER
            hasTokenProgram && !hasJupiter && !hasRaydium -> TransactionCategory.TRANSFER
            else -> TransactionCategory.UNKNOWN
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun analyzeTransaction(parsed: ParsedMessage): ValidationResult {
        val warnings = mutableListOf<TransactionWarning>()
        val dangers = mutableListOf<DangerReason>()
        
        // Analyze each program
        val programInfos = mutableMapOf<Pubkey, Int>()
        for (ix in parsed.instructions) {
            programInfos[ix.programId] = (programInfos[ix.programId] ?: 0) + 1
            
            // Check for known scams
            if (ix.programId.toBase58() in KNOWN_SCAM_PROGRAMS) {
                dangers.add(DangerReason.KnownScamProgram(ix.programId))
            }
            
            // Check for suspicious patterns
            analyzeInstruction(ix, parsed.accounts, warnings, dangers)
        }
        
        val programs = programInfos.map { (programId, count) ->
            ProgramInfo(
                programId = programId,
                name = PROGRAM_NAMES[programId],
                isKnown = programId in PROGRAM_NAMES,
                instructionCount = count
            )
        }
        
        // Warn about unknown programs
        val unknownPrograms = programs.filter { !it.isKnown }
        if (unknownPrograms.isNotEmpty()) {
            for (prog in unknownPrograms) {
                warnings.add(TransactionWarning(
                    level = WarningLevel.CAUTION,
                    message = "Unknown program: ${prog.programId.toBase58().take(8)}...",
                    programId = prog.programId
                ))
            }
        }
        
        val summary = TransactionSummary(
            numInstructions = parsed.instructions.size,
            programs = programs,
            estimatedFee = null, // Would need RPC to estimate
            isVersioned = parsed.isVersioned,
            requiresSigners = parsed.numRequiredSignatures
        )
        
        return when {
            dangers.isNotEmpty() -> ValidationResult.Dangerous(summary, dangers)
            warnings.isNotEmpty() -> ValidationResult.Warning(summary, warnings)
            else -> ValidationResult.Safe(summary)
        }
    }
    
    private fun analyzeInstruction(
        ix: ParsedInstruction,
        accounts: List<Pubkey>,
        warnings: MutableList<TransactionWarning>,
        dangers: MutableList<DangerReason>
    ) {
        // Token program checks
        if (ix.programId == TOKEN_PROGRAM || ix.programId == TOKEN_2022_PROGRAM) {
            analyzeTokenInstruction(ix, accounts, warnings, dangers)
        }
    }
    
    private fun analyzeTokenInstruction(
        ix: ParsedInstruction,
        accounts: List<Pubkey>,
        warnings: MutableList<TransactionWarning>,
        dangers: MutableList<DangerReason>
    ) {
        if (ix.data.isEmpty()) return
        
        val discriminator = ix.data[0].toInt() and 0xFF
        
        // Approve (discriminator 4) - delegating tokens to another account
        if (discriminator == 4 && ix.data.size >= 9) {
            val amount = ByteBuffer.wrap(ix.data, 1, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val delegate = accounts.getOrNull(ix.accountIndexes.getOrNull(2) ?: -1)
            
            if (amount == Long.MAX_VALUE || amount == -1L) {
                // Unlimited approval - very dangerous
                dangers.add(DangerReason.SuspiciousPattern("Unlimited token approval detected"))
            } else if (amount > 1_000_000_000_000) { // More than 1M tokens (assuming 6 decimals)
                warnings.add(TransactionWarning(
                    level = WarningLevel.HIGH_RISK,
                    message = "Large token approval: $amount",
                    programId = ix.programId
                ))
            }
        }
        
        // Transfer (discriminator 3)
        if (discriminator == 3 && ix.data.size >= 9) {
            val amount = ByteBuffer.wrap(ix.data, 1, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (amount > 1_000_000_000_000_000) { // Extremely large transfer
                warnings.add(TransactionWarning(
                    level = WarningLevel.CAUTION,
                    message = "Large token transfer: $amount",
                    programId = ix.programId
                ))
            }
        }
    }
}

/**
 * Parsed transaction message.
 */
data class ParsedMessage(
    val isVersioned: Boolean,
    val numRequiredSignatures: Int,
    val accounts: List<Pubkey>,
    val instructions: List<ParsedInstruction>,
    val hasLookupTables: Boolean = false
)

/**
 * Parsed instruction.
 */
data class ParsedInstruction(
    val programId: Pubkey,
    val accountIndexes: List<Int>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedInstruction) return false
        return programId == other.programId && 
               accountIndexes == other.accountIndexes && 
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accountIndexes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
