package com.selenus.artemis.compute

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * ComputeOptimizer - Intelligent compute budget optimization
 * 
 * Features:
 * - Automatic CU estimation based on instruction analysis
 * - Per-program CU profiling with learning
 * - Dynamic adjustment based on simulation results
 * - Cost-optimal fee recommendation
 * - Jito/priority fee integration ready
 */
class ComputeOptimizer(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val defaultComputeUnits: Int = 200_000,
        val minComputeUnits: Int = 50_000,
        val maxComputeUnits: Int = 1_400_000,
        val safetyMarginPercent: Int = 20,    // Add 20% buffer
        val historyWindowSize: Int = 50,
        val enableProfiling: Boolean = true
    )

    /**
     * Compute budget recommendation
     */
    data class ComputeBudget(
        val computeUnits: Int,
        val microLamportsPerUnit: Long,
        val estimatedTotalLamports: Long,
        val source: BudgetSource,
        val confidence: Float,
        val breakdown: List<InstructionEstimate>
    ) {
        fun toInstructions(): List<Instruction> {
            return listOf(
                ComputeBudgetProgram.setComputeUnitLimit(computeUnits),
                ComputeBudgetProgram.setComputeUnitPrice(microLamportsPerUnit)
            )
        }
    }

    enum class BudgetSource {
        PROFILED,       // Based on historical profiling
        SIMULATED,      // Based on simulation result
        ESTIMATED,      // Based on instruction analysis
        DEFAULT         // Using defaults
    }

    data class InstructionEstimate(
        val programId: Pubkey,
        val instructionType: String?,
        val estimatedCU: Int,
        val confidence: Float
    )

    // Program CU profiles
    private data class ProgramProfile(
        val programId: Pubkey,
        val samples: MutableList<CUSample> = mutableListOf(),
        var averageCU: Int = 0,
        var maxCU: Int = 0,
        var minCU: Int = Int.MAX_VALUE
    )

    private data class CUSample(
        val computeUnits: Int,
        val instructionData: ByteArray,
        val timestampMs: Long
    )

    private val programProfiles = ConcurrentHashMap<Pubkey, ProgramProfile>()
    private val mutex = Mutex()

    // Observable state
    private val _optimization = MutableStateFlow<ComputeBudget?>(null)
    val optimization: StateFlow<ComputeBudget?> = _optimization.asStateFlow()

    /**
     * Estimate compute units for a list of instructions
     */
    suspend fun estimate(
        instructions: List<Instruction>,
        priorityFee: Long = 0L
    ): ComputeBudget = mutex.withLock {
        if (instructions.isEmpty()) {
            return@withLock defaultBudget(priorityFee)
        }

        val breakdown = mutableListOf<InstructionEstimate>()
        var totalEstimate = 0
        var totalConfidence = 0f

        for (ix in instructions) {
            val estimate = estimateInstruction(ix)
            breakdown.add(estimate)
            totalEstimate += estimate.estimatedCU
            totalConfidence += estimate.confidence
        }

        // Apply safety margin
        val withMargin = (totalEstimate * (100 + config.safetyMarginPercent) / 100)
            .coerceIn(config.minComputeUnits, config.maxComputeUnits)

        val avgConfidence = if (breakdown.isNotEmpty()) {
            totalConfidence / breakdown.size
        } else 0f

        val source = if (avgConfidence > 0.7f) BudgetSource.PROFILED
        else if (avgConfidence > 0.3f) BudgetSource.ESTIMATED
        else BudgetSource.DEFAULT

        ComputeBudget(
            computeUnits = withMargin,
            microLamportsPerUnit = priorityFee,
            estimatedTotalLamports = (withMargin.toLong() * priorityFee) / 1_000_000L,
            source = source,
            confidence = avgConfidence,
            breakdown = breakdown
        )
    }

    /**
     * Record actual CU usage for learning
     */
    suspend fun recordUsage(
        programId: Pubkey,
        instructionData: ByteArray,
        actualCU: Int
    ) = mutex.withLock {
        if (!config.enableProfiling) return@withLock

        val profile = programProfiles.getOrPut(programId) { ProgramProfile(programId) }

        profile.samples.add(
            CUSample(
                computeUnits = actualCU,
                instructionData = instructionData,
                timestampMs = System.currentTimeMillis()
            )
        )

        // Trim old samples
        while (profile.samples.size > config.historyWindowSize) {
            profile.samples.removeAt(0)
        }

        // Update statistics
        if (profile.samples.isNotEmpty()) {
            profile.averageCU = profile.samples.map { it.computeUnits }.average().toInt()
            profile.maxCU = profile.samples.maxOf { it.computeUnits }
            profile.minCU = profile.samples.minOf { it.computeUnits }
        }
    }

    /**
     * Record simulation result for optimization
     */
    suspend fun recordSimulation(
        instructions: List<Instruction>,
        simulatedCU: Int,
        success: Boolean
    ) = mutex.withLock {
        if (!success) return@withLock

        // Attribute CU proportionally based on estimates
        val estimates = instructions.map { estimateInstruction(it) }
        val totalEstimate = estimates.sumOf { it.estimatedCU }

        if (totalEstimate == 0) return@withLock

        for ((idx, ix) in instructions.withIndex()) {
            val proportion = estimates[idx].estimatedCU.toFloat() / totalEstimate
            val attributedCU = (simulatedCU * proportion).toInt()
            recordUsage(ix.programId, ix.data, attributedCU)
        }
    }

    /**
     * Get optimized budget for a specific use case
     */
    fun getPreset(preset: ComputePreset): ComputeBudget {
        val (cu, fee) = when (preset) {
            ComputePreset.TRANSFER -> 50_000 to 100L
            ComputePreset.TOKEN_TRANSFER -> 100_000 to 200L
            ComputePreset.NFT_MINT -> 300_000 to 500L
            ComputePreset.NFT_TRANSFER -> 150_000 to 300L
            ComputePreset.SWAP -> 400_000 to 1000L
            ComputePreset.STAKE -> 200_000 to 300L
            ComputePreset.GAMING_ACTION -> 250_000 to 500L
            ComputePreset.GAMING_BATCH -> 600_000 to 800L
            ComputePreset.COMPLEX_TX -> 800_000 to 1500L
            ComputePreset.MAX -> config.maxComputeUnits to 2000L
        }

        return ComputeBudget(
            computeUnits = cu,
            microLamportsPerUnit = fee,
            estimatedTotalLamports = (cu.toLong() * fee) / 1_000_000L,
            source = BudgetSource.DEFAULT,
            confidence = 0.9f,
            breakdown = emptyList()
        )
    }

    enum class ComputePreset {
        TRANSFER,           // Simple SOL transfer
        TOKEN_TRANSFER,     // SPL token transfer
        NFT_MINT,           // NFT minting
        NFT_TRANSFER,       // NFT transfer
        SWAP,               // DEX swap
        STAKE,              // Staking operations
        GAMING_ACTION,      // Single game action
        GAMING_BATCH,       // Batched game actions
        COMPLEX_TX,         // Complex multi-CPI
        MAX                 // Maximum available
    }

    /**
     * Get statistics for a program
     */
    suspend fun getProgramStats(programId: Pubkey): ProgramStats? = mutex.withLock {
        programProfiles[programId]?.let { profile ->
            ProgramStats(
                programId = programId,
                sampleCount = profile.samples.size,
                averageCU = profile.averageCU,
                minCU = profile.minCU,
                maxCU = profile.maxCU,
                variance = calculateVariance(profile.samples.map { it.computeUnits })
            )
        }
    }

    data class ProgramStats(
        val programId: Pubkey,
        val sampleCount: Int,
        val averageCU: Int,
        val minCU: Int,
        val maxCU: Int,
        val variance: Float
    )

    /**
     * Get all program statistics
     */
    suspend fun getAllStats(): List<ProgramStats> = mutex.withLock {
        programProfiles.values.mapNotNull { profile ->
            if (profile.samples.isEmpty()) null
            else ProgramStats(
                programId = profile.programId,
                sampleCount = profile.samples.size,
                averageCU = profile.averageCU,
                minCU = profile.minCU,
                maxCU = profile.maxCU,
                variance = calculateVariance(profile.samples.map { it.computeUnits })
            )
        }
    }

    private fun estimateInstruction(ix: Instruction): InstructionEstimate {
        val profile = programProfiles[ix.programId]

        if (profile != null && profile.samples.isNotEmpty()) {
            // Use profiled data
            return InstructionEstimate(
                programId = ix.programId,
                instructionType = detectInstructionType(ix),
                estimatedCU = profile.averageCU,
                confidence = min(1f, profile.samples.size.toFloat() / 20)
            )
        }

        // Use heuristics based on known programs
        val heuristicCU = estimateFromHeuristics(ix)
        return InstructionEstimate(
            programId = ix.programId,
            instructionType = detectInstructionType(ix),
            estimatedCU = heuristicCU,
            confidence = 0.4f  // Medium confidence for heuristics
        )
    }

    private fun estimateFromHeuristics(ix: Instruction): Int {
        val programStr = ix.programId.toString()
        val dataSize = ix.data.size
        val accountCount = ix.accounts.size

        // Known program estimates
        return when {
            // System Program
            programStr.startsWith("1111111111111111") -> 5_000

            // Token Program
            programStr == "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA" -> 20_000

            // Token 2022
            programStr == "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb" -> 30_000

            // Associated Token Program
            programStr == "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL" -> 25_000

            // Metaplex Token Metadata
            programStr.startsWith("metaq") -> 100_000

            // Bubblegum (cNFT)
            programStr.startsWith("BGUMAp") -> 150_000

            // Compute Budget (no CU cost)
            programStr == "ComputeBudget111111111111111111111111111111" -> 0

            // Default estimation based on complexity
            else -> {
                val baseCU = 50_000
                val dataCU = dataSize * 100
                val accountCU = accountCount * 2000
                (baseCU + dataCU + accountCU).coerceIn(config.minComputeUnits, config.maxComputeUnits / 2)
            }
        }
    }

    private fun detectInstructionType(ix: Instruction): String? {
        if (ix.data.isEmpty()) return null
        // Simple discriminator detection
        return when {
            ix.data.size >= 8 -> "discriminator:${ix.data.take(8).joinToString("") { "%02x".format(it) }}"
            else -> "data:${ix.data.size}bytes"
        }
    }

    private fun calculateVariance(values: List<Int>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun defaultBudget(priorityFee: Long): ComputeBudget {
        return ComputeBudget(
            computeUnits = config.defaultComputeUnits,
            microLamportsPerUnit = priorityFee,
            estimatedTotalLamports = (config.defaultComputeUnits.toLong() * priorityFee) / 1_000_000L,
            source = BudgetSource.DEFAULT,
            confidence = 0.5f,
            breakdown = emptyList()
        )
    }
}

/**
 * Extension for quick budget estimation
 */
suspend fun ComputeOptimizer.quickEstimate(
    vararg instructions: Instruction,
    priorityFee: Long = 1000L
): ComputeOptimizer.ComputeBudget {
    return estimate(instructions.toList(), priorityFee)
}

/**
 * DSL for compute budget configuration
 */
@DslMarker
annotation class ComputeBudgetDsl

@ComputeBudgetDsl
class ComputeBudgetScope {
    var computeUnits: Int = 200_000
    var microLamportsPerUnit: Long = 1000L

    fun preset(preset: ComputeOptimizer.ComputePreset) {
        val (cu, fee) = when (preset) {
            ComputeOptimizer.ComputePreset.TRANSFER -> 50_000 to 100L
            ComputeOptimizer.ComputePreset.TOKEN_TRANSFER -> 100_000 to 200L
            ComputeOptimizer.ComputePreset.NFT_MINT -> 300_000 to 500L
            ComputeOptimizer.ComputePreset.NFT_TRANSFER -> 150_000 to 300L
            ComputeOptimizer.ComputePreset.SWAP -> 400_000 to 1000L
            ComputeOptimizer.ComputePreset.STAKE -> 200_000 to 300L
            ComputeOptimizer.ComputePreset.GAMING_ACTION -> 250_000 to 500L
            ComputeOptimizer.ComputePreset.GAMING_BATCH -> 600_000 to 800L
            ComputeOptimizer.ComputePreset.COMPLEX_TX -> 800_000 to 1500L
            ComputeOptimizer.ComputePreset.MAX -> 1_400_000 to 2000L
        }
        computeUnits = cu
        microLamportsPerUnit = fee
    }

    fun toInstructions(): List<Instruction> {
        return listOf(
            ComputeBudgetProgram.setComputeUnitLimit(computeUnits),
            ComputeBudgetProgram.setComputeUnitPrice(microLamportsPerUnit)
        )
    }
}

fun computeBudget(block: ComputeBudgetScope.() -> Unit): List<Instruction> {
    return ComputeBudgetScope().apply(block).toInstructions()
}
