package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * DynamicPriorityFeeManager - Real-time priority fee optimization
 * 
 * Features:
 * - Network-aware fee calculation
 * - Historical fee analysis
 * - Slot-based fee prediction
 * - Per-program fee profiling
 * - Budget-aware recommendations
 */
class DynamicPriorityFeeManager(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val defaultMicroLamports: Long = 1000L,
        val minMicroLamports: Long = 100L,
        val maxMicroLamports: Long = 100_000L,
        val historyWindowSize: Int = 100,
        val updateIntervalMs: Long = 5000L,
        val percentileTarget: Int = 75  // Target percentile for fee estimation
    )

    data class FeeEstimate(
        val microLamportsPerUnit: Long,
        val percentile: Int,
        val sampleCount: Int,
        val averageSlotDelta: Float,
        val confidence: Float,
        val source: FeeSource
    )

    enum class FeeSource {
        RECENT_HISTORY,     // Based on recent successful transactions
        PROGRAM_PROFILE,    // Based on program-specific data
        NETWORK_SAMPLE,     // Based on network fee sampling
        DEFAULT             // Using default configuration
    }

    sealed class FeeLevel {
        data object Minimum : FeeLevel()
        data object Low : FeeLevel()
        data object Medium : FeeLevel()
        data object High : FeeLevel()
        data object Maximum : FeeLevel()
        data class Custom(val microLamports: Long) : FeeLevel()
    }

    // Fee history storage
    private data class FeeRecord(
        val microLamports: Long,
        val computeUnits: Int,
        val slotSubmitted: Long,
        val slotLanded: Long,
        val success: Boolean,
        val programId: Pubkey?,
        val timestampMs: Long
    )

    private val feeHistory = mutableListOf<FeeRecord>()
    private val programFees = ConcurrentHashMap<Pubkey, MutableList<FeeRecord>>()
    private val mutex = Mutex()

    // Observable state
    private val _currentEstimate = MutableStateFlow(
        FeeEstimate(
            microLamportsPerUnit = config.defaultMicroLamports,
            percentile = 50,
            sampleCount = 0,
            averageSlotDelta = 0f,
            confidence = 0f,
            source = FeeSource.DEFAULT
        )
    )
    val currentEstimate: StateFlow<FeeEstimate> = _currentEstimate.asStateFlow()

    private val _networkCongestion = MutableStateFlow(0f)  // 0-1 scale
    val networkCongestion: StateFlow<Float> = _networkCongestion.asStateFlow()

    private var updateJob: Job? = null

    /**
     * Start periodic fee estimation
     */
    fun start() {
        updateJob = scope.launch {
            while (isActive) {
                delay(config.updateIntervalMs)
                updateEstimate()
            }
        }
    }

    /**
     * Stop the manager
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Record a transaction outcome for learning
     */
    suspend fun recordTransaction(
        microLamports: Long,
        computeUnits: Int,
        slotSubmitted: Long,
        slotLanded: Long,
        success: Boolean,
        programId: Pubkey? = null
    ): Unit = mutex.withLock {
        val record = FeeRecord(
            microLamports = microLamports,
            computeUnits = computeUnits,
            slotSubmitted = slotSubmitted,
            slotLanded = slotLanded,
            success = success,
            programId = programId,
            timestampMs = System.currentTimeMillis()
        )

        feeHistory.add(record)
        while (feeHistory.size > config.historyWindowSize) {
            feeHistory.removeAt(0)
        }

        programId?.let { pid ->
            programFees.getOrPut(pid) { mutableListOf() }.apply {
                add(record)
                while (size > config.historyWindowSize / 2) {
                    removeAt(0)
                }
            }
        }
    }

    /**
     * Get fee recommendation for a specific level
     */
    suspend fun getFee(level: FeeLevel): Long = mutex.withLock {
        val base = _currentEstimate.value.microLamportsPerUnit

        return when (level) {
            is FeeLevel.Minimum -> config.minMicroLamports
            is FeeLevel.Low -> (base * 0.5).toLong().coerceAtLeast(config.minMicroLamports)
            is FeeLevel.Medium -> base
            is FeeLevel.High -> (base * 2.0).toLong().coerceAtMost(config.maxMicroLamports)
            is FeeLevel.Maximum -> config.maxMicroLamports
            is FeeLevel.Custom -> level.microLamports.coerceIn(config.minMicroLamports, config.maxMicroLamports)
        }
    }

    /**
     * Get program-specific fee recommendation
     */
    suspend fun getProgramFee(programId: Pubkey, level: FeeLevel = FeeLevel.Medium): Long = mutex.withLock {
        val programHistory = programFees[programId] ?: return@withLock getFee(level)

        if (programHistory.isEmpty()) return@withLock getFee(level)

        val successfulFees = programHistory
            .filter { it.success }
            .map { it.microLamports }
            .sorted()

        if (successfulFees.isEmpty()) {
            // No successful history - use higher fee
            return@withLock (getFee(level) * 1.5).toLong().coerceAtMost(config.maxMicroLamports)
        }

        val targetIdx = (successfulFees.size * config.percentileTarget / 100).coerceIn(0, successfulFees.size - 1)
        val programFee = successfulFees[targetIdx]

        // Apply level adjustment
        val adjusted = when (level) {
            is FeeLevel.Minimum -> (programFee * 0.3).toLong()
            is FeeLevel.Low -> (programFee * 0.7).toLong()
            is FeeLevel.Medium -> programFee
            is FeeLevel.High -> (programFee * 1.5).toLong()
            is FeeLevel.Maximum -> (programFee * 2.5).toLong()
            is FeeLevel.Custom -> level.microLamports
        }

        return@withLock adjusted.coerceIn(config.minMicroLamports, config.maxMicroLamports)
    }

    /**
     * Estimate fee for target confirmation time
     */
    suspend fun estimateForConfirmationTime(
        targetSlots: Int,
        computeUnits: Int = 200_000
    ): FeeEstimate = mutex.withLock {
        if (feeHistory.isEmpty()) {
            return@withLock _currentEstimate.value
        }

        // Filter successful transactions with slot data
        val withSlots = feeHistory.filter {
            it.success && it.slotLanded > it.slotSubmitted
        }

        if (withSlots.isEmpty()) {
            return@withLock _currentEstimate.value
        }

        // Find transactions that landed within target slots
        val fastLanding = withSlots.filter {
            (it.slotLanded - it.slotSubmitted) <= targetSlots
        }

        val targetFee = if (fastLanding.isNotEmpty()) {
            // Use median of fast-landing transactions
            val sorted = fastLanding.map { it.microLamports }.sorted()
            sorted[sorted.size / 2]
        } else {
            // Need higher fee - use max from history + buffer
            val maxFee = withSlots.maxOfOrNull { it.microLamports } ?: config.defaultMicroLamports
            (maxFee * 1.5).toLong()
        }

        FeeEstimate(
            microLamportsPerUnit = targetFee.coerceIn(config.minMicroLamports, config.maxMicroLamports),
            percentile = if (fastLanding.isNotEmpty()) config.percentileTarget else 95,
            sampleCount = withSlots.size,
            averageSlotDelta = withSlots.map { (it.slotLanded - it.slotSubmitted).toFloat() }.average().toFloat(),
            confidence = (withSlots.size.toFloat() / config.historyWindowSize).coerceAtMost(1f),
            source = FeeSource.RECENT_HISTORY
        )
    }

    /**
     * Get fee statistics
     */
    suspend fun getStatistics(): FeeStatistics = mutex.withLock {
        if (feeHistory.isEmpty()) {
            return@withLock FeeStatistics(
                sampleCount = 0,
                successRate = 0f,
                averageFee = config.defaultMicroLamports,
                minFee = config.minMicroLamports,
                maxFee = config.defaultMicroLamports,
                p50Fee = config.defaultMicroLamports,
                p75Fee = config.defaultMicroLamports,
                p90Fee = config.defaultMicroLamports,
                averageSlotDelta = 0f
            )
        }

        val fees = feeHistory.map { it.microLamports }.sorted()
        val successRate = feeHistory.count { it.success }.toFloat() / feeHistory.size
        val slotDeltas = feeHistory
            .filter { it.success && it.slotLanded > it.slotSubmitted }
            .map { (it.slotLanded - it.slotSubmitted).toFloat() }

        FeeStatistics(
            sampleCount = feeHistory.size,
            successRate = successRate,
            averageFee = fees.average().toLong(),
            minFee = fees.first(),
            maxFee = fees.last(),
            p50Fee = fees[fees.size / 2],
            p75Fee = fees[(fees.size * 0.75).toInt().coerceAtMost(fees.size - 1)],
            p90Fee = fees[(fees.size * 0.90).toInt().coerceAtMost(fees.size - 1)],
            averageSlotDelta = if (slotDeltas.isNotEmpty()) slotDeltas.average().toFloat() else 0f
        )
    }

    data class FeeStatistics(
        val sampleCount: Int,
        val successRate: Float,
        val averageFee: Long,
        val minFee: Long,
        val maxFee: Long,
        val p50Fee: Long,
        val p75Fee: Long,
        val p90Fee: Long,
        val averageSlotDelta: Float
    )

    private suspend fun updateEstimate() = mutex.withLock {
        if (feeHistory.isEmpty()) return@withLock

        val successful = feeHistory.filter { it.success }
        if (successful.isEmpty()) {
            // No successful - increase fee
            val current = _currentEstimate.value.microLamportsPerUnit
            _currentEstimate.value = _currentEstimate.value.copy(
                microLamportsPerUnit = (current * 1.5).toLong().coerceAtMost(config.maxMicroLamports),
                confidence = 0.3f,
                source = FeeSource.DEFAULT
            )
            return@withLock
        }

        val fees = successful.map { it.microLamports }.sorted()
        val targetIdx = (fees.size * config.percentileTarget / 100).coerceIn(0, fees.size - 1)
        val targetFee = fees[targetIdx]

        // Calculate congestion from slot deltas
        val slotDeltas = successful
            .filter { it.slotLanded > it.slotSubmitted }
            .map { (it.slotLanded - it.slotSubmitted).toFloat() }

        val avgSlotDelta = if (slotDeltas.isNotEmpty()) slotDeltas.average().toFloat() else 0f
        val congestion = (avgSlotDelta / 10f).coerceIn(0f, 1f)  // Normalize: 10 slots = max congestion

        _networkCongestion.value = congestion

        _currentEstimate.value = FeeEstimate(
            microLamportsPerUnit = targetFee,
            percentile = config.percentileTarget,
            sampleCount = successful.size,
            averageSlotDelta = avgSlotDelta,
            confidence = (successful.size.toFloat() / config.historyWindowSize).coerceAtMost(1f),
            source = FeeSource.RECENT_HISTORY
        )
    }
}

/**
 * Extension for quick fee calculation
 */
suspend fun DynamicPriorityFeeManager.quickFee(
    highPriority: Boolean = false
): Long {
    return getFee(
        if (highPriority) DynamicPriorityFeeManager.FeeLevel.High
        else DynamicPriorityFeeManager.FeeLevel.Medium
    )
}
