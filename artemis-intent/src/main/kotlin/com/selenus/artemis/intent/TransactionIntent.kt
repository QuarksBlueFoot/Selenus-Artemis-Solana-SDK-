package com.selenus.artemis.intent

/**
 * Human-readable transaction intent.
 * 
 * Artemis Innovation: First Solana SDK to provide semantic transaction decoding.
 * No other SDK (solana-kmp, Sol4k, Solana Mobile) translates raw instructions
 * into human-understandable summaries.
 * 
 * This enables:
 * - User-friendly transaction approval dialogs
 * - Automated transaction categorization
 * - Risk assessment before signing
 * - Audit trails with semantic meaning
 * 
 * Example:
 * ```kotlin
 * val intents = TransactionIntentDecoder.decode(transaction)
 * intents.forEach { intent ->
 *     println(intent.summary) // "Transfer 2.5 SOL to 7xKXt..."
 *     println(intent.riskLevel) // LOW
 * }
 * ```
 */
data class TransactionIntent(
    /** Raw instruction index in the transaction */
    val instructionIndex: Int,
    
    /** The program executing this instruction */
    val programName: String,
    
    /** The program's public key (Base58 string) */
    val programId: String,
    
    /** The specific method/instruction being called */
    val method: String,
    
    /** Human-readable summary of the action */
    val summary: String,
    
    /** Accounts involved in this instruction */
    val accounts: List<AccountRole>,
    
    /** Decoded arguments with their values */
    val args: Map<String, Any?>,
    
    /** Risk assessment for this instruction */
    val riskLevel: RiskLevel,
    
    /** Warnings or notes for the user */
    val warnings: List<String> = emptyList(),
    
    /** Whether this instruction could not be fully decoded */
    val isPartialDecode: Boolean = false
)

/**
 * Account role in an instruction.
 */
data class AccountRole(
    /** Public key (Base58 string) */
    val pubkey: String,
    
    /** Role description, e.g., "source", "destination", "authority" */
    val role: String,
    
    /** Whether this account is a signer */
    val isSigner: Boolean,
    
    /** Whether this account is writable */
    val isWritable: Boolean,
    
    /** Known name for this address, if any (e.g., "System Program", "Token Program") */
    val knownName: String? = null
)

/**
 * Risk levels for transaction instructions.
 */
enum class RiskLevel(val displayName: String, val emoji: String) : Comparable<RiskLevel> {
    /** Safe, read-only or informational */
    INFO("Informational", "ℹ️"),
    
    /** Standard operations, minimal risk */
    LOW("Low Risk", "✅"),
    
    /** Operations that transfer value or modify state */
    MEDIUM("Medium Risk", "⚠️"),
    
    /** Operations that could result in significant loss */
    HIGH("High Risk", "🔴"),
    
    /** Potentially dangerous operations - requires extra caution */
    CRITICAL("Critical - Review Carefully", "🚨")
}

/**
 * Complete transaction intent analysis result.
 */
data class TransactionIntentAnalysis(
    /** Combined human-readable summary */
    val summary: String,
    
    /** All decoded intents from the transaction */
    val intents: List<TransactionIntent>,
    
    /** Overall risk level (highest of all intents) */
    val overallRisk: RiskLevel,
    
    /** Warnings aggregated from all intents */
    val warnings: List<String>,
    
    /** Programs involved */
    val programsInvolved: List<String>,
    
    /** All accounts involved (deduplicated) */
    val accountsInvolved: List<AccountRole>,
    
    /** Estimated transaction fee in lamports */
    val estimatedFee: Long? = null,
    
    /** Total estimated SOL cost (transfers + fees) */
    val estimatedSolCost: Long? = null,
    
    /** Whether all instructions were fully decoded */
    val fullyDecoded: Boolean = true,
    
    /** Count of signers required */
    val signersRequired: Int = 1
) {
    companion object {
        fun fromIntents(
            intents: List<TransactionIntent>,
            signersRequired: Int = 1
        ): TransactionIntentAnalysis {
            val overallRisk = intents.maxOfOrNull { it.riskLevel } ?: RiskLevel.INFO
            val programs = intents.map { it.programName }.distinct()
            val accounts = intents.flatMap { it.accounts }.distinctBy { it.pubkey }
            val fullyDecoded = intents.none { it.isPartialDecode }
            val warnings = intents.flatMap { it.warnings }.distinct()
            
            val summary = when {
                intents.isEmpty() -> "Empty transaction"
                intents.size == 1 -> intents[0].summary
                else -> {
                    // Filter out compute budget instructions for primary summary
                    val primaryIntents = intents.filter { 
                        it.programName != "Compute Budget" 
                    }
                    when {
                        primaryIntents.isEmpty() -> "Configure transaction settings"
                        primaryIntents.size == 1 -> primaryIntents[0].summary
                        else -> "${primaryIntents.size} operations"
                    }
                }
            }
            
            // Estimate SOL cost from transfer intents
            val solCost = intents
                .filter { it.programName == "System Program" && it.method == "transfer" }
                .mapNotNull { it.args["lamports"] as? Long }
                .sum()
                .takeIf { it > 0 }
            
            return TransactionIntentAnalysis(
                summary = summary,
                intents = intents,
                overallRisk = overallRisk,
                warnings = warnings,
                programsInvolved = programs,
                accountsInvolved = accounts,
                estimatedSolCost = solCost,
                fullyDecoded = fullyDecoded,
                signersRequired = signersRequired
            )
        }
    }
}
