package com.selenus.artemis.tx

import com.selenus.artemis.runtime.currentTimeMillis
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Transaction confirmation strategies.
 *
 * Goes beyond basic sendAndConfirm with:
 * - Multiple confirmation strategies (polling, subscription, hybrid)
 * - Exponential backoff with jitter
 * - Blockhash expiry detection
 * - Confirmation level awareness (processed/confirmed/finalized)
 * - Batch confirmation with parallelism
 */
object TransactionConfirmation {
    
    /** Default polling interval in milliseconds */
    const val DEFAULT_POLL_INTERVAL_MS = 500L
    
    /** Maximum backoff interval in milliseconds */
    const val MAX_BACKOFF_MS = 10_000L
    
    /** Default confirmation timeout in milliseconds */
    const val DEFAULT_TIMEOUT_MS = 60_000L
    
    /** Blockhash validity window (approximately 150 slots × 400ms) */
    const val BLOCKHASH_VALIDITY_MS = 60_000L
    
    /**
     * Transaction commitment levels.
     */
    enum class Commitment(val value: String) {
        /** Transaction is in a block that has been voted on */
        PROCESSED("processed"),
        
        /** Transaction is in a block confirmed by supermajority */
        CONFIRMED("confirmed"),
        
        /** Transaction is finalized (cannot be rolled back) */
        FINALIZED("finalized")
    }
    
    /**
     * Confirmation strategy.
     */
    sealed interface ConfirmationStrategy {
        /** Poll the RPC for transaction status */
        data class Polling(
            val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
            val useExponentialBackoff: Boolean = true,
            val maxBackoffMs: Long = MAX_BACKOFF_MS,
            val jitterPercent: Int = 20
        ) : ConfirmationStrategy
        
        /** Subscribe via WebSocket for real-time updates (preferred when available) */
        data class Subscription(
            val fallbackToPolling: Boolean = true,
            val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
        ) : ConfirmationStrategy
        
        /** Hybrid: start with subscription, fall back to polling on disconnect */
        data class Hybrid(
            val subscriptionTimeoutMs: Long = 5_000L,
            val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
        ) : ConfirmationStrategy
    }
    
    /**
     * Confirmation result.
     */
    sealed interface ConfirmationResult {
        /** Transaction confirmed at the requested level */
        data class Confirmed(
            val signature: String,
            val slot: Long,
            val commitment: Commitment,
            val confirmationTimeMs: Long
        ) : ConfirmationResult
        
        /** Transaction failed with an error */
        data class Failed(
            val signature: String,
            val error: TransactionError,
            val slot: Long?
        ) : ConfirmationResult
        
        /** Confirmation timed out */
        data class Timeout(
            val signature: String,
            val lastStatus: String?,
            val elapsedMs: Long
        ) : ConfirmationResult
        
        /** Blockhash expired before confirmation */
        data class BlockhashExpired(
            val signature: String,
            val blockhash: String
        ) : ConfirmationResult
        
        /** Network or RPC error during confirmation */
        data class NetworkError(
            val signature: String,
            val message: String,
            val cause: Throwable?
        ) : ConfirmationResult
    }
    
    /**
     * Transaction error representation.
     */
    data class TransactionError(
        val code: Int?,
        val message: String,
        val logs: List<String>?
    )
    
    /**
     * Confirmation options.
     */
    data class ConfirmOptions(
        val commitment: Commitment = Commitment.CONFIRMED,
        val strategy: ConfirmationStrategy = ConfirmationStrategy.Polling(),
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val skipPreflight: Boolean = false,
        val preflightCommitment: Commitment? = null
    )
    
    /**
     * Pluggable signature subscription provider.
     *
     * Callers that have a websocket or event-driven subscription channel provide
     * an implementation; the confirmation engine uses it instead of polling when
     * a `Subscription` or `Hybrid` strategy is selected. Omitting the provider is
     * legal: the engine gracefully falls back to polling.
     *
     * The subscription must complete when the signature reaches at least the
     * requested [Commitment], reports an error, or the caller-supplied timeout
     * elapses.
     */
    interface SignatureSubscription {
        /**
         * Wait for [signature] to reach [commitment]. Returns `true` on success,
         * `false` on logical error (signature marked failed), and throws on
         * network error. The implementation is responsible for timeout handling
         * against the [timeoutMs] budget.
         */
        suspend fun awaitConfirmation(
            signature: String,
            commitment: Commitment,
            timeoutMs: Long
        ): SubscriptionOutcome
    }

    /** Result of a [SignatureSubscription] wait. */
    sealed interface SubscriptionOutcome {
        data class Confirmed(val slot: Long) : SubscriptionOutcome
        data class Failed(val error: TransactionError, val slot: Long?) : SubscriptionOutcome
        data object TimedOut : SubscriptionOutcome
    }

    /**
     * Interface for RPC status queries (implemented by RPC client).
     */
    interface StatusProvider {
        /**
         * Gets the signature status for one or more signatures.
         */
        suspend fun getSignatureStatuses(
            signatures: List<String>,
            searchTransactionHistory: Boolean = false
        ): List<SignatureStatus?>
        
        /**
         * Gets the current blockhash and its validity.
         */
        suspend fun getBlockhashValid(blockhash: String): Boolean
        
        /**
         * Gets the current slot.
         */
        suspend fun getSlot(commitment: Commitment = Commitment.FINALIZED): Long
    }
    
    /**
     * Signature status from RPC.
     */
    data class SignatureStatus(
        val slot: Long,
        val confirmations: Int?,
        val err: TransactionError?,
        val confirmationStatus: String? // processed, confirmed, finalized
    )
    
    // ========================================================================
    // Confirmation Engine
    // ========================================================================
    
    /**
     * Confirms a single transaction with the configured strategy.
     *
     * `Polling` always uses [provider]. `Subscription` and `Hybrid` additionally
     * use [subscription] when supplied; if `subscription` is null the engine
     * silently falls back to polling (provided the strategy allows it) so that
     * callers without a websocket layer still get a working confirmation path.
     */
    suspend fun confirm(
        signature: String,
        provider: StatusProvider,
        options: ConfirmOptions = ConfirmOptions(),
        subscription: SignatureSubscription? = null
    ): ConfirmationResult {
        val startTime = currentTimeMillis()

        return when (val strategy = options.strategy) {
            is ConfirmationStrategy.Polling -> {
                confirmWithPolling(signature, provider, options, strategy, startTime)
            }
            is ConfirmationStrategy.Subscription -> {
                if (subscription != null) {
                    confirmWithSubscription(
                        signature, provider, options, subscription,
                        fallbackPollIntervalMs = strategy.pollIntervalMs,
                        allowFallback = strategy.fallbackToPolling,
                        startTime = startTime
                    )
                } else if (strategy.fallbackToPolling) {
                    confirmWithPolling(
                        signature, provider, options,
                        ConfirmationStrategy.Polling(intervalMs = strategy.pollIntervalMs),
                        startTime
                    )
                } else {
                    ConfirmationResult.NetworkError(
                        signature = signature,
                        message = "Subscription strategy requires a SignatureSubscription or fallbackToPolling=true",
                        cause = null
                    )
                }
            }
            is ConfirmationStrategy.Hybrid -> {
                // Try subscription first within the subscriptionTimeoutMs budget.
                if (subscription != null) {
                    val budget = min(strategy.subscriptionTimeoutMs, options.timeoutMs)
                    val outcome = runCatching {
                        subscription.awaitConfirmation(signature, options.commitment, budget)
                    }.getOrNull()

                    when (outcome) {
                        is SubscriptionOutcome.Confirmed -> ConfirmationResult.Confirmed(
                            signature = signature,
                            slot = outcome.slot,
                            commitment = options.commitment,
                            confirmationTimeMs = currentTimeMillis() - startTime
                        )
                        is SubscriptionOutcome.Failed -> ConfirmationResult.Failed(
                            signature = signature,
                            error = outcome.error,
                            slot = outcome.slot
                        )
                        SubscriptionOutcome.TimedOut, null -> confirmWithPolling(
                            signature, provider, options,
                            ConfirmationStrategy.Polling(intervalMs = strategy.pollIntervalMs),
                            startTime
                        )
                    }
                } else {
                    confirmWithPolling(
                        signature, provider, options,
                        ConfirmationStrategy.Polling(intervalMs = strategy.pollIntervalMs),
                        startTime
                    )
                }
            }
        }
    }

    private suspend fun confirmWithSubscription(
        signature: String,
        provider: StatusProvider,
        options: ConfirmOptions,
        subscription: SignatureSubscription,
        fallbackPollIntervalMs: Long,
        allowFallback: Boolean,
        startTime: Long
    ): ConfirmationResult {
        val outcome = runCatching {
            subscription.awaitConfirmation(signature, options.commitment, options.timeoutMs)
        }.getOrElse { throwable ->
            if (allowFallback) {
                return confirmWithPolling(
                    signature, provider, options,
                    ConfirmationStrategy.Polling(intervalMs = fallbackPollIntervalMs),
                    startTime
                )
            }
            return ConfirmationResult.NetworkError(
                signature = signature,
                message = "subscription error: ${throwable.message ?: throwable::class.simpleName}",
                cause = throwable
            )
        }

        return when (outcome) {
            is SubscriptionOutcome.Confirmed -> ConfirmationResult.Confirmed(
                signature = signature,
                slot = outcome.slot,
                commitment = options.commitment,
                confirmationTimeMs = currentTimeMillis() - startTime
            )
            is SubscriptionOutcome.Failed -> ConfirmationResult.Failed(
                signature = signature,
                error = outcome.error,
                slot = outcome.slot
            )
            SubscriptionOutcome.TimedOut -> ConfirmationResult.Timeout(
                signature = signature,
                lastStatus = null,
                elapsedMs = currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Confirms multiple transactions in parallel.
     */
    suspend fun confirmBatch(
        signatures: List<String>,
        provider: StatusProvider,
        options: ConfirmOptions = ConfirmOptions()
    ): Map<String, ConfirmationResult> {
        val results = mutableMapOf<String, ConfirmationResult>()
        val pending = signatures.toMutableSet()
        val startTime = currentTimeMillis()
        
        val strategy = options.strategy as? ConfirmationStrategy.Polling
            ?: ConfirmationStrategy.Polling()
        
        var currentInterval = strategy.intervalMs
        
        while (pending.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= options.timeoutMs) {
                pending.forEach { sig ->
                    results[sig] = ConfirmationResult.Timeout(
                        signature = sig,
                        lastStatus = null,
                        elapsedMs = elapsed
                    )
                }
                break
            }
            
            try {
                val statuses = provider.getSignatureStatuses(pending.toList())
                
                pending.toList().forEachIndexed { index, sig ->
                    val status = statuses.getOrNull(index)
                    if (status != null) {
                        val result = evaluateStatus(sig, status, options.commitment, startTime)
                        if (result != null) {
                            results[sig] = result
                            pending.remove(sig)
                        }
                    }
                }
                
                // Reset interval on progress
                if (results.size > (signatures.size - pending.size)) {
                    currentInterval = strategy.intervalMs
                }
                
            } catch (e: Exception) {
                // Network error - continue with backoff
            }
            
            delay(currentInterval)
            
            if (strategy.useExponentialBackoff) {
                currentInterval = min(
                    (currentInterval * 1.5).toLong(),
                    strategy.maxBackoffMs
                )
                // Add jitter
                val jitter = (currentInterval * strategy.jitterPercent / 100 *
                             (kotlin.random.Random.nextDouble() * 2 - 1)).toLong()
                currentInterval += jitter
            }
        }
        
        return results
    }
    
    private suspend fun confirmWithPolling(
        signature: String,
        provider: StatusProvider,
        options: ConfirmOptions,
        strategy: ConfirmationStrategy.Polling,
        startTime: Long
    ): ConfirmationResult {
        var currentInterval = strategy.intervalMs
        var lastStatus: SignatureStatus? = null
        
        while (true) {
            val elapsed = currentTimeMillis() - startTime
            if (elapsed >= options.timeoutMs) {
                return ConfirmationResult.Timeout(
                    signature = signature,
                    lastStatus = lastStatus?.confirmationStatus,
                    elapsedMs = elapsed
                )
            }
            
            try {
                val statuses = provider.getSignatureStatuses(listOf(signature))
                val status = statuses.firstOrNull()
                
                if (status != null) {
                    lastStatus = status
                    val result = evaluateStatus(signature, status, options.commitment, startTime)
                    if (result != null) {
                        return result
                    }
                    // Reset interval on progress
                    currentInterval = strategy.intervalMs
                }
            } catch (e: Exception) {
                // Continue with backoff
            }
            
            delay(currentInterval)
            
            if (strategy.useExponentialBackoff) {
                currentInterval = min(
                    (currentInterval * 1.5).toLong(),
                    strategy.maxBackoffMs
                )
                // Add jitter
                val jitter = (currentInterval * strategy.jitterPercent / 100 *
                             (kotlin.random.Random.nextDouble() * 2 - 1)).toLong()
                currentInterval += jitter
            }
        }
    }
    
    private fun evaluateStatus(
        signature: String,
        status: SignatureStatus,
        targetCommitment: Commitment,
        startTime: Long
    ): ConfirmationResult? {
        // Check for error
        if (status.err != null) {
            return ConfirmationResult.Failed(
                signature = signature,
                error = status.err,
                slot = status.slot
            )
        }
        
        // Check commitment level
        val currentLevel = when (status.confirmationStatus) {
            "processed" -> Commitment.PROCESSED
            "confirmed" -> Commitment.CONFIRMED
            "finalized" -> Commitment.FINALIZED
            else -> null
        }
        
        if (currentLevel != null && currentLevel.ordinal >= targetCommitment.ordinal) {
            return ConfirmationResult.Confirmed(
                signature = signature,
                slot = status.slot,
                commitment = currentLevel,
                confirmationTimeMs = currentTimeMillis() - startTime
            )
        }
        
        return null
    }
    
    // ========================================================================
    // Blockhash Management
    // ========================================================================
    
    /**
     * Tracks a blockhash's validity for transaction lifetime management.
     */
    data class BlockhashTracker(
        val blockhash: String,
        val fetchedAtMs: Long,
        val lastValidBlockHeight: Long? = null
    ) {
        /**
         * Estimates if the blockhash is still valid.
         * Blockhashes are typically valid for ~150 slots (~60 seconds).
         */
        fun isLikelyValid(): Boolean {
            val elapsed = currentTimeMillis() - fetchedAtMs
            return elapsed < BLOCKHASH_VALIDITY_MS * 0.8 // 80% of validity window
        }
        
        /**
         * Gets the remaining validity time in milliseconds.
         */
        fun remainingValidityMs(): Long {
            val elapsed = currentTimeMillis() - fetchedAtMs
            return (BLOCKHASH_VALIDITY_MS - elapsed).coerceAtLeast(0)
        }
    }
    
    /**
     * Creates a blockhash tracker from a fresh blockhash.
     */
    fun trackBlockhash(blockhash: String, lastValidBlockHeight: Long? = null): BlockhashTracker {
        return BlockhashTracker(
            blockhash = blockhash,
            fetchedAtMs = currentTimeMillis(),
            lastValidBlockHeight = lastValidBlockHeight
        )
    }
}

/**
 * Convenient extension for confirming with default options.
 */
suspend fun String.confirmTransaction(
    provider: TransactionConfirmation.StatusProvider,
    commitment: TransactionConfirmation.Commitment = TransactionConfirmation.Commitment.CONFIRMED
): TransactionConfirmation.ConfirmationResult {
    return TransactionConfirmation.confirm(
        signature = this,
        provider = provider,
        options = TransactionConfirmation.ConfirmOptions(commitment = commitment)
    )
}
