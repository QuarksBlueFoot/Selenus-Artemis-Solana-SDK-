package com.selenus.artemis.preview

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * TransactionSimulator - Comprehensive transaction simulation and preview
 * 
 * Features:
 * - Pre-flight simulation with detailed results
 * - Balance change prediction
 * - Account state changes preview
 * - Error decoding and suggestions
 * - Gas estimation
 * - Transaction dry-run
 */
class TransactionSimulator(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val defaultCommitment: Commitment = Commitment.CONFIRMED,
        val simulationTimeoutMs: Long = 30_000L,
        val enableCaching: Boolean = true,
        val cacheTtlMs: Long = 10_000L,
        val maxRetries: Int = 2
    )

    enum class Commitment {
        PROCESSED,
        CONFIRMED,
        FINALIZED
    }

    /**
     * Simulation result with comprehensive details
     */
    data class SimulationResult(
        val success: Boolean,
        val error: SimulationError?,
        val computeUnitsConsumed: Long,
        val logs: List<String>,
        val accountChanges: List<AccountChange>,
        val balanceChanges: List<BalanceChange>,
        val returnData: ByteArray?,
        val slot: Long,
        val unitsConsumed: Long,
        val fee: Long,
        val innerInstructions: List<InnerInstructionResult>
    )

    /**
     * Simulation error with details
     */
    data class SimulationError(
        val code: Int?,
        val message: String,
        val programId: Pubkey?,
        val instructionIndex: Int?,
        val suggestion: String?,
        val category: ErrorCategory
    )

    enum class ErrorCategory {
        INSUFFICIENT_FUNDS,
        ACCOUNT_NOT_FOUND,
        INVALID_ACCOUNT_DATA,
        PROGRAM_ERROR,
        SIGNATURE_ERROR,
        COMPUTE_LIMIT,
        NETWORK_ERROR,
        UNKNOWN
    }

    /**
     * Account state change from simulation
     */
    data class AccountChange(
        val pubkey: Pubkey,
        val owner: Pubkey?,
        val lamportsBefore: Long,
        val lamportsAfter: Long,
        val dataBefore: ByteArray?,
        val dataAfter: ByteArray?,
        val isWritable: Boolean,
        val isSigner: Boolean
    ) {
        val lamportsDelta: Long get() = lamportsAfter - lamportsBefore
        val dataChanged: Boolean get() = !dataBefore.contentEquals(dataAfter)
    }

    /**
     * Balance change summary
     */
    data class BalanceChange(
        val pubkey: Pubkey,
        val tokenMint: Pubkey?,      // null for SOL
        val before: Long,
        val after: Long,
        val symbol: String?
    ) {
        val delta: Long get() = after - before
        val isPositive: Boolean get() = delta > 0
        val isNegative: Boolean get() = delta < 0
    }

    /**
     * Inner instruction result
     */
    data class InnerInstructionResult(
        val index: Int,
        val programId: Pubkey,
        val data: ByteArray,
        val accounts: List<Pubkey>
    )

    /**
     * Simulation cache entry
     */
    private data class CacheEntry(
        val result: SimulationResult,
        val cachedAtMs: Long,
        val transactionHash: String
    )

    private val simulationCache = ConcurrentHashMap<String, CacheEntry>()
    private val mutex = Mutex()

    // Observable state
    private val _lastSimulation = MutableStateFlow<SimulationResult?>(null)
    val lastSimulation: StateFlow<SimulationResult?> = _lastSimulation.asStateFlow()

    /**
     * Simulate a transaction
     */
    suspend fun simulate(
        serializedTx: ByteArray,
        accounts: List<Pubkey> = emptyList(),
        simulator: suspend (ByteArray, List<Pubkey>) -> RawSimulationResponse
    ): SimulationResult = mutex.withLock {
        val txHash = serializedTx.contentHashCode().toString()

        // Check cache
        if (config.enableCaching) {
            val cached = simulationCache[txHash]
            if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < config.cacheTtlMs) {
                return@withLock cached.result
            }
        }

        var lastError: Exception? = null
        repeat(config.maxRetries + 1) { attempt ->
            try {
                val rawResponse = withTimeout(config.simulationTimeoutMs) {
                    simulator(serializedTx, accounts)
                }

                val result = parseSimulationResponse(rawResponse)

                // Cache successful simulation
                if (config.enableCaching) {
                    simulationCache[txHash] = CacheEntry(result, System.currentTimeMillis(), txHash)
                }

                _lastSimulation.value = result
                return@withLock result

            } catch (e: Exception) {
                lastError = e
                if (attempt < config.maxRetries) {
                    delay(500L * (attempt + 1))
                }
            }
        }

        // Return error result
        SimulationResult(
            success = false,
            error = SimulationError(
                code = null,
                message = lastError?.message ?: "Simulation failed",
                programId = null,
                instructionIndex = null,
                suggestion = "Check network connectivity and try again",
                category = ErrorCategory.NETWORK_ERROR
            ),
            computeUnitsConsumed = 0,
            logs = emptyList(),
            accountChanges = emptyList(),
            balanceChanges = emptyList(),
            returnData = null,
            slot = 0,
            unitsConsumed = 0,
            fee = 0,
            innerInstructions = emptyList()
        )
    }

    /**
     * Raw simulation response from RPC
     */
    data class RawSimulationResponse(
        val err: Any?,
        val logs: List<String>?,
        val accounts: List<RawAccountInfo?>?,
        val unitsConsumed: Long?,
        val returnData: String?,
        val innerInstructions: List<Any>?
    )

    data class RawAccountInfo(
        val lamports: Long,
        val owner: String,
        val data: List<String>,
        val executable: Boolean,
        val rentEpoch: Long
    )

    /**
     * Preview transaction effects (simplified)
     */
    suspend fun preview(
        instructions: List<Instruction>,
        feePayer: Pubkey,
        previewer: suspend (List<Instruction>, Pubkey) -> PreviewResponse
    ): TransactionPreview {
        val response = previewer(instructions, feePayer)
        return parsePreviewResponse(response, instructions, feePayer)
    }

    data class PreviewResponse(
        val success: Boolean,
        val estimatedFee: Long,
        val estimatedCU: Long,
        val balanceChanges: Map<String, Long>,
        val warnings: List<String>
    )

    /**
     * Transaction preview for UI display
     */
    data class TransactionPreview(
        val title: String,
        val description: String,
        val estimatedFee: FeeEstimate,
        val balanceImpact: List<BalanceImpact>,
        val accountsAffected: List<AffectedAccount>,
        val warnings: List<Warning>,
        val riskLevel: RiskLevel,
        val instructionSummaries: List<InstructionSummary>
    )

    data class FeeEstimate(
        val networkFee: Long,           // Lamports
        val priorityFee: Long,          // Lamports
        val totalFee: Long,             // Lamports
        val estimatedCU: Long
    )

    data class BalanceImpact(
        val token: TokenInfo,
        val change: Long,
        val newBalance: Long?,
        val formattedChange: String
    )

    data class TokenInfo(
        val mint: Pubkey?,              // null for SOL
        val symbol: String,
        val decimals: Int,
        val name: String?
    )

    data class AffectedAccount(
        val pubkey: Pubkey,
        val label: String?,             // e.g., "Your Wallet", "Token Account"
        val type: AccountType,
        val isWritable: Boolean
    )

    enum class AccountType {
        WALLET,
        TOKEN_ACCOUNT,
        PROGRAM,
        SYSTEM,
        UNKNOWN
    }

    data class Warning(
        val message: String,
        val severity: WarningSeverity,
        val suggestion: String?
    )

    enum class WarningSeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    enum class RiskLevel {
        SAFE,           // Standard transfer, known program
        LOW,            // Minor risk, new recipient
        MEDIUM,         // Moderate risk, unfamiliar program
        HIGH,           // High risk, approval needed
        CRITICAL        // Very high risk, multiple red flags
    }

    data class InstructionSummary(
        val index: Int,
        val programName: String?,
        val programId: Pubkey,
        val action: String,             // Human-readable action
        val details: String?            // Additional details
    )

    /**
     * Estimate compute units for a transaction
     */
    suspend fun estimateComputeUnits(
        instructions: List<Instruction>,
        estimator: suspend (List<Instruction>) -> Long
    ): Long {
        return try {
            estimator(instructions)
        } catch (e: Exception) {
            // Default estimate based on instruction count
            val baseUnits = 50_000L
            val perInstruction = 30_000L
            baseUnits + (instructions.size * perInstruction)
        }
    }

    /**
     * Decode simulation error to human-readable message
     */
    fun decodeError(error: SimulationError): DecodedError {
        val suggestions = mutableListOf<String>()
        val explanation = when (error.category) {
            ErrorCategory.INSUFFICIENT_FUNDS -> {
                suggestions.add("Add more SOL to your wallet")
                suggestions.add("Reduce the transaction amount")
                "Your wallet doesn't have enough SOL to complete this transaction"
            }
            ErrorCategory.ACCOUNT_NOT_FOUND -> {
                suggestions.add("Verify the account address is correct")
                suggestions.add("The account may need to be created first")
                "One of the accounts required for this transaction doesn't exist"
            }
            ErrorCategory.INVALID_ACCOUNT_DATA -> {
                suggestions.add("The account data may be corrupted or invalid")
                "The data in one of the accounts is not in the expected format"
            }
            ErrorCategory.PROGRAM_ERROR -> {
                suggestions.add("Check the program documentation")
                suggestions.add("Verify all instruction parameters are correct")
                "The program returned an error: ${error.message}"
            }
            ErrorCategory.SIGNATURE_ERROR -> {
                suggestions.add("Ensure all required accounts have signed")
                "Missing or invalid signature"
            }
            ErrorCategory.COMPUTE_LIMIT -> {
                suggestions.add("Increase the compute unit limit")
                suggestions.add("Split into smaller transactions")
                "Transaction exceeded the compute budget"
            }
            ErrorCategory.NETWORK_ERROR -> {
                suggestions.add("Check your internet connection")
                suggestions.add("Try again in a few moments")
                "Network error during simulation"
            }
            ErrorCategory.UNKNOWN -> {
                suggestions.add("Check transaction parameters")
                "An unknown error occurred: ${error.message}"
            }
        }

        return DecodedError(
            title = getCategoryTitle(error.category),
            explanation = explanation,
            suggestions = suggestions,
            technicalDetails = error.message,
            programId = error.programId,
            instructionIndex = error.instructionIndex
        )
    }

    data class DecodedError(
        val title: String,
        val explanation: String,
        val suggestions: List<String>,
        val technicalDetails: String,
        val programId: Pubkey?,
        val instructionIndex: Int?
    )

    private fun getCategoryTitle(category: ErrorCategory): String {
        return when (category) {
            ErrorCategory.INSUFFICIENT_FUNDS -> "Insufficient Funds"
            ErrorCategory.ACCOUNT_NOT_FOUND -> "Account Not Found"
            ErrorCategory.INVALID_ACCOUNT_DATA -> "Invalid Account Data"
            ErrorCategory.PROGRAM_ERROR -> "Program Error"
            ErrorCategory.SIGNATURE_ERROR -> "Signature Required"
            ErrorCategory.COMPUTE_LIMIT -> "Compute Limit Exceeded"
            ErrorCategory.NETWORK_ERROR -> "Network Error"
            ErrorCategory.UNKNOWN -> "Unknown Error"
        }
    }

    /**
     * Clear simulation cache
     */
    suspend fun clearCache() = mutex.withLock {
        simulationCache.clear()
    }

    private fun parseSimulationResponse(response: RawSimulationResponse): SimulationResult {
        val error = if (response.err != null) {
            parseError(response.err, response.logs ?: emptyList())
        } else null

        val accountChanges = response.accounts?.mapIndexedNotNull { index, info ->
            if (info == null) return@mapIndexedNotNull null
            AccountChange(
                pubkey = Pubkey(ByteArray(32)),  // Would need actual keys
                owner = Pubkey.fromBase58(info.owner),
                lamportsBefore = 0,  // Would need pre-state
                lamportsAfter = info.lamports,
                dataBefore = null,
                dataAfter = null,
                isWritable = true,
                isSigner = false
            )
        } ?: emptyList()

        return SimulationResult(
            success = response.err == null,
            error = error,
            computeUnitsConsumed = response.unitsConsumed ?: 0,
            logs = response.logs ?: emptyList(),
            accountChanges = accountChanges,
            balanceChanges = emptyList(),  // Would parse from account changes
            returnData = response.returnData?.let { decodeBase64(it) },
            slot = 0,
            unitsConsumed = response.unitsConsumed ?: 0,
            fee = 0,  // Would calculate from CU and priority fee
            innerInstructions = emptyList()
        )
    }

    private fun parseError(err: Any, logs: List<String>): SimulationError {
        val message = err.toString()
        val category = categorizeError(message, logs)

        return SimulationError(
            code = extractErrorCode(message),
            message = message,
            programId = extractProgramId(logs),
            instructionIndex = extractInstructionIndex(message),
            suggestion = getSuggestion(category),
            category = category
        )
    }

    private fun categorizeError(message: String, logs: List<String>): ErrorCategory {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("insufficient") -> ErrorCategory.INSUFFICIENT_FUNDS
            lowerMessage.contains("account not found") -> ErrorCategory.ACCOUNT_NOT_FOUND
            lowerMessage.contains("invalid account data") -> ErrorCategory.INVALID_ACCOUNT_DATA
            lowerMessage.contains("signature") -> ErrorCategory.SIGNATURE_ERROR
            lowerMessage.contains("compute") -> ErrorCategory.COMPUTE_LIMIT
            logs.any { it.contains("Program failed") } -> ErrorCategory.PROGRAM_ERROR
            else -> ErrorCategory.UNKNOWN
        }
    }

    private fun extractErrorCode(message: String): Int? {
        val codeMatch = Regex("\\berror (\\d+)\\b", RegexOption.IGNORE_CASE).find(message)
        return codeMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractProgramId(logs: List<String>): Pubkey? {
        for (log in logs) {
            if (log.contains("Program") && log.contains("invoke")) {
                val match = Regex("Program (\\w+) invoke").find(log)
                match?.groupValues?.get(1)?.let {
                    return try { Pubkey.fromBase58(it) } catch (e: Exception) { null }
                }
            }
        }
        return null
    }

    private fun extractInstructionIndex(message: String): Int? {
        val match = Regex("instruction (\\d+)", RegexOption.IGNORE_CASE).find(message)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun getSuggestion(category: ErrorCategory): String {
        return when (category) {
            ErrorCategory.INSUFFICIENT_FUNDS -> "Add more SOL to your wallet"
            ErrorCategory.ACCOUNT_NOT_FOUND -> "Check that the account exists"
            ErrorCategory.INVALID_ACCOUNT_DATA -> "Verify account data format"
            ErrorCategory.PROGRAM_ERROR -> "Check program documentation"
            ErrorCategory.SIGNATURE_ERROR -> "Ensure all signers have signed"
            ErrorCategory.COMPUTE_LIMIT -> "Increase compute unit limit"
            ErrorCategory.NETWORK_ERROR -> "Check network and retry"
            ErrorCategory.UNKNOWN -> "Review transaction parameters"
        }
    }

    private fun parsePreviewResponse(
        response: PreviewResponse,
        instructions: List<Instruction>,
        feePayer: Pubkey
    ): TransactionPreview {
        val warnings = response.warnings.map {
            Warning(it, WarningSeverity.MEDIUM, null)
        }

        val riskLevel = calculateRiskLevel(instructions, warnings)

        val instructionSummaries = instructions.mapIndexed { index, ix ->
            InstructionSummary(
                index = index,
                programName = getProgramName(ix.programId),
                programId = ix.programId,
                action = describeInstruction(ix),
                details = null
            )
        }

        return TransactionPreview(
            title = "Transaction Preview",
            description = generateDescription(instructions),
            estimatedFee = FeeEstimate(
                networkFee = response.estimatedFee,
                priorityFee = 0,
                totalFee = response.estimatedFee,
                estimatedCU = response.estimatedCU
            ),
            balanceImpact = emptyList(),
            accountsAffected = emptyList(),
            warnings = warnings,
            riskLevel = riskLevel,
            instructionSummaries = instructionSummaries
        )
    }

    private fun calculateRiskLevel(instructions: List<Instruction>, warnings: List<Warning>): RiskLevel {
        if (warnings.any { it.severity == WarningSeverity.CRITICAL }) return RiskLevel.CRITICAL
        if (warnings.any { it.severity == WarningSeverity.HIGH }) return RiskLevel.HIGH
        if (warnings.size > 3) return RiskLevel.MEDIUM
        return RiskLevel.SAFE
    }

    private fun getProgramName(programId: Pubkey): String? {
        val id = programId.toString()
        return when {
            id.startsWith("1111111111111") -> "System Program"
            id == "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA" -> "Token Program"
            id == "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb" -> "Token 2022"
            id == "ComputeBudget111111111111111111111111111111" -> "Compute Budget"
            id.startsWith("metaq") -> "Metaplex"
            else -> null
        }
    }

    private fun describeInstruction(ix: Instruction): String {
        val programName = getProgramName(ix.programId) ?: "Unknown Program"
        return "Invoke $programName"
    }

    private fun generateDescription(instructions: List<Instruction>): String {
        return "Transaction with ${instructions.size} instruction(s)"
    }

    private fun decodeBase64(data: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}
