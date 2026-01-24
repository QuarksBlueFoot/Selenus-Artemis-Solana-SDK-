package com.selenus.artemis.compute

import kotlin.math.max
import kotlin.math.min

/**
 * Artemis Priority Fee Estimation
 * 
 * The Solana Mobile SDK lacks built-in priority fee estimation, requiring
 * developers to manually query and calculate fees. Artemis provides:
 * 
 * - Smart fee estimation based on recent slot data
 * - Multiple fee strategies (economy, standard, fast, urgent)
 * - Account-aware fee estimation (fees vary by accounts accessed)
 * - Compute unit budgeting with fee multipliers
 * - Fee caps and minimum thresholds
 * 
 * Priority fees = priorityRate × computeUnits
 */
object PriorityFees {
    
    /** Default compute unit limit for transactions */
    const val DEFAULT_COMPUTE_UNITS = 200_000
    
    /** Maximum compute units per transaction */
    const val MAX_COMPUTE_UNITS = 1_400_000
    
    /** Minimum priority fee rate (microlamports per compute unit) */
    const val MIN_PRIORITY_RATE = 1L
    
    /** Default max priority fee in lamports */
    const val DEFAULT_MAX_FEE_LAMPORTS = 10_000_000L // 0.01 SOL
    
    /**
     * Fee tier for transaction priority.
     */
    enum class FeeTier(
        val percentile: Int,
        val description: String
    ) {
        /** Lowest fees, may take longer to confirm */
        ECONOMY(25, "Economy - 25th percentile"),
        
        /** Standard fees for normal transactions */
        STANDARD(50, "Standard - 50th percentile (median)"),
        
        /** Higher fees for faster confirmation */
        FAST(75, "Fast - 75th percentile"),
        
        /** Highest fees for urgent transactions */
        URGENT(95, "Urgent - 95th percentile")
    }
    
    /**
     * Priority fee recommendation.
     */
    data class FeeRecommendation(
        val priorityRate: Long, // microlamports per compute unit
        val computeUnits: Int,
        val totalFeeLamports: Long,
        val tier: FeeTier,
        val confidence: Confidence,
        val basedOnSamples: Int
    ) {
        /** Estimated total fee in SOL */
        val totalFeeSol: Double
            get() = totalFeeLamports / 1_000_000_000.0
    }
    
    /**
     * Confidence level of the fee estimate.
     */
    enum class Confidence {
        /** High confidence - based on many recent samples */
        HIGH,
        /** Medium confidence - based on some samples */
        MEDIUM,
        /** Low confidence - few samples, using defaults */
        LOW,
        /** No data available, using defaults */
        DEFAULT
    }
    
    /**
     * Fee estimation options.
     */
    data class FeeOptions(
        val tier: FeeTier = FeeTier.STANDARD,
        val computeUnits: Int = DEFAULT_COMPUTE_UNITS,
        val maxFeeLamports: Long = DEFAULT_MAX_FEE_LAMPORTS,
        val minPriorityRate: Long = MIN_PRIORITY_RATE,
        val accountAddresses: List<String>? = null
    )
    
    /**
     * Fee data from a recent block/slot.
     */
    data class SlotFeeData(
        val slot: Long,
        val fees: List<Long>, // priority rates in microlamports
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Interface for RPC fee queries (implemented by RPC client).
     */
    interface FeeProvider {
        /**
         * Gets recent priority fees for the specified accounts.
         * Returns fee data for recent slots.
         */
        suspend fun getRecentPriorityFees(
            accountAddresses: List<String>? = null,
            slots: Int = 150
        ): List<SlotFeeData>
        
        /**
         * Gets the current slot.
         */
        suspend fun getCurrentSlot(): Long
    }
    
    // ========================================================================
    // Fee Estimation
    // ========================================================================
    
    /**
     * Estimates priority fee based on recent slot data.
     */
    fun estimate(
        feeData: List<SlotFeeData>,
        options: FeeOptions = FeeOptions()
    ): FeeRecommendation {
        // Flatten all fee samples
        val allFees = feeData.flatMap { it.fees }
            .filter { it > 0 }
            .sorted()
        
        val (priorityRate, confidence) = when {
            allFees.size >= 100 -> {
                val rate = percentile(allFees, options.tier.percentile)
                rate to Confidence.HIGH
            }
            allFees.size >= 20 -> {
                val rate = percentile(allFees, options.tier.percentile)
                rate to Confidence.MEDIUM
            }
            allFees.size >= 5 -> {
                val rate = percentile(allFees, options.tier.percentile)
                rate to Confidence.LOW
            }
            else -> {
                // Use tier-based defaults
                val defaultRate = when (options.tier) {
                    FeeTier.ECONOMY -> 1_000L
                    FeeTier.STANDARD -> 10_000L
                    FeeTier.FAST -> 100_000L
                    FeeTier.URGENT -> 1_000_000L
                }
                defaultRate to Confidence.DEFAULT
            }
        }
        
        // Ensure minimum rate
        val adjustedRate = max(priorityRate, options.minPriorityRate)
        
        // Calculate total fee
        val computeUnits = options.computeUnits.coerceIn(1, MAX_COMPUTE_UNITS)
        var totalFeeLamports = (adjustedRate * computeUnits) / 1_000_000 // microlamports to lamports
        
        // Apply max cap
        if (totalFeeLamports > options.maxFeeLamports) {
            totalFeeLamports = options.maxFeeLamports
        }
        
        return FeeRecommendation(
            priorityRate = adjustedRate,
            computeUnits = computeUnits,
            totalFeeLamports = totalFeeLamports,
            tier = options.tier,
            confidence = confidence,
            basedOnSamples = allFees.size
        )
    }
    
    /**
     * Estimates priority fee using an RPC provider.
     */
    suspend fun estimate(
        provider: FeeProvider,
        options: FeeOptions = FeeOptions()
    ): FeeRecommendation {
        return try {
            val feeData = provider.getRecentPriorityFees(
                accountAddresses = options.accountAddresses
            )
            estimate(feeData, options)
        } catch (e: Exception) {
            // Return default on error
            estimate(emptyList(), options)
        }
    }
    
    /**
     * Gets all tier recommendations at once.
     */
    fun estimateAllTiers(
        feeData: List<SlotFeeData>,
        computeUnits: Int = DEFAULT_COMPUTE_UNITS,
        maxFeeLamports: Long = DEFAULT_MAX_FEE_LAMPORTS
    ): Map<FeeTier, FeeRecommendation> {
        return FeeTier.entries.associateWith { tier ->
            estimate(feeData, FeeOptions(
                tier = tier,
                computeUnits = computeUnits,
                maxFeeLamports = maxFeeLamports
            ))
        }
    }
    
    /**
     * Gets all tier recommendations using an RPC provider.
     */
    suspend fun estimateAllTiers(
        provider: FeeProvider,
        accountAddresses: List<String>? = null,
        computeUnits: Int = DEFAULT_COMPUTE_UNITS,
        maxFeeLamports: Long = DEFAULT_MAX_FEE_LAMPORTS
    ): Map<FeeTier, FeeRecommendation> {
        return try {
            val feeData = provider.getRecentPriorityFees(accountAddresses)
            estimateAllTiers(feeData, computeUnits, maxFeeLamports)
        } catch (e: Exception) {
            estimateAllTiers(emptyList(), computeUnits, maxFeeLamports)
        }
    }
    
    // ========================================================================
    // Compute Budget Instructions
    // ========================================================================
    
    /** Compute Budget program ID */
    private val COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111"
    
    /**
     * Creates SetComputeUnitLimit instruction data.
     */
    fun setComputeUnitLimitData(units: Int): ByteArray {
        val data = ByteArray(5)
        data[0] = 2 // SetComputeUnitLimit discriminator
        data[1] = (units and 0xFF).toByte()
        data[2] = ((units shr 8) and 0xFF).toByte()
        data[3] = ((units shr 16) and 0xFF).toByte()
        data[4] = ((units shr 24) and 0xFF).toByte()
        return data
    }
    
    /**
     * Creates SetComputeUnitPrice instruction data.
     */
    fun setComputeUnitPriceData(microLamports: Long): ByteArray {
        val data = ByteArray(9)
        data[0] = 3 // SetComputeUnitPrice discriminator
        for (i in 0 until 8) {
            data[1 + i] = ((microLamports shr (i * 8)) and 0xFF).toByte()
        }
        return data
    }
    
    /**
     * Compute budget instruction pair for a fee recommendation.
     */
    data class ComputeBudgetInstructions(
        val computeUnitLimit: ByteArray,
        val computeUnitPrice: ByteArray,
        val programId: String = COMPUTE_BUDGET_PROGRAM
    )
    
    /**
     * Creates compute budget instructions from a fee recommendation.
     */
    fun createComputeBudgetInstructions(
        recommendation: FeeRecommendation
    ): ComputeBudgetInstructions {
        return ComputeBudgetInstructions(
            computeUnitLimit = setComputeUnitLimitData(recommendation.computeUnits),
            computeUnitPrice = setComputeUnitPriceData(recommendation.priorityRate)
        )
    }
    
    // ========================================================================
    // Utility Functions
    // ========================================================================
    
    private fun percentile(sortedList: List<Long>, percentile: Int): Long {
        if (sortedList.isEmpty()) return 0L
        val index = (sortedList.size * percentile / 100.0).toInt()
            .coerceIn(0, sortedList.size - 1)
        return sortedList[index]
    }
    
    /**
     * Formats a fee recommendation for display.
     */
    fun formatRecommendation(recommendation: FeeRecommendation): String {
        return buildString {
            appendLine("Priority Fee Recommendation")
            appendLine("═══════════════════════════")
            appendLine("Tier: ${recommendation.tier.description}")
            appendLine("Priority Rate: ${recommendation.priorityRate} µL/CU")
            appendLine("Compute Units: ${recommendation.computeUnits}")
            appendLine("Total Fee: ${recommendation.totalFeeLamports} lamports (${String.format("%.9f", recommendation.totalFeeSol)} SOL)")
            appendLine("Confidence: ${recommendation.confidence}")
            appendLine("Based on ${recommendation.basedOnSamples} samples")
        }
    }
}

/**
 * Fee estimator with cached data for efficiency.
 */
class CachedFeeEstimator(
    private val provider: PriorityFees.FeeProvider,
    private val cacheValidityMs: Long = 10_000 // 10 seconds
) {
    private var cachedData: List<PriorityFees.SlotFeeData>? = null
    private var cacheTimestamp: Long = 0
    
    /**
     * Estimates fees, using cached data if still valid.
     */
    suspend fun estimate(
        options: PriorityFees.FeeOptions = PriorityFees.FeeOptions()
    ): PriorityFees.FeeRecommendation {
        val now = System.currentTimeMillis()
        
        // Refresh cache if stale or if different accounts requested
        if (cachedData == null || 
            now - cacheTimestamp > cacheValidityMs ||
            options.accountAddresses != null) {
            cachedData = provider.getRecentPriorityFees(options.accountAddresses)
            cacheTimestamp = now
        }
        
        return PriorityFees.estimate(cachedData!!, options)
    }
    
    /**
     * Forces a cache refresh on next estimate.
     */
    fun invalidateCache() {
        cachedData = null
        cacheTimestamp = 0
    }
}
