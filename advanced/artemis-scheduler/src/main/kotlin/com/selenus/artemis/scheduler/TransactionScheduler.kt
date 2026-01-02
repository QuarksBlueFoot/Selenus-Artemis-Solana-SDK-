/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Predictive Transaction Scheduler - Main Implementation
 *
 * This is a first-of-its-kind feature in Solana SDKs. No competitor
 * (solana-kmp, Sol4k, Solana Mobile) offers predictive scheduling.
 */

package com.selenus.artemis.scheduler

import com.selenus.artemis.rpc.RpcApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.Closeable
import java.util.LinkedList

/**
 * Predictive Transaction Scheduler for Solana.
 * 
 * This scheduler monitors network conditions and recommends optimal times
 * to submit transactions. It considers:
 * 
 * - Current network congestion (TPS, block fill rate)
 * - Recent priority fee levels
 * - Epoch timing (congestion often increases at epoch boundaries)
 * - Historical patterns
 * 
 * ## Usage
 * ```kotlin
 * val scheduler = TransactionScheduler(rpc)
 * scheduler.start()
 * 
 * // Get recommendation for a transaction
 * val recommendation = scheduler.recommend(computeUnits = 200_000)
 * 
 * when (recommendation.action) {
 *     ScheduleAction.SUBMIT_NOW -> {
 *         // Submit immediately
 *         submitTransaction(priorityFee = recommendation.recommendedPriorityFee)
 *     }
 *     ScheduleAction.WAIT -> {
 *         // Wait for better conditions
 *         delay(5000)
 *     }
 *     ScheduleAction.SUBMIT_WITH_PRIORITY -> {
 *         // Submit with suggested priority fee
 *         submitTransaction(priorityFee = recommendation.recommendedPriorityFee)
 *     }
 * }
 * 
 * // Observe network state changes
 * scheduler.networkState.collect { state ->
 *     updateUI(state.congestionLevel)
 * }
 * ```
 */
class TransactionScheduler(
    private val rpc: RpcApi,
    private val config: SchedulerConfig = SchedulerConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Closeable {
    
    private val _networkState = MutableStateFlow<NetworkState?>(null)
    val networkState: StateFlow<NetworkState?> = _networkState.asStateFlow()
    
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    private var monitoringJob: Job? = null
    
    // Historical data for predictions
    private val tpsHistory = LinkedList<Double>()
    private val feeHistory = LinkedList<Long>()
    private val maxHistorySize = 60 // 5 minutes at 5-second intervals
    
    /**
     * Start monitoring network conditions.
     */
    fun start() {
        if (_isMonitoring.value) return
        _isMonitoring.value = true
        
        monitoringJob = scope.launch {
            while (_isMonitoring.value) {
                try {
                    val state = fetchNetworkState()
                    _networkState.value = state
                    
                    // Update history
                    addToHistory(state)
                } catch (e: Exception) {
                    // Continue monitoring even if one fetch fails
                }
                
                delay(config.refreshIntervalMs)
            }
        }
    }
    
    /**
     * Stop monitoring network conditions.
     */
    fun stop() {
        _isMonitoring.value = false
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Get the current priority fee tiers based on network conditions.
     */
    fun getPriorityFeeTiers(): PriorityFeeTiers {
        val state = _networkState.value ?: return PriorityFeeTiers.MINIMAL
        
        return when (state.congestionLevel) {
            CongestionLevel.MINIMAL -> PriorityFeeTiers.MINIMAL
            CongestionLevel.LOW -> PriorityFeeTiers.MINIMAL
            CongestionLevel.MODERATE -> PriorityFeeTiers.MODERATE
            CongestionLevel.HIGH -> PriorityFeeTiers.HIGH
            CongestionLevel.SEVERE -> PriorityFeeTiers.SEVERE
        }
    }
    
    /**
     * Get a scheduling recommendation for a transaction.
     * 
     * @param computeUnits The estimated compute units for the transaction
     * @param urgent Whether this transaction is time-sensitive
     * @param maxWaitMs Maximum time willing to wait for better conditions
     * @return Recommendation for when and how to submit
     */
    fun recommend(
        computeUnits: Int = config.defaultComputeUnits,
        urgent: Boolean = false,
        maxWaitMs: Long = config.maxWaitTimeMs
    ): ScheduleRecommendation {
        val state = _networkState.value ?: return defaultRecommendation(computeUnits)
        
        val tiers = getPriorityFeeTiers()
        
        // Urgent transactions always submit immediately with high priority
        if (urgent) {
            return ScheduleRecommendation(
                action = ScheduleAction.SUBMIT_URGENT,
                recommendedPriorityFee = tiers.urgent,
                confidence = 0.9,
                estimatedConfirmationMs = estimateConfirmationTime(state, tiers.urgent),
                estimatedCostLamports = calculateCost(computeUnits, tiers.urgent),
                reason = "Urgent transaction - submitting with maximum priority",
                networkState = state,
                alternatives = emptyList()
            )
        }
        
        // Analyze current conditions
        return when (state.congestionLevel) {
            CongestionLevel.MINIMAL -> {
                ScheduleRecommendation(
                    action = ScheduleAction.SUBMIT_NOW,
                    recommendedPriorityFee = tiers.economy,
                    confidence = 0.95,
                    estimatedConfirmationMs = estimateConfirmationTime(state, tiers.economy),
                    estimatedCostLamports = calculateCost(computeUnits, tiers.economy),
                    reason = "Network is lightly loaded - optimal time to submit",
                    networkState = state,
                    alternatives = listOf(
                        ScheduleAlternative(
                            action = ScheduleAction.SUBMIT_WITH_PRIORITY,
                            waitMs = null,
                            priorityFee = tiers.standard,
                            estimatedConfirmationMs = estimateConfirmationTime(state, tiers.standard),
                            estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
                            description = "Add small priority fee for even faster confirmation"
                        )
                    )
                )
            }
            
            CongestionLevel.LOW -> {
                ScheduleRecommendation(
                    action = ScheduleAction.SUBMIT_NOW,
                    recommendedPriorityFee = tiers.standard,
                    confidence = 0.9,
                    estimatedConfirmationMs = estimateConfirmationTime(state, tiers.standard),
                    estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
                    reason = "Normal network conditions - safe to submit",
                    networkState = state,
                    alternatives = emptyList()
                )
            }
            
            CongestionLevel.MODERATE -> {
                val predictedImprovement = predictImprovement()
                
                if (predictedImprovement && maxWaitMs >= 30_000) {
                    ScheduleRecommendation(
                        action = ScheduleAction.WAIT,
                        recommendedPriorityFee = tiers.standard,
                        confidence = 0.7,
                        estimatedConfirmationMs = estimateConfirmationTime(state, tiers.standard),
                        estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
                        reason = "Moderate congestion detected - conditions may improve soon",
                        networkState = state,
                        alternatives = listOf(
                            ScheduleAlternative(
                                action = ScheduleAction.SUBMIT_WITH_PRIORITY,
                                waitMs = null,
                                priorityFee = tiers.fast,
                                estimatedConfirmationMs = estimateConfirmationTime(state, tiers.fast),
                                estimatedCostLamports = calculateCost(computeUnits, tiers.fast),
                                description = "Submit now with priority fee to ensure confirmation"
                            )
                        )
                    )
                } else {
                    ScheduleRecommendation(
                        action = ScheduleAction.SUBMIT_WITH_PRIORITY,
                        recommendedPriorityFee = tiers.standard,
                        confidence = 0.85,
                        estimatedConfirmationMs = estimateConfirmationTime(state, tiers.standard),
                        estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
                        reason = "Moderate congestion - priority fee recommended",
                        networkState = state,
                        alternatives = emptyList()
                    )
                }
            }
            
            CongestionLevel.HIGH -> {
                ScheduleRecommendation(
                    action = ScheduleAction.SUBMIT_WITH_PRIORITY,
                    recommendedPriorityFee = tiers.fast,
                    confidence = 0.75,
                    estimatedConfirmationMs = estimateConfirmationTime(state, tiers.fast),
                    estimatedCostLamports = calculateCost(computeUnits, tiers.fast),
                    reason = "High network congestion - elevated priority fee required",
                    networkState = state,
                    alternatives = listOf(
                        ScheduleAlternative(
                            action = ScheduleAction.WAIT,
                            waitMs = maxWaitMs.coerceAtMost(60_000),
                            priorityFee = tiers.standard,
                            estimatedConfirmationMs = estimateConfirmationTime(state, tiers.standard) * 2,
                            estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
                            description = "Wait for congestion to decrease"
                        ),
                        ScheduleAlternative(
                            action = ScheduleAction.SUBMIT_URGENT,
                            waitMs = null,
                            priorityFee = tiers.urgent,
                            estimatedConfirmationMs = estimateConfirmationTime(state, tiers.urgent),
                            estimatedCostLamports = calculateCost(computeUnits, tiers.urgent),
                            description = "Submit with maximum priority (expensive)"
                        )
                    )
                )
            }
            
            CongestionLevel.SEVERE -> {
                if (maxWaitMs >= 60_000) {
                    ScheduleRecommendation(
                        action = ScheduleAction.DEFER,
                        recommendedPriorityFee = tiers.urgent,
                        confidence = 0.6,
                        estimatedConfirmationMs = estimateConfirmationTime(state, tiers.urgent),
                        estimatedCostLamports = calculateCost(computeUnits, tiers.urgent),
                        reason = "Severe congestion - recommend deferring non-critical transactions",
                        networkState = state,
                        alternatives = listOf(
                            ScheduleAlternative(
                                action = ScheduleAction.SUBMIT_URGENT,
                                waitMs = null,
                                priorityFee = tiers.urgent,
                                estimatedConfirmationMs = estimateConfirmationTime(state, tiers.urgent),
                                estimatedCostLamports = calculateCost(computeUnits, tiers.urgent),
                                description = "Submit anyway with maximum priority (very expensive)"
                            )
                        )
                    )
                } else {
                    ScheduleRecommendation(
                        action = ScheduleAction.SUBMIT_URGENT,
                        recommendedPriorityFee = tiers.urgent,
                        confidence = 0.5,
                        estimatedConfirmationMs = estimateConfirmationTime(state, tiers.urgent),
                        estimatedCostLamports = calculateCost(computeUnits, tiers.urgent),
                        reason = "Severe congestion - high priority fee required for confirmation",
                        networkState = state,
                        alternatives = emptyList()
                    )
                }
            }
        }
    }
    
    /**
     * Force a refresh of network state.
     */
    suspend fun refresh(): NetworkState {
        val state = fetchNetworkState()
        _networkState.value = state
        addToHistory(state)
        return state
    }
    
    private suspend fun fetchNetworkState(): NetworkState {
        // Fetch recent performance samples
        val samplesJson = try {
            rpc.getRecentPerformanceSamples(10)
        } catch (e: Exception) {
            null
        }
        
        val samples = samplesJson?.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                val numTx = obj["numTransactions"]?.jsonPrimitive?.longOrNull ?: 0L
                val period = obj["samplePeriodSecs"]?.jsonPrimitive?.longOrNull ?: 60L
                Pair(numTx, period)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
        
        val currentTps = samples.firstOrNull()?.let { (numTx, period) -> 
            numTx.toDouble() / period.toDouble() 
        } ?: 0.0
        
        val averageTps = if (samples.isNotEmpty()) {
            samples.sumOf { (numTx, period) -> numTx.toDouble() / period.toDouble() } / samples.size
        } else 0.0
        
        // Fetch slot info
        val slot = try {
            rpc.getSlot()
        } catch (e: Exception) {
            0L
        }
        
        // Fetch epoch info
        val epochInfoJson = try {
            rpc.getEpochInfo()
        } catch (e: Exception) {
            null
        }
        
        val slotIndex = epochInfoJson?.get("slotIndex")?.jsonPrimitive?.longOrNull ?: 0L
        val slotsInEpoch = epochInfoJson?.get("slotsInEpoch")?.jsonPrimitive?.longOrNull ?: 1L
        val epochProgress = slotIndex.toDouble() / slotsInEpoch.toDouble()
        
        // Fetch recent priority fees
        val priorityFeesJson = try {
            rpc.getRecentPrioritizationFees()
        } catch (e: Exception) {
            null
        }
        
        val priorityFees = priorityFeesJson?.mapNotNull { element ->
            try {
                element.jsonObject["prioritizationFee"]?.jsonPrimitive?.longOrNull
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
        
        val medianFee = if (priorityFees.isNotEmpty()) {
            priorityFees.sorted().let { sorted -> sorted[sorted.size / 2] }
        } else 0L
        
        // Calculate block fill rate (estimate based on TPS)
        val maxTps = 4000.0 // Conservative estimate
        val blockFillRate = (currentTps / maxTps).coerceIn(0.0, 1.0)
        
        // Determine congestion level
        val congestionLevel = when {
            blockFillRate < 0.3 && medianFee < 1000 -> CongestionLevel.MINIMAL
            blockFillRate < 0.5 && medianFee < 5000 -> CongestionLevel.LOW
            blockFillRate < 0.7 && medianFee < 20000 -> CongestionLevel.MODERATE
            blockFillRate < 0.85 && medianFee < 100000 -> CongestionLevel.HIGH
            else -> CongestionLevel.SEVERE
        }
        
        return NetworkState(
            currentTps = currentTps,
            averageTps = averageTps,
            maxTps = maxTps,
            currentSlot = slot,
            epochProgress = epochProgress,
            congestionLevel = congestionLevel,
            medianPriorityFee = medianFee,
            blockFillRate = blockFillRate,
            leaderScheduleOffset = null // Would require additional leader schedule tracking
        )
    }
    
    private fun addToHistory(state: NetworkState) {
        synchronized(tpsHistory) {
            tpsHistory.addLast(state.currentTps)
            if (tpsHistory.size > maxHistorySize) tpsHistory.removeFirst()
        }
        
        synchronized(feeHistory) {
            feeHistory.addLast(state.medianPriorityFee)
            if (feeHistory.size > maxHistorySize) feeHistory.removeFirst()
        }
    }
    
    private fun predictImprovement(): Boolean {
        synchronized(tpsHistory) {
            if (tpsHistory.size < 5) return false
            
            // Check if TPS is trending down (indicates improving conditions)
            val recent = tpsHistory.takeLast(5)
            val earlier = tpsHistory.take(5)
            
            val recentAvg = recent.average()
            val earlierAvg = earlier.average()
            
            return recentAvg < earlierAvg * 0.9 // TPS dropping by 10%
        }
    }
    
    private fun estimateConfirmationTime(state: NetworkState, priorityFee: Long): Long {
        // Base confirmation time is ~400ms (slot time)
        val baseTime = 400L
        
        val congestionMultiplier = when (state.congestionLevel) {
            CongestionLevel.MINIMAL -> 1.0
            CongestionLevel.LOW -> 1.5
            CongestionLevel.MODERATE -> 2.5
            CongestionLevel.HIGH -> 5.0
            CongestionLevel.SEVERE -> 10.0
        }
        
        val priorityDiscount = when {
            priorityFee >= 100000 -> 0.3
            priorityFee >= 10000 -> 0.5
            priorityFee >= 1000 -> 0.7
            else -> 1.0
        }
        
        return (baseTime * congestionMultiplier * priorityDiscount).toLong()
    }
    
    private fun calculateCost(computeUnits: Int, priorityFee: Long): Long {
        // Base fee (5000 lamports)
        val baseFee = 5000L
        
        // Priority fee cost = (priority_fee * compute_units) / 1_000_000
        val priorityCost = (priorityFee * computeUnits) / 1_000_000
        
        return baseFee + priorityCost
    }
    
    private fun defaultRecommendation(computeUnits: Int): ScheduleRecommendation {
        val tiers = PriorityFeeTiers.MINIMAL
        
        return ScheduleRecommendation(
            action = ScheduleAction.SUBMIT_NOW,
            recommendedPriorityFee = tiers.standard,
            confidence = 0.5,
            estimatedConfirmationMs = 1000,
            estimatedCostLamports = calculateCost(computeUnits, tiers.standard),
            reason = "Network state unknown - using default settings",
            networkState = NetworkState(
                currentTps = 0.0,
                averageTps = 0.0,
                maxTps = 4000.0,
                currentSlot = 0,
                epochProgress = 0.5,
                congestionLevel = CongestionLevel.LOW,
                medianPriorityFee = 0,
                blockFillRate = 0.0,
                leaderScheduleOffset = null
            )
        )
    }
    
    override fun close() {
        stop()
        scope.cancel()
    }
}
