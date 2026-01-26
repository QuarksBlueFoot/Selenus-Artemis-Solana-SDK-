/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Intelligent Batching Engine - Batch Types
 *
 * This module automatically combines multiple operations into optimized
 * transactions. First-of-its-kind in Solana SDKs.
 */

package com.selenus.artemis.batch

import com.selenus.artemis.tx.Instruction

/**
 * Represents a single operation that can be batched.
 */
data class BatchOperation(
    /** Unique ID for this operation */
    val id: String,
    
    /** The instruction(s) for this operation */
    val instructions: List<Instruction>,
    
    /** Estimated compute units for this operation */
    val estimatedComputeUnits: Int,
    
    /** Human-readable description */
    val description: String,
    
    /** Priority (higher = include first) */
    val priority: Int = 0,
    
    /** Whether this operation is idempotent (safe to retry) */
    val isIdempotent: Boolean = true,
    
    /** Custom metadata */
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /** Default compute unit estimate if not specified */
        const val DEFAULT_COMPUTE_UNITS = 50_000
    }
}

/**
 * Result of batch analysis.
 */
data class BatchPlan(
    /** Planned transaction batches */
    val batches: List<PlannedBatch>,
    
    /** Total number of operations across all batches */
    val totalOperations: Int,
    
    /** Total estimated compute units */
    val totalComputeUnits: Int,
    
    /** Estimated total cost in lamports */
    val estimatedCostLamports: Long,
    
    /** Savings compared to individual transactions */
    val savingsLamports: Long,
    
    /** Savings percentage */
    val savingsPercent: Double
)

/**
 * A planned batch of operations.
 */
data class PlannedBatch(
    /** Batch index (0-based) */
    val index: Int,
    
    /** Operations in this batch */
    val operations: List<BatchOperation>,
    
    /** Combined instructions */
    val instructions: List<Instruction>,
    
    /** Total compute units for this batch */
    val computeUnits: Int,
    
    /** Estimated cost in lamports */
    val estimatedCostLamports: Long,
    
    /** Whether this batch is at capacity */
    val atCapacity: Boolean
)

/**
 * Result of executing a batch.
 */
sealed class BatchResult {
    /** Batch executed successfully */
    data class Success(
        val batchIndex: Int,
        val signature: String,
        val slot: Long?,
        val operationIds: List<String>
    ) : BatchResult()
    
    /** Batch partially succeeded (some operations failed) */
    data class PartialSuccess(
        val batchIndex: Int,
        val signature: String,
        val successfulOperations: List<String>,
        val failedOperations: List<FailedOperation>
    ) : BatchResult()
    
    /** Entire batch failed */
    data class Failed(
        val batchIndex: Int,
        val error: String,
        val operationIds: List<String>,
        val retryable: Boolean
    ) : BatchResult()
}

/**
 * Information about a failed operation.
 */
data class FailedOperation(
    val operationId: String,
    val error: String,
    val instructionIndex: Int
)

/**
 * Events emitted during batch execution.
 */
sealed class BatchEvent {
    /** Batch execution started */
    data class Started(val totalBatches: Int, val totalOperations: Int) : BatchEvent()
    
    /** Individual batch started */
    data class BatchStarted(val batchIndex: Int, val operationCount: Int) : BatchEvent()
    
    /** Individual batch completed */
    data class BatchCompleted(val result: BatchResult) : BatchEvent()
    
    /** All batches completed */
    data class AllCompleted(
        val successful: Int,
        val failed: Int,
        val totalTime: Long
    ) : BatchEvent()
    
    /** Progress update */
    data class Progress(
        val completedBatches: Int,
        val totalBatches: Int,
        val completedOperations: Int,
        val totalOperations: Int
    ) : BatchEvent()
}

/**
 * Configuration for the batching engine.
 */
data class BatchConfig(
    /** Maximum compute units per transaction */
    val maxComputeUnitsPerTx: Int = 1_400_000,
    
    /** Maximum instructions per transaction */
    val maxInstructionsPerTx: Int = 64,
    
    /** Maximum transaction size in bytes */
    val maxTxSizeBytes: Int = 1232,
    
    /** Default priority fee in microlamports per CU */
    val defaultPriorityFee: Long = 1000,
    
    /** Whether to add compute budget instructions */
    val addComputeBudget: Boolean = true,
    
    /** Whether to continue on batch failure */
    val continueOnFailure: Boolean = true,
    
    /** Delay between batch submissions (ms) */
    val batchDelayMs: Long = 100,
    
    /** Whether to simulate before submitting */
    val simulateFirst: Boolean = true,
    
    /** Whether to optimize instruction ordering */
    val optimizeOrdering: Boolean = true
)

/**
 * Strategies for batching operations.
 */
enum class BatchStrategy {
    /** Maximize operations per transaction (minimize transaction count) */
    MAXIMIZE_BATCH_SIZE,
    
    /** Prioritize high-priority operations first */
    PRIORITY_FIRST,
    
    /** Group similar operations together */
    GROUP_BY_TYPE,
    
    /** Minimize compute units per batch for reliability */
    MINIMIZE_COMPUTE
}
