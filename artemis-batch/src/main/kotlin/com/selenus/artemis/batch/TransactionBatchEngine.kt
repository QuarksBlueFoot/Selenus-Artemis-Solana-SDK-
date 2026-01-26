/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Intelligent Batching Engine - Main Implementation
 *
 * This is a first-of-its-kind feature in Solana SDKs. No competitor
 * (solana-kmp, Sol4k, Solana Mobile) offers automatic transaction batching.
 */

package com.selenus.artemis.batch

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.tx.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable
import java.util.UUID

/**
 * Intelligent Batching Engine for Solana transactions.
 * 
 * This engine automatically combines multiple operations into optimized
 * transactions, reducing costs and improving efficiency. It handles:
 * 
 * - Compute unit optimization
 * - Instruction ordering
 * - Priority fee management
 * - Batch planning and execution
 * 
 * ## Usage
 * ```kotlin
 * val engine = TransactionBatchEngine()
 * 
 * // Create operations to batch
 * val operations = listOf(
 *     engine.createTransferOperation("Alice", 1_000_000),
 *     engine.createTransferOperation("Bob", 2_000_000),
 *     engine.createTokenTransferOperation(tokenMint, "Carol", 100),
 *     // ... more operations
 * )
 * 
 * // Plan the batches
 * val plan = engine.plan(operations)
 * println("${plan.batches.size} batches needed")
 * println("Savings: ${plan.savingsPercent}%")
 * 
 * // Execute the plan
 * engine.events.collect { event ->
 *     when (event) {
 *         is BatchEvent.Progress -> updateProgressUI(event)
 *         is BatchEvent.BatchCompleted -> handleResult(event.result)
 *     }
 * }
 * 
 * val results = engine.execute(plan, feePayer, signer)
 * ```
 */
class TransactionBatchEngine(
    private val config: BatchConfig = BatchConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Closeable {
    
    private val _events = MutableSharedFlow<BatchEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BatchEvent> = _events.asSharedFlow()
    
    /**
     * Create a batch operation from instructions.
     */
    fun createOperation(
        instructions: List<Instruction>,
        description: String,
        estimatedComputeUnits: Int = BatchOperation.DEFAULT_COMPUTE_UNITS,
        priority: Int = 0,
        isIdempotent: Boolean = true
    ): BatchOperation {
        return BatchOperation(
            id = UUID.randomUUID().toString(),
            instructions = instructions,
            estimatedComputeUnits = estimatedComputeUnits,
            description = description,
            priority = priority,
            isIdempotent = isIdempotent
        )
    }
    
    /**
     * Plan how to batch a list of operations.
     * 
     * This analyzes the operations and determines the optimal way to
     * combine them into transactions.
     * 
     * @param operations The operations to batch
     * @param strategy The batching strategy to use
     * @return A plan describing how the operations will be batched
     */
    fun plan(
        operations: List<BatchOperation>,
        strategy: BatchStrategy = BatchStrategy.MAXIMIZE_BATCH_SIZE
    ): BatchPlan {
        if (operations.isEmpty()) {
            return BatchPlan(
                batches = emptyList(),
                totalOperations = 0,
                totalComputeUnits = 0,
                estimatedCostLamports = 0,
                savingsLamports = 0,
                savingsPercent = 0.0
            )
        }
        
        // Sort operations based on strategy
        val sortedOperations = when (strategy) {
            BatchStrategy.PRIORITY_FIRST -> operations.sortedByDescending { it.priority }
            BatchStrategy.MINIMIZE_COMPUTE -> operations.sortedBy { it.estimatedComputeUnits }
            else -> operations
        }
        
        val batches = mutableListOf<PlannedBatch>()
        var currentOperations = mutableListOf<BatchOperation>()
        var currentComputeUnits = 0
        var currentInstructionCount = 0
        
        for (operation in sortedOperations) {
            val operationInstructions = operation.instructions.size
            
            // Check if adding this operation would exceed limits
            val wouldExceedCompute = currentComputeUnits + operation.estimatedComputeUnits > config.maxComputeUnitsPerTx
            val wouldExceedInstructions = currentInstructionCount + operationInstructions > config.maxInstructionsPerTx - 2 // Leave room for compute budget
            
            if (wouldExceedCompute || wouldExceedInstructions) {
                // Finalize current batch
                if (currentOperations.isNotEmpty()) {
                    batches.add(createBatch(batches.size, currentOperations))
                }
                
                // Start new batch
                currentOperations = mutableListOf(operation)
                currentComputeUnits = operation.estimatedComputeUnits
                currentInstructionCount = operationInstructions
            } else {
                // Add to current batch
                currentOperations.add(operation)
                currentComputeUnits += operation.estimatedComputeUnits
                currentInstructionCount += operationInstructions
            }
        }
        
        // Add final batch
        if (currentOperations.isNotEmpty()) {
            batches.add(createBatch(batches.size, currentOperations))
        }
        
        // Calculate totals
        val totalOperations = operations.size
        val totalComputeUnits = operations.sumOf { it.estimatedComputeUnits }
        val batchedCost = batches.sumOf { it.estimatedCostLamports }
        val individualCost = totalOperations * calculateBaseFee(config.maxComputeUnitsPerTx / 10) // Estimate individual tx cost
        val savings = individualCost - batchedCost
        val savingsPercent = if (individualCost > 0) {
            (savings.toDouble() / individualCost.toDouble()) * 100
        } else 0.0
        
        return BatchPlan(
            batches = batches,
            totalOperations = totalOperations,
            totalComputeUnits = totalComputeUnits,
            estimatedCostLamports = batchedCost,
            savingsLamports = savings,
            savingsPercent = savingsPercent
        )
    }
    
    private fun createBatch(index: Int, operations: List<BatchOperation>): PlannedBatch {
        val allInstructions = operations.flatMap { it.instructions }
        val totalComputeUnits = operations.sumOf { it.estimatedComputeUnits }
        val cost = calculateBaseFee(totalComputeUnits)
        val atCapacity = totalComputeUnits > config.maxComputeUnitsPerTx * 0.9
        
        return PlannedBatch(
            index = index,
            operations = operations,
            instructions = allInstructions,
            computeUnits = totalComputeUnits,
            estimatedCostLamports = cost,
            atCapacity = atCapacity
        )
    }
    
    private fun calculateBaseFee(computeUnits: Int): Long {
        // Base fee (5000 lamports) + priority fee
        val baseFee = 5000L
        val priorityFee = (config.defaultPriorityFee * computeUnits) / 1_000_000
        return baseFee + priorityFee
    }
    
    /**
     * Build transactions from a batch plan.
     * 
     * @param plan The batch plan
     * @param feePayer The fee payer pubkey
     * @param recentBlockhash The recent blockhash to use
     * @return List of transactions ready to sign
     */
    fun buildTransactions(
        plan: BatchPlan,
        feePayer: Pubkey,
        recentBlockhash: String
    ): List<Transaction> {
        return plan.batches.map { batch ->
            val tx = Transaction(
                feePayer = feePayer,
                recentBlockhash = recentBlockhash,
                instructions = batch.instructions
            )
            
            // Add compute budget instructions if configured
            if (config.addComputeBudget) {
                addComputeBudgetInstructions(tx, batch.computeUnits)
            }
            
            tx
        }
    }
    
    private fun addComputeBudgetInstructions(tx: Transaction, computeUnits: Int) {
        // Compute Budget Program ID
        val computeBudgetProgram = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")
        
        // SetComputeUnitLimit instruction (discriminator = 2)
        val setLimitData = ByteArray(5).apply {
            this[0] = 2.toByte() // discriminator
            val units = computeUnits.coerceAtMost(config.maxComputeUnitsPerTx)
            this[1] = (units and 0xFF).toByte()
            this[2] = ((units shr 8) and 0xFF).toByte()
            this[3] = ((units shr 16) and 0xFF).toByte()
            this[4] = ((units shr 24) and 0xFF).toByte()
        }
        
        val setLimitIx = Instruction(
            programId = computeBudgetProgram,
            accounts = emptyList(),
            data = setLimitData
        )
        
        // SetComputeUnitPrice instruction (discriminator = 3)
        val priorityFee = config.defaultPriorityFee
        val setPriceData = ByteArray(9).apply {
            this[0] = 3.toByte() // discriminator
            for (i in 0..7) {
                this[i + 1] = ((priorityFee shr (i * 8)) and 0xFF).toByte()
            }
        }
        
        val setPriceIx = Instruction(
            programId = computeBudgetProgram,
            accounts = emptyList(),
            data = setPriceData
        )
        
        // Add at the beginning of the transaction
        tx.instructions.add(0, setLimitIx)
        tx.instructions.add(1, setPriceIx)
    }
    
    /**
     * Execute a batch plan using the provided executor.
     * 
     * @param plan The batch plan to execute
     * @param executor Function to execute each transaction
     * @return Results for each batch
     */
    suspend fun execute(
        plan: BatchPlan,
        executor: suspend (Transaction, Int) -> BatchResult
    ): List<BatchResult> {
        if (plan.batches.isEmpty()) {
            return emptyList()
        }
        
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<BatchResult>()
        var completedOperations = 0
        
        _events.emit(BatchEvent.Started(plan.batches.size, plan.totalOperations))
        
        for ((index, batch) in plan.batches.withIndex()) {
            _events.emit(BatchEvent.BatchStarted(index, batch.operations.size))
            
            // Note: The actual transaction building and signing should be done by the caller
            // We emit the batch for execution
            val result = try {
                // Create a placeholder transaction for the executor
                val tx = Transaction()
                for (instruction in batch.instructions) {
                    tx.addInstruction(instruction)
                }
                executor(tx, index)
            } catch (e: Exception) {
                BatchResult.Failed(
                    batchIndex = index,
                    error = e.message ?: "Unknown error",
                    operationIds = batch.operations.map { it.id },
                    retryable = true
                )
            }
            
            results.add(result)
            _events.emit(BatchEvent.BatchCompleted(result))
            
            // Update progress
            completedOperations += batch.operations.size
            _events.emit(BatchEvent.Progress(
                completedBatches = index + 1,
                totalBatches = plan.batches.size,
                completedOperations = completedOperations,
                totalOperations = plan.totalOperations
            ))
            
            // Check if we should continue
            if (result is BatchResult.Failed && !config.continueOnFailure) {
                break
            }
            
            // Delay between batches
            if (index < plan.batches.size - 1) {
                delay(config.batchDelayMs)
            }
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        val successful = results.count { it is BatchResult.Success }
        val failed = results.count { it is BatchResult.Failed }
        
        _events.emit(BatchEvent.AllCompleted(successful, failed, elapsed))
        
        return results
    }
    
    /**
     * Calculate the optimal number of batches for a set of operations.
     */
    fun estimateBatchCount(operations: List<BatchOperation>): Int {
        val totalCompute = operations.sumOf { it.estimatedComputeUnits }
        val totalInstructions = operations.sumOf { it.instructions.size }
        
        val computeBatches = (totalCompute + config.maxComputeUnitsPerTx - 1) / config.maxComputeUnitsPerTx
        val instructionBatches = (totalInstructions + config.maxInstructionsPerTx - 3) / (config.maxInstructionsPerTx - 2)
        
        return maxOf(computeBatches, instructionBatches).coerceAtLeast(1)
    }
    
    override fun close() {
        // No resources to clean up currently
    }
}
