package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * AdaptiveFeeOptimizer - Intelligent priority fee optimization for gaming
 * 
 * Features:
 * - Network congestion awareness
 * - Per-program fee profiling
 * - Action priority weighting
 * - Predictive fee estimation
 * - Cost budget management
 * - Real-time fee adjustment
 */
class AdaptiveFeeOptimizer(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val baseMicroLamports: Long = 100L,
        val minMicroLamports: Long = 50L,
        val maxMicroLamports: Long = 50_000L,
        val sampleWindowSize: Int = 50,
        val adjustmentIntervalMs: Long = 2000L,
        val aggressiveMode: Boolean = false,
        val budgetPerHourMicroLamports: Long = 10_000_000L  // 10 SOL/hour max
    )

    /**
     * Fee recommendation based on current network state
     */
    data class FeeRecommendation(
        val computeUnitLimit: Int,
        val microLamportsPerUnit: Long,
        val estimatedTotalLamports: Long,
        val confidence: Float,                   // 0-1 confidence in estimate
        val congestionLevel: CongestionLevel,
        val reason: String
    ) {
        /**
         * Generate instructions from recommendation
         */
        fun toInstructions(): List<Instruction> {
            return listOf(
                com.selenus.artemis.compute.ComputeBudgetProgram.setComputeUnitLimit(computeUnitLimit),
                com.selenus.artemis.compute.ComputeBudgetProgram.setComputeUnitPrice(microLamportsPerUnit)
            )
        }
    }

    enum class CongestionLevel {
        LOW,        // Network idle, minimal fees needed
        NORMAL,     // Standard activity
        ELEVATED,   // Higher than normal
        HIGH,       // Congestion detected
        CRITICAL    // Network stressed, high fees required
    }

    /**
     * Transaction outcome for learning
     */
    data class TransactionOutcome(
        val signature: String,
        val programId: Pubkey?,
        val actionPriority: ActionPriority,
        val computeUnitsUsed: Int,
        val microLamportsSpent: Long,
        val success: Boolean,
        val confirmationTimeMs: Long,
        val slot: Long,
        val timestampMs: Long = System.currentTimeMillis()
    )

    enum class ActionPriority(val weight: Float) {
        BACKGROUND(0.1f),       // Background sync, can wait
        LOW(0.25f),             // Non-urgent
        NORMAL(0.5f),           // Standard actions
        HIGH(0.75f),            // Important player actions
        CRITICAL(1.0f),         // Must-land immediately
        COMBAT(0.9f),           // Real-time combat
        TRADE(0.8f)             // Economic transactions
    }

    // Internal state
    private val outcomes = ConcurrentHashMap<String, MutableList<TransactionOutcome>>()
    private val mutex = Mutex()
    private var currentCongestionLevel = CongestionLevel.NORMAL
    private var recentConfirmationTimes = ArrayDeque<Long>(config.sampleWindowSize)
    private var recentSuccessRate = 1.0f
    private var hourlySpendMicroLamports = 0L
    private var hourStartMs = System.currentTimeMillis()

    // Observable state
    private val _congestionState = MutableStateFlow(CongestionLevel.NORMAL)
    val congestionState: StateFlow<CongestionLevel> = _congestionState.asStateFlow()

    private val _feeStats = MutableStateFlow(FeeStats(0, 0, 0L, 0f, 0L))
    val feeStats: StateFlow<FeeStats> = _feeStats.asStateFlow()

    data class FeeStats(
        val totalTransactions: Int,
        val successfulTransactions: Int,
        val totalMicroLamportsSpent: Long,
        val averageConfirmationTimeMs: Float,
        val estimatedHourlyCost: Long
    )

    private var analysisJob: Job? = null

    /**
     * Start the optimizer
     */
    fun start() {
        analysisJob = scope.launch {
            while (isActive) {
                delay(config.adjustmentIntervalMs)
                analyzeCongestion()
            }
        }
    }

    /**
     * Stop the optimizer
     */
    fun stop() {
        analysisJob?.cancel()
        analysisJob = null
    }

    /**
     * Get fee recommendation for a transaction
     */
    suspend fun recommend(
        programId: Pubkey? = null,
        priority: ActionPriority = ActionPriority.NORMAL,
        estimatedComputeUnits: Int = 200_000,
        context: TransactionContext = TransactionContext()
    ): FeeRecommendation = mutex.withLock {
        checkBudgetReset()

        // Get program-specific history
        val programHistory = programId?.let { outcomes[it.toString()] } ?: emptyList()
        
        // Calculate base fee from priority and congestion
        val baseFee = calculateBaseFee(priority)
        val congestionMultiplier = getCongestionMultiplier()
        val programAdjustment = calculateProgramAdjustment(programHistory)
        
        // Apply all adjustments
        var recommendedFee = (baseFee * congestionMultiplier * programAdjustment * priority.weight).toLong()
        
        // Apply context adjustments
        if (context.isRetry) {
            recommendedFee = (recommendedFee * 1.5).toLong()
        }
        if (context.isTimeSensitive) {
            recommendedFee = (recommendedFee * 1.3).toLong()
        }
        if (context.isHighValue) {
            recommendedFee = (recommendedFee * 1.2).toLong()
        }

        // Clamp to configured limits
        recommendedFee = recommendedFee.coerceIn(config.minMicroLamports, config.maxMicroLamports)

        // Check budget
        val estimatedTotal = recommendedFee * estimatedComputeUnits / 1_000_000L
        if (hourlySpendMicroLamports + estimatedTotal > config.budgetPerHourMicroLamports) {
            // Budget constrained - reduce fee
            recommendedFee = min(recommendedFee, config.baseMicroLamports)
        }

        // Calculate confidence based on data availability
        val confidence = calculateConfidence(programHistory.size)

        FeeRecommendation(
            computeUnitLimit = estimatedComputeUnits,
            microLamportsPerUnit = recommendedFee,
            estimatedTotalLamports = (recommendedFee * estimatedComputeUnits) / 1_000_000L,
            confidence = confidence,
            congestionLevel = currentCongestionLevel,
            reason = generateReason(priority, congestionMultiplier, programAdjustment)
        )
    }

    /**
     * Context for fee recommendation
     */
    data class TransactionContext(
        val isRetry: Boolean = false,
        val retryCount: Int = 0,
        val isTimeSensitive: Boolean = false,
        val isHighValue: Boolean = false,
        val deadlineMs: Long? = null
    )

    /**
     * Record a transaction outcome for learning
     */
    suspend fun recordOutcome(outcome: TransactionOutcome) = mutex.withLock {
        val key = outcome.programId?.toString() ?: "default"
        
        outcomes.getOrPut(key) { mutableListOf() }.apply {
            add(outcome)
            while (size > config.sampleWindowSize) {
                removeAt(0)
            }
        }

        // Update metrics
        recentConfirmationTimes.addLast(outcome.confirmationTimeMs)
        while (recentConfirmationTimes.size > config.sampleWindowSize) {
            recentConfirmationTimes.removeFirst()
        }

        // Update spend tracking
        hourlySpendMicroLamports += outcome.microLamportsSpent

        // Update success rate
        val allRecent = outcomes.values.flatten().takeLast(config.sampleWindowSize)
        recentSuccessRate = allRecent.count { it.success }.toFloat() / max(1, allRecent.size)

        // Update stats
        updateStats()
    }

    /**
     * Get tier-based presets for gaming
     */
    fun getGamingPreset(tier: GamingTier): FeeRecommendation {
        val (cu, price) = when (tier) {
            GamingTier.CASUAL -> 150_000 to 100L
            GamingTier.STANDARD -> 200_000 to 300L
            GamingTier.COMPETITIVE -> 400_000 to 800L
            GamingTier.ESPORTS -> 600_000 to 2000L
            GamingTier.BOSS_BATTLE -> 800_000 to 5000L
        }

        return FeeRecommendation(
            computeUnitLimit = cu,
            microLamportsPerUnit = price,
            estimatedTotalLamports = (price * cu) / 1_000_000L,
            confidence = 0.8f,
            congestionLevel = currentCongestionLevel,
            reason = "Gaming preset: ${tier.name}"
        )
    }

    enum class GamingTier {
        CASUAL,         // Idle/background games
        STANDARD,       // Normal gameplay
        COMPETITIVE,    // PvP/ranked
        ESPORTS,        // Tournament play
        BOSS_BATTLE     // High-stakes raids
    }

    /**
     * Get current budget status
     */
    fun getBudgetStatus(): BudgetStatus {
        val elapsed = System.currentTimeMillis() - hourStartMs
        val remaining = config.budgetPerHourMicroLamports - hourlySpendMicroLamports
        val percentUsed = (hourlySpendMicroLamports.toFloat() / config.budgetPerHourMicroLamports) * 100

        return BudgetStatus(
            hourlyBudget = config.budgetPerHourMicroLamports,
            spent = hourlySpendMicroLamports,
            remaining = remaining,
            percentUsed = percentUsed,
            timeElapsedMs = elapsed,
            projectedHourlyCost = if (elapsed > 0) {
                (hourlySpendMicroLamports.toFloat() / elapsed * 3_600_000L).toLong()
            } else 0L
        )
    }

    data class BudgetStatus(
        val hourlyBudget: Long,
        val spent: Long,
        val remaining: Long,
        val percentUsed: Float,
        val timeElapsedMs: Long,
        val projectedHourlyCost: Long
    )

    private fun calculateBaseFee(priority: ActionPriority): Long {
        return when (priority) {
            ActionPriority.BACKGROUND -> config.minMicroLamports
            ActionPriority.LOW -> config.baseMicroLamports / 2
            ActionPriority.NORMAL -> config.baseMicroLamports
            ActionPriority.HIGH -> config.baseMicroLamports * 2
            ActionPriority.COMBAT -> config.baseMicroLamports * 3
            ActionPriority.TRADE -> config.baseMicroLamports * 2
            ActionPriority.CRITICAL -> config.baseMicroLamports * 5
        }
    }

    private fun getCongestionMultiplier(): Float {
        return when (currentCongestionLevel) {
            CongestionLevel.LOW -> 0.5f
            CongestionLevel.NORMAL -> 1.0f
            CongestionLevel.ELEVATED -> 1.5f
            CongestionLevel.HIGH -> 2.5f
            CongestionLevel.CRITICAL -> 5.0f
        }
    }

    private fun calculateProgramAdjustment(history: List<TransactionOutcome>): Float {
        if (history.isEmpty()) return 1.0f

        val recentFailures = history.takeLast(10).count { !it.success }
        val avgConfirmTime = history.map { it.confirmationTimeMs }.average()

        // Increase fee if many failures or slow confirmations
        var adjustment = 1.0f
        if (recentFailures > 3) adjustment *= 1.3f
        if (avgConfirmTime > 5000) adjustment *= 1.2f
        if (avgConfirmTime > 10000) adjustment *= 1.5f

        return adjustment.coerceIn(0.5f, 3.0f)
    }

    private fun calculateConfidence(sampleCount: Int): Float {
        return min(1.0f, sampleCount.toFloat() / config.sampleWindowSize)
    }

    private fun generateReason(priority: ActionPriority, congestion: Float, program: Float): String {
        val parts = mutableListOf<String>()
        parts.add("Priority: ${priority.name}")
        if (congestion > 1.0f) parts.add("Congestion: ${currentCongestionLevel.name}")
        if (program > 1.1f) parts.add("Program history adjustment")
        return parts.joinToString(", ")
    }

    private suspend fun analyzeCongestion() = mutex.withLock {
        if (recentConfirmationTimes.isEmpty()) return@withLock

        val avgConfirmTime = recentConfirmationTimes.average()
        val failureRate = 1.0f - recentSuccessRate

        currentCongestionLevel = when {
            avgConfirmTime < 1000 && failureRate < 0.05 -> CongestionLevel.LOW
            avgConfirmTime < 3000 && failureRate < 0.1 -> CongestionLevel.NORMAL
            avgConfirmTime < 6000 && failureRate < 0.2 -> CongestionLevel.ELEVATED
            avgConfirmTime < 10000 && failureRate < 0.4 -> CongestionLevel.HIGH
            else -> CongestionLevel.CRITICAL
        }

        _congestionState.value = currentCongestionLevel
    }

    private fun checkBudgetReset() {
        val now = System.currentTimeMillis()
        if (now - hourStartMs > 3_600_000L) {
            hourStartMs = now
            hourlySpendMicroLamports = 0L
        }
    }

    private suspend fun updateStats() {
        val allOutcomes = outcomes.values.flatten()
        val stats = FeeStats(
            totalTransactions = allOutcomes.size,
            successfulTransactions = allOutcomes.count { it.success },
            totalMicroLamportsSpent = allOutcomes.sumOf { it.microLamportsSpent },
            averageConfirmationTimeMs = if (allOutcomes.isNotEmpty()) {
                allOutcomes.map { it.confirmationTimeMs }.average().toFloat()
            } else 0f,
            estimatedHourlyCost = getBudgetStatus().projectedHourlyCost
        )
        _feeStats.value = stats
    }
}

/**
 * Extension for quick fee recommendation
 */
suspend fun AdaptiveFeeOptimizer.quickRecommend(
    priority: AdaptiveFeeOptimizer.ActionPriority = AdaptiveFeeOptimizer.ActionPriority.NORMAL
): AdaptiveFeeOptimizer.FeeRecommendation {
    return recommend(priority = priority)
}
