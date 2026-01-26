/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Offline Transaction Queue - Default RPC Submitter
 */

package com.selenus.artemis.offline

import com.selenus.artemis.rpc.RpcApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Default implementation of TransactionSubmitter using RpcApi.
 */
class DefaultTransactionSubmitter(
    private val rpc: RpcApi,
    private val skipPreflight: Boolean = false,
    private val maxRetries: Int? = null
) : TransactionSubmitter {
    
    override suspend fun submit(transactionBytes: ByteArray): SubmissionResult {
        return try {
            val signature = rpc.sendRawTransaction(transactionBytes, skipPreflight, maxRetries)
            SubmissionResult.Success(signature)
        } catch (e: Exception) {
            classifyError(e.message ?: "Unknown error")
        }
    }
    
    override suspend fun getStatus(signature: String): ConfirmationStatus {
        return try {
            val statuses = rpc.getSignatureStatuses(listOf(signature))
            val value = statuses["value"]
            
            if (value == null || value is JsonNull) {
                return ConfirmationStatus.Unknown
            }
            
            val statusArray = value.jsonArray
            if (statusArray.isEmpty()) {
                return ConfirmationStatus.Unknown
            }
            
            val status = statusArray[0]
            if (status is JsonNull) {
                return ConfirmationStatus.Unknown
            }
            
            val statusObj = status.jsonObject
            val err = statusObj["err"]
            
            if (err != null && err !is JsonNull) {
                return ConfirmationStatus.Failed(err.toString())
            }
            
            val slot = statusObj["slot"]?.jsonPrimitive?.long ?: 0L
            val confirmationStatus = statusObj["confirmationStatus"]?.jsonPrimitive?.content
            
            return when (confirmationStatus) {
                "finalized" -> ConfirmationStatus.Finalized(slot)
                "confirmed" -> ConfirmationStatus.Confirmed(slot)
                "processed" -> ConfirmationStatus.Processing
                else -> ConfirmationStatus.Unknown
            }
        } catch (e: Exception) {
            ConfirmationStatus.Unknown
        }
    }
    
    private fun classifyError(message: String): SubmissionResult {
        val lowerMessage = message.lowercase()
        
        return when {
            // Blockhash errors
            lowerMessage.contains("blockhash not found") ||
            lowerMessage.contains("blockhash expired") ||
            lowerMessage.contains("too old") -> {
                SubmissionResult.RetryableError(message, RetryReason.BLOCKHASH_EXPIRED)
            }
            
            // Nonce errors
            lowerMessage.contains("nonce") && lowerMessage.contains("used") -> {
                SubmissionResult.RetryableError(message, RetryReason.NONCE_ALREADY_USED)
            }
            
            // Already processed
            lowerMessage.contains("already been processed") ||
            lowerMessage.contains("already processed") -> {
                // Try to extract signature if possible
                SubmissionResult.AlreadyProcessed("")
            }
            
            // Network errors
            lowerMessage.contains("connection") ||
            lowerMessage.contains("timeout") ||
            lowerMessage.contains("network") -> {
                SubmissionResult.RetryableError(message, RetryReason.NETWORK_ERROR)
            }
            
            // Rate limiting
            lowerMessage.contains("rate") ||
            lowerMessage.contains("too many requests") ||
            lowerMessage.contains("429") -> {
                SubmissionResult.RetryableError(message, RetryReason.RATE_LIMITED)
            }
            
            // Congestion
            lowerMessage.contains("congestion") ||
            lowerMessage.contains("dropped") -> {
                SubmissionResult.RetryableError(message, RetryReason.CONGESTED)
            }
            
            // Node errors
            lowerMessage.contains("node") ||
            lowerMessage.contains("internal") -> {
                SubmissionResult.RetryableError(message, RetryReason.NODE_ERROR)
            }
            
            // Permanent errors (invalid transaction)
            lowerMessage.contains("signature verification") ||
            lowerMessage.contains("invalid") ||
            lowerMessage.contains("insufficient") ||
            lowerMessage.contains("custom program error") -> {
                SubmissionResult.PermanentError(message)
            }
            
            // Default to retryable
            else -> {
                SubmissionResult.RetryableError(message, RetryReason.NETWORK_ERROR)
            }
        }
    }
}
