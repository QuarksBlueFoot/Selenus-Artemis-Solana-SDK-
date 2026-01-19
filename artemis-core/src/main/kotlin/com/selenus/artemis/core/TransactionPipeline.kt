package com.selenus.artemis.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TransactionPipeline - Composable, interceptor-based transaction processing.
 * 
 * Inspired by OkHttp interceptors but designed specifically for Solana transactions.
 * Allows adding cross-cutting concerns like logging, retry, simulation, etc.
 * 
 * Example:
 * ```kotlin
 * val pipeline = TransactionPipeline.Builder()
 *     .addInterceptor(LoggingInterceptor())
 *     .addInterceptor(SimulationInterceptor(rpc))
 *     .addInterceptor(RetryInterceptor(maxRetries = 3))
 *     .addInterceptor(PriorityFeeInterceptor(computeUnits = 200_000))
 *     .build()
 *     
 * val result = pipeline.execute(transaction)
 * ```
 */
class TransactionPipeline private constructor(
    private val interceptors: List<Interceptor>
) {
    
    /**
     * Context passed through the interceptor chain.
     */
    data class Context(
        val transactionData: ByteArray,
        val metadata: MutableMap<String, Any> = mutableMapOf(),
        val startTimeNanos: Long = System.nanoTime()
    ) {
        /** Recent blockhash used in this transaction */
        var blockhash: String? = null
        
        /** Signatures after signing */
        var signatures: List<String> = emptyList()
        
        /** Result signature after sending */
        var resultSignature: String? = null
        
        /** Simulation logs if available */
        var simulationLogs: List<String>? = null
        
        /** Compute units consumed (estimated or actual) */
        var computeUnitsConsumed: Long? = null
        
        /** Priority fee applied */
        var priorityFee: Long? = null
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Context) return false
            return transactionData.contentEquals(other.transactionData)
        }
        
        override fun hashCode(): Int = transactionData.contentHashCode()
    }
    
    /**
     * Result of pipeline execution.
     */
    sealed class Result {
        data class Success(
            val signature: String,
            val context: Context,
            val durationMs: Long
        ) : Result()
        
        data class Failure(
            val error: SolanaError,
            val context: Context,
            val durationMs: Long
        ) : Result()
    }
    
    /**
     * Interceptor interface - each interceptor can modify or act on the transaction.
     */
    interface Interceptor {
        /**
         * Intercept the transaction processing.
         * 
         * @param chain The chain to proceed with processing
         * @return The result of processing
         */
        suspend fun intercept(chain: Chain): Result
    }
    
    /**
     * Chain interface for proceeding with the next interceptor.
     */
    interface Chain {
        val context: Context
        
        /**
         * Proceed to the next interceptor.
         */
        suspend fun proceed(context: Context): Result
    }
    
    private class RealChain(
        override val context: Context,
        private val interceptors: List<Interceptor>,
        private val index: Int
    ) : Chain {
        override suspend fun proceed(context: Context): Result {
            if (index >= interceptors.size) {
                // End of chain - this shouldn't happen if properly configured
                return Result.Failure(
                    SolanaError.Unknown("No terminal interceptor configured"),
                    context,
                    (System.nanoTime() - context.startTimeNanos) / 1_000_000
                )
            }
            val next = RealChain(context, interceptors, index + 1)
            val interceptor = interceptors[index]
            return interceptor.intercept(next)
        }
    }
    
    /**
     * Execute the pipeline with the given transaction data.
     */
    suspend fun execute(transactionData: ByteArray): Result {
        val context = Context(transactionData)
        val chain = RealChain(context, interceptors, 0)
        return chain.proceed(context)
    }
    
    /**
     * Builder for TransactionPipeline.
     */
    class Builder {
        private val interceptors = mutableListOf<Interceptor>()
        
        fun addInterceptor(interceptor: Interceptor): Builder {
            interceptors.add(interceptor)
            return this
        }
        
        fun build(): TransactionPipeline {
            return TransactionPipeline(interceptors.toList())
        }
    }
    
    companion object {
        fun builder() = Builder()
    }
}

/**
 * Logging interceptor for debugging.
 */
class LoggingInterceptor(
    private val tag: String = "TxPipeline",
    private val logger: (String) -> Unit = { println(it) }
) : TransactionPipeline.Interceptor {
    
    override suspend fun intercept(chain: TransactionPipeline.Chain): TransactionPipeline.Result {
        val context = chain.context
        logger("[$tag] Starting transaction (${context.transactionData.size} bytes)")
        
        val result = chain.proceed(context)
        
        when (result) {
            is TransactionPipeline.Result.Success -> {
                logger("[$tag] Success: ${result.signature} (${result.durationMs}ms)")
            }
            is TransactionPipeline.Result.Failure -> {
                logger("[$tag] Failed: ${result.error.message} (${result.durationMs}ms)")
            }
        }
        
        return result
    }
}

/**
 * Retry interceptor with exponential backoff.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 5000,
    private val retryOn: (SolanaError) -> Boolean = { 
        it is SolanaError.NetworkError && it.isRetryable ||
        it is SolanaError.BlockhashExpired ||
        it is SolanaError.Timeout
    }
) : TransactionPipeline.Interceptor {
    
    override suspend fun intercept(chain: TransactionPipeline.Chain): TransactionPipeline.Result {
        var lastResult: TransactionPipeline.Result? = null
        var delayMs = initialDelayMs
        
        repeat(maxRetries + 1) { attempt ->
            val result = chain.proceed(chain.context)
            
            if (result is TransactionPipeline.Result.Success) {
                return result
            }
            
            lastResult = result
            val failure = result as TransactionPipeline.Result.Failure
            
            if (!retryOn(failure.error) || attempt >= maxRetries) {
                return result
            }
            
            delay(delayMs)
            delayMs = minOf(delayMs * 2, maxDelayMs)
        }
        
        return lastResult!!
    }
}

/**
 * Rate limiting interceptor to prevent overwhelming RPC nodes.
 */
class RateLimitInterceptor(
    private val maxRequestsPerSecond: Int = 10
) : TransactionPipeline.Interceptor {
    
    private val mutex = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()
    
    override suspend fun intercept(chain: TransactionPipeline.Chain): TransactionPipeline.Result {
        mutex.withLock {
            val now = System.currentTimeMillis()
            
            // Remove old timestamps
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() > 1000) {
                requestTimestamps.removeFirst()
            }
            
            // Wait if we've exceeded the rate limit
            if (requestTimestamps.size >= maxRequestsPerSecond) {
                val waitTime = 1000 - (now - requestTimestamps.first())
                if (waitTime > 0) {
                    delay(waitTime)
                }
            }
            
            requestTimestamps.addLast(System.currentTimeMillis())
        }
        
        return chain.proceed(chain.context)
    }
}

/**
 * Timing interceptor for performance monitoring.
 */
class TimingInterceptor(
    private val onTiming: (String, Long) -> Unit = { name, ms -> }
) : TransactionPipeline.Interceptor {
    
    override suspend fun intercept(chain: TransactionPipeline.Chain): TransactionPipeline.Result {
        val start = System.nanoTime()
        val result = chain.proceed(chain.context)
        val durationMs = (System.nanoTime() - start) / 1_000_000
        
        when (result) {
            is TransactionPipeline.Result.Success -> {
                onTiming("tx_success", durationMs)
            }
            is TransactionPipeline.Result.Failure -> {
                onTiming("tx_failure", durationMs)
            }
        }
        
        return result
    }
}
