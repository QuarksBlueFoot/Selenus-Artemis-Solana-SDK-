package com.selenus.artemis.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * BatchProcessor - Intelligent batching with concurrent execution.
 * 
 * Handles large lists of items by chunking them appropriately and processing
 * concurrently while respecting rate limits and avoiding RPC overload.
 * 
 * Unlike simple chunking, this provides:
 * - Concurrent chunk processing with configurable concurrency
 * - Progress reporting
 * - Error handling strategies (fail fast, continue on error, retry)
 * - Deduplication
 * - Result aggregation
 * 
 * Example:
 * ```kotlin
 * val processor = BatchProcessor<String, AccountInfo>(
 *     chunkSize = 100,
 *     concurrency = 4
 * )
 * 
 * val results = processor.process(
 *     items = pubkeys,
 *     operation = { chunk -> rpc.getMultipleAccounts(chunk) },
 *     onProgress = { completed, total -> println("$completed / $total") }
 * )
 * ```
 */
class BatchProcessor<T, R>(
    private val chunkSize: Int = 100,
    private val concurrency: Int = 4,
    private val errorStrategy: ErrorStrategy = ErrorStrategy.FAIL_FAST
) {
    
    /**
     * Error handling strategy.
     */
    enum class ErrorStrategy {
        /** Stop processing on first error */
        FAIL_FAST,
        /** Continue processing, collect errors */
        CONTINUE_ON_ERROR,
        /** Retry failed chunks */
        RETRY
    }
    
    /**
     * Batch processing result.
     */
    data class BatchResult<R>(
        val successful: List<R>,
        val failed: List<ChunkError>,
        val totalItems: Int,
        val successfulItems: Int,
        val durationMs: Long
    ) {
        val hasErrors: Boolean get() = failed.isNotEmpty()
        val allSuccessful: Boolean get() = failed.isEmpty()
    }
    
    /**
     * Error for a specific chunk.
     */
    data class ChunkError(
        val chunkIndex: Int,
        val error: Throwable,
        val items: Int
    )
    
    /**
     * Process items in batches concurrently.
     */
    suspend fun process(
        items: List<T>,
        operation: suspend (List<T>) -> List<R>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): BatchResult<R> {
        val startTime = System.currentTimeMillis()
        
        if (items.isEmpty()) {
            return BatchResult(
                successful = emptyList(),
                failed = emptyList(),
                totalItems = 0,
                successfulItems = 0,
                durationMs = 0
            )
        }
        
        val chunks = items.chunked(chunkSize)
        val totalChunks = chunks.size
        val semaphore = Semaphore(concurrency)
        
        val results = mutableListOf<R>()
        val errors = mutableListOf<ChunkError>()
        var completedItems = 0
        val resultsMutex = Mutex()
        
        coroutineScope {
            chunks.mapIndexed { index, chunk ->
                async {
                    semaphore.withPermit {
                        try {
                            val chunkResults = operation(chunk)
                            resultsMutex.withLock {
                                results.addAll(chunkResults)
                                completedItems += chunk.size
                                onProgress?.invoke(completedItems, items.size)
                            }
                        } catch (e: Exception) {
                            when (errorStrategy) {
                                ErrorStrategy.FAIL_FAST -> throw e
                                ErrorStrategy.CONTINUE_ON_ERROR -> {
                                    resultsMutex.withLock {
                                        errors.add(ChunkError(index, e, chunk.size))
                                        completedItems += chunk.size
                                        onProgress?.invoke(completedItems, items.size)
                                    }
                                }
                                ErrorStrategy.RETRY -> {
                                    // Simple retry logic - could be more sophisticated
                                    repeat(3) { attempt ->
                                        try {
                                            delay((attempt + 1) * 500L)
                                            val chunkResults = operation(chunk)
                                            resultsMutex.withLock {
                                                results.addAll(chunkResults)
                                                completedItems += chunk.size
                                                onProgress?.invoke(completedItems, items.size)
                                            }
                                            return@async
                                        } catch (retryError: Exception) {
                                            if (attempt == 2) {
                                                resultsMutex.withLock {
                                                    errors.add(ChunkError(index, retryError, chunk.size))
                                                    completedItems += chunk.size
                                                    onProgress?.invoke(completedItems, items.size)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        
        return BatchResult(
            successful = results.toList(),
            failed = errors.toList(),
            totalItems = items.size,
            successfulItems = results.size,
            durationMs = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * Process items with deduplication.
     */
    suspend fun processUnique(
        items: List<T>,
        operation: suspend (List<T>) -> List<R>,
        keySelector: (T) -> Any = { it as Any },
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): BatchResult<R> {
        val uniqueItems = items.distinctBy(keySelector)
        return process(uniqueItems, operation, onProgress)
    }
    
    companion object {
        /**
         * Simple helper for getMultipleAccounts patterns.
         */
        fun <T> accounts(
            chunkSize: Int = 100,
            concurrency: Int = 4
        ): BatchProcessor<T, T> = BatchProcessor(chunkSize, concurrency)
    }
}

/**
 * Extension function for simple batch processing.
 */
suspend fun <T, R> List<T>.processBatch(
    chunkSize: Int = 100,
    concurrency: Int = 4,
    operation: suspend (List<T>) -> List<R>
): List<R> {
    val processor = BatchProcessor<T, R>(chunkSize, concurrency)
    return processor.process(this, operation).successful
}

/**
 * Extension function for batch processing with progress.
 */
suspend fun <T, R> List<T>.processBatchWithProgress(
    chunkSize: Int = 100,
    concurrency: Int = 4,
    onProgress: (completed: Int, total: Int) -> Unit,
    operation: suspend (List<T>) -> List<R>
): BatchProcessor.BatchResult<R> {
    val processor = BatchProcessor<T, R>(chunkSize, concurrency)
    return processor.process(this, operation, onProgress)
}
