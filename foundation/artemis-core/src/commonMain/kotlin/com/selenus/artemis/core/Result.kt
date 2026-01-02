package com.selenus.artemis.core

/**
 * Type-safe Result wrapper for Solana operations.
 * 
 * Unlike exceptions, this forces callers to handle both success and failure cases,
 * making error handling explicit and composable. Inspired by Rust's Result<T, E>.
 * 
 * Example:
 * ```kotlin
 * when (val result = rpc.getBalance(owner)) {
 *     is SolanaResult.Success -> println("Balance: ${result.value}")
 *     is SolanaResult.Failure -> println("Error: ${result.error.message}")
 * }
 * ```
 */
sealed class SolanaResult<out T, out E : SolanaError> {
    
    data class Success<T>(val value: T) : SolanaResult<T, Nothing>()
    
    data class Failure<E : SolanaError>(val error: E) : SolanaResult<Nothing, E>()
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    /**
     * Get the success value or null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    /**
     * Get the error or null.
     */
    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }
    
    /**
     * Get the success value or throw.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.toException()
    }
    
    /**
     * Get the success value or a default.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default
    }
    
    /**
     * Get the success value or compute a default.
     */
    inline fun getOrElse(default: (E) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default(error)
    }
    
    /**
     * Transform the success value.
     */
    inline fun <R> map(transform: (T) -> R): SolanaResult<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    
    /**
     * Transform the error.
     */
    inline fun <F : SolanaError> mapError(transform: (E) -> F): SolanaResult<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }
    
    /**
     * Chain another operation that may fail.
     */
    inline fun <R> flatMap(transform: (T) -> SolanaResult<R, @UnsafeVariance E>): SolanaResult<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
    
    /**
     * Recover from an error.
     */
    inline fun recover(handler: (E) -> @UnsafeVariance T): SolanaResult<T, Nothing> = when (this) {
        is Success -> this
        is Failure -> Success(handler(error))
    }
    
    /**
     * Recover from an error with another result.
     */
    inline fun recoverWith(handler: (E) -> SolanaResult<@UnsafeVariance T, @UnsafeVariance E>): SolanaResult<T, E> = when (this) {
        is Success -> this
        is Failure -> handler(error)
    }
    
    /**
     * Execute a side effect on success.
     */
    inline fun onSuccess(action: (T) -> Unit): SolanaResult<T, E> {
        if (this is Success) action(value)
        return this
    }
    
    /**
     * Execute a side effect on failure.
     */
    inline fun onFailure(action: (E) -> Unit): SolanaResult<T, E> {
        if (this is Failure) action(error)
        return this
    }
    
    companion object {
        /**
         * Create a success result.
         */
        fun <T> success(value: T): SolanaResult<T, Nothing> = Success(value)
        
        /**
         * Create a failure result.
         */
        fun <E : SolanaError> failure(error: E): SolanaResult<Nothing, E> = Failure(error)
        
        /**
         * Wrap a block that might throw into a Result.
         */
        inline fun <T> catching(block: () -> T): SolanaResult<T, SolanaError.Unknown> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(SolanaError.Unknown(e.message ?: "Unknown error", e))
        }
        
        /**
         * Combine multiple results into a single result containing a list.
         */
        fun <T, E : SolanaError> combine(vararg results: SolanaResult<T, E>): SolanaResult<List<T>, E> {
            val values = mutableListOf<T>()
            for (result in results) {
                when (result) {
                    is Success -> values.add(result.value)
                    is Failure -> return Failure(result.error)
                }
            }
            return Success(values)
        }
    }
}

/**
 * Base class for Solana-specific errors.
 */
sealed class SolanaError(
    open val message: String,
    open val cause: Throwable? = null
) {
    fun toException(): SolanaException = SolanaException(this)
    
    // RPC Errors
    data class RpcError(
        override val message: String,
        val code: Int? = null,
        override val cause: Throwable? = null
    ) : SolanaError(message, cause)
    
    // Transaction Errors
    data class TransactionFailed(
        override val message: String,
        val signature: String? = null,
        val logs: List<String>? = null,
        override val cause: Throwable? = null
    ) : SolanaError(message, cause)
    
    data class BlockhashExpired(
        override val message: String = "Blockhash expired",
        val blockhash: String? = null
    ) : SolanaError(message)
    
    data class InsufficientFunds(
        override val message: String,
        val required: Long? = null,
        val available: Long? = null
    ) : SolanaError(message)
    
    // Account Errors
    data class AccountNotFound(
        override val message: String,
        val pubkey: String
    ) : SolanaError(message)
    
    data class InvalidAccountData(
        override val message: String,
        val pubkey: String? = null
    ) : SolanaError(message)
    
    // Network Errors
    data class NetworkError(
        override val message: String,
        val isRetryable: Boolean = true,
        override val cause: Throwable? = null
    ) : SolanaError(message, cause)
    
    data class Timeout(
        override val message: String = "Request timed out",
        val timeoutMs: Long? = null
    ) : SolanaError(message)
    
    // Validation Errors
    data class InvalidInput(
        override val message: String,
        val field: String? = null
    ) : SolanaError(message)
    
    data class SignatureFailed(
        override val message: String,
        override val cause: Throwable? = null
    ) : SolanaError(message, cause)
    
    // Generic
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : SolanaError(message, cause)
}

/**
 * Exception wrapper for SolanaError.
 */
class SolanaException(val error: SolanaError) : Exception(error.message, error.cause)

/**
 * Type alias for common result types.
 */
typealias RpcResult<T> = SolanaResult<T, SolanaError.RpcError>
typealias TxResult<T> = SolanaResult<T, SolanaError.TransactionFailed>
