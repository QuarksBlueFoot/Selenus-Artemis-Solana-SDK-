package com.selenus.artemis.wallet

/**
 * Artemis Transaction Sending Options
 * 
 * Enhanced options for sign-and-send operations that provide parity
 * with Solana Mobile MWA 2.0 protocol and additional Artemis features.
 * 
 * These options give fine-grained control over transaction submission,
 * confirmation, and retry behavior.
 */
data class SendTransactionOptions(
    /**
     * The minimum slot that the request can be evaluated at.
     * This corresponds to the minContextSlot in MWA.
     */
    val minContextSlot: Long? = null,
    
    /**
     * Commitment level for transaction confirmation.
     * Options: "processed" | "confirmed" | "finalized"
     */
    val commitment: Commitment = Commitment.CONFIRMED,
    
    /**
     * If true, skip the preflight transaction checks.
     * Use with caution as this can result in transactions that fail on-chain.
     */
    val skipPreflight: Boolean = false,
    
    /**
     * Maximum number of times for the RPC node to retry sending the
     * transaction to the leader. If not provided, the RPC node will
     * retry the transaction until it is finalized or until the
     * blockhash expires.
     */
    val maxRetries: Int? = null,
    
    /**
     * Commitment level to use for the preflight check.
     * Only relevant when skipPreflight is false.
     */
    val preflightCommitment: Commitment = Commitment.PROCESSED,
    
    /**
     * If true, wait for the transaction to reach the specified
     * commitment level before returning.
     */
    val waitForConfirmation: Boolean = true,
    
    /**
     * Timeout in milliseconds for waiting for confirmation.
     * Only applies when waitForConfirmation is true.
     */
    val confirmationTimeout: Long = 60_000L,
    
    /**
     * For batch operations: If true, wait for each transaction
     * to reach commitment before sending the next one.
     * This is critical for dependent transactions.
     * 
     * Matches MWA's waitForCommitmentToSendNextTransaction.
     */
    val waitForCommitmentToSendNextTransaction: Boolean = false,
    
    /**
     * For batch operations with waitForCommitmentToSendNextTransaction:
     * The commitment level to wait for before sending the next transaction.
     */
    val batchCommitment: Commitment = Commitment.CONFIRMED,
    
    /**
     * Optional reference to correlate this send with application state.
     * Useful for tracking and debugging.
     */
    val reference: String? = null,
    
    /**
     * Artemis Enhanced: Whether to use the local RPC or wallet's RPC.
     * Some wallets may have their own optimized RPC routing.
     */
    val preferWalletRpc: Boolean = false,
    
    /**
     * Artemis Enhanced: Priority fee strategy.
     * Allows dynamic fee adjustment for faster confirmation.
     */
    val priorityFeeStrategy: PriorityFeeStrategy = PriorityFeeStrategy.NONE
) {
    companion object {
        /**
         * Default options for most use cases.
         */
        val Default = SendTransactionOptions()
        
        /**
         * Fast options: Skip preflight, max retries for speed.
         */
        val Fast = SendTransactionOptions(
            skipPreflight = true,
            maxRetries = 0,
            waitForConfirmation = false
        )
        
        /**
         * Safe options: Full preflight, wait for finalization.
         */
        val Safe = SendTransactionOptions(
            commitment = Commitment.FINALIZED,
            skipPreflight = false,
            waitForConfirmation = true,
            confirmationTimeout = 90_000L
        )
        
        /**
         * Batch options: For ordered transaction sequences.
         */
        val Batch = SendTransactionOptions(
            waitForCommitmentToSendNextTransaction = true,
            batchCommitment = Commitment.CONFIRMED
        )
    }
    
    /**
     * Builder pattern for fluent configuration.
     */
    class Builder {
        private var minContextSlot: Long? = null
        private var commitment: Commitment = Commitment.CONFIRMED
        private var skipPreflight: Boolean = false
        private var maxRetries: Int? = null
        private var preflightCommitment: Commitment = Commitment.PROCESSED
        private var waitForConfirmation: Boolean = true
        private var confirmationTimeout: Long = 60_000L
        private var waitForCommitmentToSendNextTransaction: Boolean = false
        private var batchCommitment: Commitment = Commitment.CONFIRMED
        private var reference: String? = null
        private var preferWalletRpc: Boolean = false
        private var priorityFeeStrategy: PriorityFeeStrategy = PriorityFeeStrategy.NONE
        
        fun minContextSlot(slot: Long?) = apply { this.minContextSlot = slot }
        fun commitment(commitment: Commitment) = apply { this.commitment = commitment }
        fun skipPreflight(skip: Boolean) = apply { this.skipPreflight = skip }
        fun maxRetries(retries: Int?) = apply { this.maxRetries = retries }
        fun preflightCommitment(commitment: Commitment) = apply { this.preflightCommitment = commitment }
        fun waitForConfirmation(wait: Boolean) = apply { this.waitForConfirmation = wait }
        fun confirmationTimeout(timeout: Long) = apply { this.confirmationTimeout = timeout }
        fun waitForCommitmentToSendNextTransaction(wait: Boolean) = apply { this.waitForCommitmentToSendNextTransaction = wait }
        fun batchCommitment(commitment: Commitment) = apply { this.batchCommitment = commitment }
        fun reference(ref: String?) = apply { this.reference = ref }
        fun preferWalletRpc(prefer: Boolean) = apply { this.preferWalletRpc = prefer }
        fun priorityFeeStrategy(strategy: PriorityFeeStrategy) = apply { this.priorityFeeStrategy = strategy }
        
        fun build() = SendTransactionOptions(
            minContextSlot = minContextSlot,
            commitment = commitment,
            skipPreflight = skipPreflight,
            maxRetries = maxRetries,
            preflightCommitment = preflightCommitment,
            waitForConfirmation = waitForConfirmation,
            confirmationTimeout = confirmationTimeout,
            waitForCommitmentToSendNextTransaction = waitForCommitmentToSendNextTransaction,
            batchCommitment = batchCommitment,
            reference = reference,
            preferWalletRpc = preferWalletRpc,
            priorityFeeStrategy = priorityFeeStrategy
        )
    }
}

/**
 * Transaction commitment levels.
 * Aligned with Solana RPC commitment specs.
 */
enum class Commitment(val value: String) {
    /**
     * Query the most recent block processed by the node.
     * Note: The block may not be complete.
     */
    PROCESSED("processed"),
    
    /**
     * Query the most recent block that has been voted on by
     * a supermajority of the cluster.
     */
    CONFIRMED("confirmed"),
    
    /**
     * Query the most recent block that has been confirmed by
     * a supermajority of the cluster and has been finalized.
     */
    FINALIZED("finalized");
    
    override fun toString(): String = value
}

/**
 * Priority fee strategies for transaction submission.
 */
enum class PriorityFeeStrategy {
    /** No priority fee adjustment. */
    NONE,
    
    /** Use a low priority fee for cost savings. */
    LOW,
    
    /** Use a medium priority fee for balanced speed/cost. */
    MEDIUM,
    
    /** Use a high priority fee for faster inclusion. */
    HIGH,
    
    /** Dynamically determine based on network conditions. */
    DYNAMIC
}

/**
 * Result of a send-and-confirm operation.
 */
data class SendTransactionResult(
    /** The transaction signature (base58). */
    val signature: String,
    
    /** Whether confirmation was successful (when waitForConfirmation was true). */
    val confirmed: Boolean,
    
    /** The slot at which the transaction was processed, if known. */
    val slot: Long? = null,
    
    /** The commitment level achieved. */
    val commitment: Commitment? = null,
    
    /** Any error message if the transaction failed. */
    val error: String? = null,
    
    /** Reference passed in options, for correlation. */
    val reference: String? = null
) {
    val isSuccess: Boolean get() = error == null
    val isFailure: Boolean get() = error != null
}

/**
 * Result of a batch send operation.
 */
data class BatchSendResult(
    /** Results for each transaction, in order. */
    val results: List<SendTransactionResult>,
    
    /** Number of successful transactions. */
    val successCount: Int = results.count { it.isSuccess },
    
    /** Number of failed transactions. */
    val failureCount: Int = results.count { it.isFailure }
) {
    val allSuccessful: Boolean get() = failureCount == 0
    val allFailed: Boolean get() = successCount == 0
    val signatures: List<String> get() = results.map { it.signature }
    
    /** Get results that failed. */
    val failures: List<SendTransactionResult> get() = results.filter { it.isFailure }
    
    /** Get results that succeeded. */
    val successes: List<SendTransactionResult> get() = results.filter { it.isSuccess }
}
