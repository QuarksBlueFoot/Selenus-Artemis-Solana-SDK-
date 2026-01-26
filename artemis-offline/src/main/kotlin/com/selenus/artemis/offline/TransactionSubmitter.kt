/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Offline Transaction Queue - Transaction Submitter Interface
 */

package com.selenus.artemis.offline

/**
 * Interface for submitting transactions to the Solana network.
 * 
 * Implementations should handle RPC communication and error classification.
 */
interface TransactionSubmitter {
    
    /**
     * Submit a serialized transaction to the network.
     * 
     * @param transactionBytes The serialized transaction bytes
     * @return The result of the submission attempt
     */
    suspend fun submit(transactionBytes: ByteArray): SubmissionResult
    
    /**
     * Get the confirmation status of a submitted transaction.
     * 
     * @param signature The transaction signature
     * @return The current confirmation status
     */
    suspend fun getStatus(signature: String): ConfirmationStatus
}

/**
 * Confirmation status of a transaction.
 */
sealed class ConfirmationStatus {
    /** Transaction not found or still processing */
    data object Unknown : ConfirmationStatus()
    
    /** Transaction is being processed */
    data object Processing : ConfirmationStatus()
    
    /** Transaction is confirmed */
    data class Confirmed(val slot: Long) : ConfirmationStatus()
    
    /** Transaction is finalized (32+ confirmations) */
    data class Finalized(val slot: Long) : ConfirmationStatus()
    
    /** Transaction failed */
    data class Failed(val error: String?) : ConfirmationStatus()
}
