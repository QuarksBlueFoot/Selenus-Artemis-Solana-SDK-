/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Predictive Transaction Scheduler - Network Metrics
 *
 * This module analyzes network conditions to suggest optimal transaction
 * submission times. First-of-its-kind in Solana SDKs.
 */

package com.selenus.artemis.scheduler

import kotlinx.serialization.Serializable

/**
 * Current state of the Solana network.
 */
@Serializable
data class NetworkState(
    /** Current TPS (transactions per second) */
    val currentTps: Double,
    
    /** Average TPS over the last minute */
    val averageTps: Double,
    
    /** Maximum TPS capacity estimate */
    val maxTps: Double,
    
    /** Current slot */
    val currentSlot: Long,
    
    /** Epoch progress (0.0 - 1.0) */
    val epochProgress: Double,
    
    /** Current congestion level */
    val congestionLevel: CongestionLevel,
    
    /** Median priority fee in microlamports per compute unit */
    val medianPriorityFee: Long,
    
    /** Recent block fill rate (0.0 - 1.0) */
    val blockFillRate: Double,
    
    /** Leader schedule slot offset (slots until favorable leader) */
    val leaderScheduleOffset: Int?,
    
    /** Timestamp when this state was captured */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calculate a simple congestion score (0-100).
     * Lower is better.
     */
    val congestionScore: Int
        get() = when (congestionLevel) {
            CongestionLevel.MINIMAL -> (10 * blockFillRate).toInt()
            CongestionLevel.LOW -> (30 * blockFillRate).toInt()
            CongestionLevel.MODERATE -> (50 * blockFillRate).toInt()
            CongestionLevel.HIGH -> (70 + 20 * blockFillRate).toInt()
            CongestionLevel.SEVERE -> (90 + 10 * blockFillRate).toInt().coerceAtMost(100)
        }
}

/**
 * Network congestion levels.
 */
@Serializable
enum class CongestionLevel {
    /** Network is lightly loaded, transactions confirm quickly */
    MINIMAL,
    
    /** Normal operation, standard confirmation times */
    LOW,
    
    /** Some congestion, may need priority fees */
    MODERATE,
    
    /** Heavy load, priority fees recommended */
    HIGH,
    
    /** Severe congestion, high priority fees required */
    SEVERE
}

/**
 * Recommendation for when to submit a transaction.
 */
@Serializable
data class ScheduleRecommendation(
    /** Recommended action */
    val action: ScheduleAction,
    
    /** Recommended priority fee in microlamports per CU */
    val recommendedPriorityFee: Long,
    
    /** Confidence score (0.0 - 1.0) */
    val confidence: Double,
    
    /** Estimated confirmation time in milliseconds */
    val estimatedConfirmationMs: Long,
    
    /** Estimated total cost in lamports (including priority fee) */
    val estimatedCostLamports: Long,
    
    /** Reason for the recommendation */
    val reason: String,
    
    /** Current network state that informed this recommendation */
    val networkState: NetworkState,
    
    /** Alternative options */
    val alternatives: List<ScheduleAlternative> = emptyList()
)

/**
 * Recommended action for transaction submission.
 */
@Serializable
enum class ScheduleAction {
    /** Submit immediately - network conditions are optimal */
    SUBMIT_NOW,
    
    /** Wait for better conditions */
    WAIT,
    
    /** Submit with priority fee to ensure confirmation */
    SUBMIT_WITH_PRIORITY,
    
    /** Submit urgently regardless of conditions */
    SUBMIT_URGENT,
    
    /** Network is too congested, defer until later */
    DEFER
}

/**
 * Alternative scheduling option.
 */
@Serializable
data class ScheduleAlternative(
    /** Action for this alternative */
    val action: ScheduleAction,
    
    /** Wait time in milliseconds (if WAIT) */
    val waitMs: Long?,
    
    /** Priority fee for this alternative */
    val priorityFee: Long,
    
    /** Estimated confirmation time */
    val estimatedConfirmationMs: Long,
    
    /** Estimated cost in lamports */
    val estimatedCostLamports: Long,
    
    /** Trade-off description */
    val description: String
)

/**
 * Priority fee tiers based on network conditions.
 */
@Serializable
data class PriorityFeeTiers(
    /** Fee for minimal priority (may be dropped in congestion) */
    val economy: Long,
    
    /** Standard fee for normal conditions */
    val standard: Long,
    
    /** Higher fee for faster confirmation */
    val fast: Long,
    
    /** Highest fee for critical transactions */
    val urgent: Long
) {
    companion object {
        /** Default fees for minimal congestion */
        val MINIMAL = PriorityFeeTiers(
            economy = 0,
            standard = 100,
            fast = 1000,
            urgent = 10000
        )
        
        /** Fees for moderate congestion */
        val MODERATE = PriorityFeeTiers(
            economy = 1000,
            standard = 5000,
            fast = 20000,
            urgent = 100000
        )
        
        /** Fees for heavy congestion */
        val HIGH = PriorityFeeTiers(
            economy = 10000,
            standard = 50000,
            fast = 200000,
            urgent = 1000000
        )
        
        /** Fees for severe congestion */
        val SEVERE = PriorityFeeTiers(
            economy = 50000,
            standard = 200000,
            fast = 500000,
            urgent = 2000000
        )
    }
}

/**
 * Configuration for the transaction scheduler.
 */
data class SchedulerConfig(
    /** How often to refresh network state (ms) */
    val refreshIntervalMs: Long = 5_000,
    
    /** Maximum wait time to recommend (ms) */
    val maxWaitTimeMs: Long = 60_000,
    
    /** Congestion threshold for recommending wait */
    val waitThreshold: CongestionLevel = CongestionLevel.HIGH,
    
    /** Default compute units for cost estimation */
    val defaultComputeUnits: Int = 200_000,
    
    /** Whether to consider epoch timing */
    val considerEpochTiming: Boolean = true,
    
    /** Whether to track leader schedule */
    val trackLeaderSchedule: Boolean = false
)
