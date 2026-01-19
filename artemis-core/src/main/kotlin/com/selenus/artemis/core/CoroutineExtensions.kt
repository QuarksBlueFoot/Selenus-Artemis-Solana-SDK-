package com.selenus.artemis.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * CoroutineScope extensions for Solana operations.
 * 
 * Provides scoped, cancellable transaction and RPC operations that
 * integrate cleanly with Android's lifecycle-aware coroutines.
 * 
 * Example with Android ViewModel:
 * ```kotlin
 * class WalletViewModel : ViewModel() {
 *     fun sendTransaction(tx: ByteArray) {
 *         viewModelScope.launchTransaction(tx) {
 *             onStart { showLoading() }
 *             onSuccess { sig -> showSuccess(sig) }
 *             onError { error -> showError(error) }
 *             onComplete { hideLoading() }
 *         }
 *     }
 * }
 * ```
 */

/**
 * Configuration for transaction execution.
 */
class TransactionConfig {
    var maxRetries: Int = 3
    var retryDelayMs: Long = 500
    var timeoutMs: Long = 60000
    var confirmationLevel: ConfirmationLevel = ConfirmationLevel.CONFIRMED
    
    enum class ConfirmationLevel {
        PROCESSED,
        CONFIRMED,
        FINALIZED
    }
    
    internal var onStart: (() -> Unit)? = null
    internal var onSuccess: ((String) -> Unit)? = null
    internal var onError: ((SolanaError) -> Unit)? = null
    internal var onProgress: ((TransactionProgress) -> Unit)? = null
    internal var onComplete: (() -> Unit)? = null
    
    fun onStart(block: () -> Unit) { onStart = block }
    fun onSuccess(block: (String) -> Unit) { onSuccess = block }
    fun onError(block: (SolanaError) -> Unit) { onError = block }
    fun onProgress(block: (TransactionProgress) -> Unit) { onProgress = block }
    fun onComplete(block: () -> Unit) { onComplete = block }
}

/**
 * Transaction progress state.
 */
sealed class TransactionProgress {
    object Signing : TransactionProgress()
    object Sending : TransactionProgress()
    data class Confirming(val confirmations: Int, val target: Int) : TransactionProgress()
    data class Confirmed(val signature: String) : TransactionProgress()
    data class Failed(val error: SolanaError) : TransactionProgress()
}

/**
 * Executor interface for transaction operations.
 */
interface TransactionExecutor {
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun send(signedData: ByteArray): String
    suspend fun confirm(signature: String, level: TransactionConfig.ConfirmationLevel): Boolean
}

/**
 * Launch a transaction with progress callbacks.
 */
fun CoroutineScope.launchTransaction(
    transactionData: ByteArray,
    executor: TransactionExecutor,
    config: TransactionConfig.() -> Unit
): Job {
    val cfg = TransactionConfig().apply(config)
    
    return launch {
        cfg.onStart?.invoke()
        
        try {
            // Signing
            cfg.onProgress?.invoke(TransactionProgress.Signing)
            val signed = executor.sign(transactionData)
            
            // Sending with retries
            cfg.onProgress?.invoke(TransactionProgress.Sending)
            var signature: String? = null
            var lastError: SolanaError? = null
            
            repeat(cfg.maxRetries) { attempt ->
                try {
                    signature = executor.send(signed)
                    return@repeat
                } catch (e: Exception) {
                    lastError = when (e) {
                        is SolanaException -> e.error
                        else -> SolanaError.NetworkError(e.message ?: "Unknown", true, e)
                    }
                    if (attempt < cfg.maxRetries - 1) {
                        delay(cfg.retryDelayMs * (attempt + 1))
                    }
                }
            }
            
            if (signature == null) {
                cfg.onProgress?.invoke(TransactionProgress.Failed(lastError!!))
                cfg.onError?.invoke(lastError!!)
                return@launch
            }
            
            // Confirming
            val sig = signature!!
            cfg.onProgress?.invoke(TransactionProgress.Confirming(0, 32))
            
            withTimeout(cfg.timeoutMs) {
                val confirmed = executor.confirm(sig, cfg.confirmationLevel)
                if (confirmed) {
                    cfg.onProgress?.invoke(TransactionProgress.Confirmed(sig))
                    cfg.onSuccess?.invoke(sig)
                } else {
                    val error = SolanaError.TransactionFailed("Confirmation failed", sig)
                    cfg.onProgress?.invoke(TransactionProgress.Failed(error))
                    cfg.onError?.invoke(error)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val error = SolanaError.Timeout("Transaction timed out", cfg.timeoutMs)
            cfg.onProgress?.invoke(TransactionProgress.Failed(error))
            cfg.onError?.invoke(error)
        } catch (e: CancellationException) {
            throw e // Don't catch cancellation
        } catch (e: Exception) {
            val error = when (e) {
                is SolanaException -> e.error
                else -> SolanaError.Unknown(e.message ?: "Unknown error", e)
            }
            cfg.onProgress?.invoke(TransactionProgress.Failed(error))
            cfg.onError?.invoke(error)
        } finally {
            cfg.onComplete?.invoke()
        }
    }
}

/**
 * Execute a transaction with Result return type.
 */
suspend fun <T> withSolanaContext(
    timeoutMs: Long = 30000L,
    retries: Int = 3,
    retryDelayMs: Long = 500L,
    isRetryable: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): SolanaResult<T, SolanaError> {
    var lastError: SolanaError? = null
    
    repeat(retries) { attempt ->
        try {
            val result = withTimeout(timeoutMs) { block() }
            return SolanaResult.success(result)
        } catch (e: TimeoutCancellationException) {
            lastError = SolanaError.Timeout("Operation timed out", timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SolanaException) {
            lastError = e.error
            if (!isRetryable(e)) {
                return SolanaResult.failure(e.error)
            }
        } catch (e: Exception) {
            lastError = SolanaError.Unknown(e.message ?: "Unknown error", e)
            if (!isRetryable(e)) {
                return SolanaResult.failure(lastError!!)
            }
        }
        
        if (attempt < retries - 1) {
            delay(retryDelayMs * (attempt + 1))
        }
    }
    
    return SolanaResult.failure(lastError!!)
}

/**
 * Execute multiple operations in parallel, collecting results.
 */
suspend fun <T, R> List<T>.parallelMap(
    concurrency: Int = 10,
    transform: suspend (T) -> R
): List<R> {
    val semaphore = Semaphore(concurrency)
    return coroutineScope {
        this@parallelMap.map { item ->
            async {
                semaphore.withPermit {
                    transform(item)
                }
            }
        }.awaitAll()
    }
}

/**
 * Execute operations with automatic retry on failure.
 */
suspend fun <T> retry(
    times: Int = 3,
    initialDelayMs: Long = 100,
    maxDelayMs: Long = 5000,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldRetry(e)) throw e
            delay(currentDelay)
            currentDelay = minOf((currentDelay * factor).toLong(), maxDelayMs)
        }
    }
    return block() // Last attempt
}

/**
 * Debounce operator for rapid successive calls.
 */
class Debouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long = 500L
) {
    private var job: Job? = null
    
    fun debounce(action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            action()
        }
    }
    
    fun cancel() {
        job?.cancel()
    }
}

/**
 * Throttle operator for rate limiting.
 */
class Throttler(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 1000L
) {
    private var lastExecution = 0L
    private val mutex = Mutex()
    
    suspend fun <T> throttle(action: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastExecution
            if (timeSinceLast < intervalMs) {
                delay(intervalMs - timeSinceLast)
            }
            lastExecution = System.currentTimeMillis()
        }
        return action()
    }
}
