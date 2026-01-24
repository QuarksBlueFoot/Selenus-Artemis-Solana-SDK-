package com.selenus.artemis.tx

import kotlin.math.min
import kotlin.random.Random

/**
 * Artemis Transaction Retry Pipeline
 * 
 * Production-grade transaction retry mechanism that goes far beyond
 * Solana Mobile SDK's basic send-and-confirm. Features:
 * 
 * - Exponential backoff with jitter
 * - Blockhash refresh on expiry
 * - Automatic priority fee escalation
 * - Nonce verification for durable transactions
 * - Configurable retry policies
 * - Detailed retry telemetry
 * 
 * The pipeline handles transient failures gracefully while avoiding
 * duplicate transaction submission.
 */
object RetryPipeline {
    
    /** Default maximum retry attempts */
    const val DEFAULT_MAX_RETRIES = 5
    
    /** Default initial backoff in milliseconds */
    const val DEFAULT_INITIAL_BACKOFF_MS = 500L
    
    /** Maximum backoff interval */
    const val MAX_BACKOFF_MS = 30_000L
    
    /** Fee escalation multiplier per retry */
    const val FEE_ESCALATION_MULTIPLIER = 1.5
    
    /**
     * Retry policy configuration.
     */
    data class RetryPolicy(
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
        val maxBackoffMs: Long = MAX_BACKOFF_MS,
        val backoffMultiplier: Double = 2.0,
        val jitterPercent: Int = 20,
        val escalateFees: Boolean = true,
        val feeEscalationMultiplier: Double = FEE_ESCALATION_MULTIPLIER,
        val maxFeeEscalations: Int = 3,
        val refreshBlockhash: Boolean = true,
        val verifyNonce: Boolean = true
    )
    
    /**
     * Retry result.
     */
    sealed interface RetryResult<T> {
        /** Successfully completed after retries */
        data class Success<T>(
            val result: T,
            val attempts: Int,
            val totalTimeMs: Long,
            val feeEscalations: Int
        ) : RetryResult<T>
        
        /** All retries exhausted */
        data class Exhausted<T>(
            val lastError: RetryError,
            val attempts: Int,
            val totalTimeMs: Long,
            val errors: List<RetryError>
        ) : RetryResult<T>
        
        /** Non-retryable error encountered */
        data class Failed<T>(
            val error: RetryError,
            val attempts: Int,
            val reason: String
        ) : RetryResult<T>
    }
    
    /**
     * Error types encountered during retry.
     */
    sealed interface RetryError {
        val message: String
        val retryable: Boolean
        
        /** Network connectivity issue */
        data class NetworkError(
            override val message: String,
            val cause: Throwable?
        ) : RetryError {
            override val retryable = true
        }
        
        /** RPC returned an error */
        data class RpcError(
            override val message: String,
            val code: Int?
        ) : RetryError {
            override val retryable: Boolean
                get() = code == null || code in RETRYABLE_RPC_CODES
                
            companion object {
                val RETRYABLE_RPC_CODES = setOf(
                    -32005, // Node is behind
                    -32007, // Node is unhealthy
                    -32009, // Slot skipped
                    429     // Rate limited
                )
            }
        }
        
        /** Transaction simulation failed */
        data class SimulationError(
            override val message: String,
            val logs: List<String>?
        ) : RetryError {
            override val retryable = false
        }
        
        /** Blockhash expired */
        data class BlockhashExpired(
            override val message: String = "Blockhash not found or expired"
        ) : RetryError {
            override val retryable = true // Retryable with new blockhash
        }
        
        /** Nonce was advanced (for durable transactions) */
        data class NonceAdvanced(
            override val message: String = "Nonce has been advanced"
        ) : RetryError {
            override val retryable = false // Transaction is stale
        }
        
        /** Insufficient funds */
        data class InsufficientFunds(
            override val message: String
        ) : RetryError {
            override val retryable = false
        }
        
        /** Transaction already processed */
        data class AlreadyProcessed(
            override val message: String = "Transaction already processed"
        ) : RetryError {
            override val retryable = false
        }
        
        /** Unknown error */
        data class Unknown(
            override val message: String,
            val cause: Throwable?
        ) : RetryError {
            override val retryable = true
        }
    }
    
    /**
     * Retry telemetry for monitoring.
     */
    data class RetryTelemetry(
        val attemptNumber: Int,
        val backoffMs: Long,
        val feeEscalation: Int,
        val error: RetryError?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Interface for transaction operations.
     */
    interface TransactionOperations<T> {
        /**
         * Executes the transaction operation.
         */
        suspend fun execute(): T
        
        /**
         * Called when blockhash needs refresh.
         * Return true if blockhash was refreshed successfully.
         */
        suspend fun refreshBlockhash(): Boolean = true
        
        /**
         * Called when fees should be escalated.
         * Return the new priority rate if escalation was applied.
         */
        suspend fun escalateFees(multiplier: Double): Long? = null
        
        /**
         * For durable transactions: verifies the nonce hasn't advanced.
         */
        suspend fun verifyNonce(): Boolean = true
    }
    
    /**
     * Listener for retry events.
     */
    interface RetryListener {
        fun onRetryAttempt(telemetry: RetryTelemetry) {}
        fun onFeeEscalation(newRate: Long) {}
        fun onBlockhashRefresh() {}
    }
    
    // ========================================================================
    // Retry Execution
    // ========================================================================
    
    /**
     * Executes an operation with retry policy.
     */
    suspend fun <T> execute(
        operations: TransactionOperations<T>,
        policy: RetryPolicy = RetryPolicy(),
        listener: RetryListener? = null
    ): RetryResult<T> {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<RetryError>()
        var currentBackoff = policy.initialBackoffMs
        var feeEscalations = 0
        
        for (attempt in 1..policy.maxRetries) {
            try {
                val result = operations.execute()
                return RetryResult.Success(
                    result = result,
                    attempts = attempt,
                    totalTimeMs = System.currentTimeMillis() - startTime,
                    feeEscalations = feeEscalations
                )
            } catch (e: Exception) {
                val error = classifyError(e)
                errors.add(error)
                
                listener?.onRetryAttempt(RetryTelemetry(
                    attemptNumber = attempt,
                    backoffMs = currentBackoff,
                    feeEscalation = feeEscalations,
                    error = error
                ))
                
                // Check if error is retryable
                if (!error.retryable) {
                    return RetryResult.Failed(
                        error = error,
                        attempts = attempt,
                        reason = "Non-retryable error: ${error.message}"
                    )
                }
                
                // Handle blockhash expiry
                if (error is RetryError.BlockhashExpired && policy.refreshBlockhash) {
                    val refreshed = operations.refreshBlockhash()
                    if (refreshed) {
                        listener?.onBlockhashRefresh()
                    }
                }
                
                // Handle fee escalation
                if (policy.escalateFees && feeEscalations < policy.maxFeeEscalations) {
                    if (shouldEscalateFees(error)) {
                        val newRate = operations.escalateFees(policy.feeEscalationMultiplier)
                        if (newRate != null) {
                            feeEscalations++
                            listener?.onFeeEscalation(newRate)
                        }
                    }
                }
                
                // Verify nonce for durable transactions
                if (policy.verifyNonce) {
                    if (!operations.verifyNonce()) {
                        return RetryResult.Failed(
                            error = RetryError.NonceAdvanced(),
                            attempts = attempt,
                            reason = "Nonce was advanced, transaction is stale"
                        )
                    }
                }
                
                // Apply backoff before next attempt
                if (attempt < policy.maxRetries) {
                    val jitter = (currentBackoff * policy.jitterPercent / 100 *
                                 (Random.nextDouble() * 2 - 1)).toLong()
                    Thread.sleep(currentBackoff + jitter)
                    
                    currentBackoff = min(
                        (currentBackoff * policy.backoffMultiplier).toLong(),
                        policy.maxBackoffMs
                    )
                }
            }
        }
        
        return RetryResult.Exhausted(
            lastError = errors.lastOrNull() ?: RetryError.Unknown("Unknown error", null),
            attempts = policy.maxRetries,
            totalTimeMs = System.currentTimeMillis() - startTime,
            errors = errors
        )
    }
    
    /**
     * Simple retry wrapper for suspending functions.
     */
    suspend fun <T> retry(
        policy: RetryPolicy = RetryPolicy(),
        block: suspend () -> T
    ): RetryResult<T> {
        return execute(
            operations = object : TransactionOperations<T> {
                override suspend fun execute(): T = block()
            },
            policy = policy
        )
    }
    
    // ========================================================================
    // Error Classification
    // ========================================================================
    
    /**
     * Classifies an exception into a RetryError.
     */
    fun classifyError(e: Exception): RetryError {
        val message = e.message ?: "Unknown error"
        
        return when {
            // Network errors
            e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException -> {
                RetryError.NetworkError(message, e)
            }
            
            // Blockhash errors
            message.contains("blockhash not found", ignoreCase = true) ||
            message.contains("blockhash expired", ignoreCase = true) ||
            message.contains("too old", ignoreCase = true) -> {
                RetryError.BlockhashExpired()
            }
            
            // Already processed
            message.contains("already been processed", ignoreCase = true) ||
            message.contains("already processed", ignoreCase = true) -> {
                RetryError.AlreadyProcessed()
            }
            
            // Insufficient funds
            message.contains("insufficient funds", ignoreCase = true) ||
            message.contains("insufficient lamports", ignoreCase = true) -> {
                RetryError.InsufficientFunds(message)
            }
            
            // Nonce errors
            message.contains("nonce", ignoreCase = true) &&
            message.contains("advanced", ignoreCase = true) -> {
                RetryError.NonceAdvanced()
            }
            
            // Simulation errors
            message.contains("simulation", ignoreCase = true) ||
            message.contains("program error", ignoreCase = true) -> {
                RetryError.SimulationError(message, null)
            }
            
            else -> {
                RetryError.Unknown(message, e)
            }
        }
    }
    
    /**
     * Determines if fees should be escalated based on error type.
     */
    private fun shouldEscalateFees(error: RetryError): Boolean {
        return error is RetryError.BlockhashExpired ||
               error is RetryError.RpcError ||
               error is RetryError.NetworkError
    }
}

/**
 * Builder for creating retry pipelines with fluent API.
 */
class RetryPipelineBuilder<T> {
    private var maxRetries = RetryPipeline.DEFAULT_MAX_RETRIES
    private var initialBackoffMs = RetryPipeline.DEFAULT_INITIAL_BACKOFF_MS
    private var maxBackoffMs = RetryPipeline.MAX_BACKOFF_MS
    private var backoffMultiplier = 2.0
    private var jitterPercent = 20
    private var escalateFees = true
    private var feeEscalationMultiplier = RetryPipeline.FEE_ESCALATION_MULTIPLIER
    private var maxFeeEscalations = 3
    private var refreshBlockhash = true
    private var verifyNonce = true
    private var listener: RetryPipeline.RetryListener? = null
    
    fun maxRetries(n: Int) = apply { maxRetries = n }
    fun initialBackoff(ms: Long) = apply { initialBackoffMs = ms }
    fun maxBackoff(ms: Long) = apply { maxBackoffMs = ms }
    fun backoffMultiplier(m: Double) = apply { backoffMultiplier = m }
    fun jitter(percent: Int) = apply { jitterPercent = percent }
    fun escalateFees(enabled: Boolean) = apply { escalateFees = enabled }
    fun feeEscalationMultiplier(m: Double) = apply { feeEscalationMultiplier = m }
    fun maxFeeEscalations(n: Int) = apply { maxFeeEscalations = n }
    fun refreshBlockhash(enabled: Boolean) = apply { refreshBlockhash = enabled }
    fun verifyNonce(enabled: Boolean) = apply { verifyNonce = enabled }
    fun listener(l: RetryPipeline.RetryListener) = apply { listener = l }
    
    fun buildPolicy(): RetryPipeline.RetryPolicy {
        return RetryPipeline.RetryPolicy(
            maxRetries = maxRetries,
            initialBackoffMs = initialBackoffMs,
            maxBackoffMs = maxBackoffMs,
            backoffMultiplier = backoffMultiplier,
            jitterPercent = jitterPercent,
            escalateFees = escalateFees,
            feeEscalationMultiplier = feeEscalationMultiplier,
            maxFeeEscalations = maxFeeEscalations,
            refreshBlockhash = refreshBlockhash,
            verifyNonce = verifyNonce
        )
    }
    
    suspend fun execute(
        operations: RetryPipeline.TransactionOperations<T>
    ): RetryPipeline.RetryResult<T> {
        return RetryPipeline.execute(operations, buildPolicy(), listener)
    }
}

/**
 * Creates a retry pipeline builder.
 */
fun <T> retryPipeline(): RetryPipelineBuilder<T> = RetryPipelineBuilder()
