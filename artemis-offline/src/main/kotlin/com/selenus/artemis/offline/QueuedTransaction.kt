/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Offline Transaction Queue - Transaction Model
 *
 * This module enables preparing transactions while offline and submitting
 * them when connectivity is restored. First-of-its-kind in Solana SDKs.
 */

package com.selenus.artemis.offline

import kotlinx.serialization.Serializable

/**
 * Represents a transaction queued for submission.
 */
@Serializable
data class QueuedTransaction(
    /** Unique identifier for this queued transaction */
    val id: String,
    
    /** Human-readable label for the transaction */
    val label: String,
    
    /** Serialized transaction bytes (Base64 encoded) */
    val transactionBase64: String,
    
    /** The wallet that needs to sign this transaction */
    val signerPubkey: String,
    
    /** Whether the transaction has been signed */
    val isSigned: Boolean,
    
    /** The blockhash used (may be stale if durable nonce not used) */
    val blockhash: String?,
    
    /** Durable nonce pubkey if using durable nonce */
    val durableNoncePubkey: String?,
    
    /** Current status of the transaction */
    val status: TransactionStatus,
    
    /** Transaction signature after submission */
    val signature: String? = null,
    
    /** Human-readable summary of what this transaction does */
    val intentSummary: String? = null,
    
    /** Risk level from intent analysis */
    val riskLevel: String? = null,
    
    /** Priority level (higher = submit first) */
    val priority: Int = 0,
    
    /** Timestamp when the transaction was created */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Timestamp when the transaction was last updated */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** Number of submission attempts */
    val attempts: Int = 0,
    
    /** Last error message if failed */
    val lastError: String? = null,
    
    /** Tags for organizing/filtering transactions */
    val tags: List<String> = emptyList(),
    
    /** Custom metadata for app-specific data */
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Check if this transaction can be submitted.
     */
    val canSubmit: Boolean
        get() = isSigned && status in listOf(
            TransactionStatus.PENDING,
            TransactionStatus.RETRY
        )
    
    /**
     * Check if this transaction uses a durable nonce.
     */
    val usesDurableNonce: Boolean
        get() = durableNoncePubkey != null
    
    /**
     * Check if the transaction is in a terminal state.
     */
    val isTerminal: Boolean
        get() = status in listOf(
            TransactionStatus.CONFIRMED,
            TransactionStatus.FINALIZED,
            TransactionStatus.FAILED,
            TransactionStatus.EXPIRED,
            TransactionStatus.CANCELLED
        )
}

/**
 * Status of a queued transaction.
 */
@Serializable
enum class TransactionStatus {
    /** Transaction is queued but not yet submitted */
    PENDING,
    
    /** Transaction is being submitted */
    SUBMITTING,
    
    /** Transaction was submitted, waiting for confirmation */
    SUBMITTED,
    
    /** Transaction needs to be retried (e.g., blockhash expired) */
    RETRY,
    
    /** Transaction is confirmed (at least 1 confirmation) */
    CONFIRMED,
    
    /** Transaction is finalized (32+ confirmations) */
    FINALIZED,
    
    /** Transaction failed and cannot be retried */
    FAILED,
    
    /** Transaction expired (blockhash too old, no durable nonce) */
    EXPIRED,
    
    /** Transaction was manually cancelled */
    CANCELLED
}

/**
 * Result of a transaction submission attempt.
 */
sealed class SubmissionResult {
    /** Transaction submitted successfully */
    data class Success(
        val signature: String,
        val slot: Long? = null
    ) : SubmissionResult()
    
    /** Submission failed, can retry */
    data class RetryableError(
        val error: String,
        val reason: RetryReason
    ) : SubmissionResult()
    
    /** Submission failed permanently */
    data class PermanentError(
        val error: String
    ) : SubmissionResult()
    
    /** Transaction already confirmed */
    data class AlreadyProcessed(
        val signature: String
    ) : SubmissionResult()
}

/**
 * Reasons why a transaction might need retry.
 */
enum class RetryReason {
    /** Blockhash has expired - need to regenerate with new blockhash */
    BLOCKHASH_EXPIRED,
    
    /** Nonce has been consumed - need to advance and retry */
    NONCE_ALREADY_USED,
    
    /** Network error - can retry as-is */
    NETWORK_ERROR,
    
    /** Node error - can retry with different node */
    NODE_ERROR,
    
    /** Rate limited - wait and retry */
    RATE_LIMITED,
    
    /** Cluster is congested - increase priority fee and retry */
    CONGESTED
}

/**
 * Events emitted by the offline queue.
 */
sealed class QueueEvent {
    /** Transaction added to queue */
    data class TransactionQueued(val transaction: QueuedTransaction) : QueueEvent()
    
    /** Transaction status changed */
    data class StatusChanged(
        val transactionId: String,
        val oldStatus: TransactionStatus,
        val newStatus: TransactionStatus
    ) : QueueEvent()
    
    /** Transaction submitted */
    data class TransactionSubmitted(
        val transactionId: String,
        val signature: String
    ) : QueueEvent()
    
    /** Transaction confirmed */
    data class TransactionConfirmed(
        val transactionId: String,
        val signature: String,
        val slot: Long
    ) : QueueEvent()
    
    /** Transaction failed */
    data class TransactionFailed(
        val transactionId: String,
        val error: String,
        val permanent: Boolean
    ) : QueueEvent()
    
    /** Connectivity restored - queue processing starting */
    data object ConnectivityRestored : QueueEvent()
    
    /** Connectivity lost - queue processing paused */
    data object ConnectivityLost : QueueEvent()
    
    /** Queue is empty */
    data object QueueEmpty : QueueEvent()
}
