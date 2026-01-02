/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import com.selenus.artemis.core.TransactionPipeline

/**
 * Pipeline interceptor that decodes transaction intents before processing.
 * 
 * This interceptor adds human-readable intent analysis to the pipeline context,
 * making it available for logging, approval UIs, or security checks.
 * 
 * ## Usage
 * ```kotlin
 * val pipeline = TransactionPipeline.Builder()
 *     .addInterceptor(IntentInterceptor(
 *         onAnalysis = { analysis ->
 *             // Show to user or log
 *             println("Transaction: ${analysis.summary}")
 *             println("Risk: ${analysis.overallRisk}")
 *         },
 *         blockHighRisk = true // Optional: reject high-risk transactions
 *     ))
 *     .addInterceptor(SimulationInterceptor(rpc))
 *     .addInterceptor(SendInterceptor(rpc))
 *     .build()
 * ```
 */
class IntentInterceptor(
    private val onAnalysis: ((TransactionIntentAnalysis) -> Unit)? = null,
    private val blockHighRisk: Boolean = false,
    private val blockCriticalRisk: Boolean = false,
    private val requireApproval: ((TransactionIntentAnalysis) -> Boolean)? = null
) : TransactionPipeline.Interceptor {
    
    companion object {
        /** Metadata key for storing the intent analysis in the pipeline context */
        const val INTENT_ANALYSIS_KEY = "artemis.intent.analysis"
        
        /** Metadata key for storing the risk level */
        const val RISK_LEVEL_KEY = "artemis.intent.riskLevel"
        
        /** Metadata key for storing warnings */
        const val WARNINGS_KEY = "artemis.intent.warnings"
    }
    
    override suspend fun intercept(chain: TransactionPipeline.Chain): TransactionPipeline.Result {
        val context = chain.context
        
        // Decode transaction bytes for analysis
        val analysis = try {
            TransactionIntentDecoder.decodeFromBytes(context.transactionData)
        } catch (e: Exception) {
            // If we can't decode, create a minimal analysis
            TransactionIntentAnalysis(
                summary = "Unable to decode transaction",
                intents = emptyList(),
                overallRisk = RiskLevel.MEDIUM,
                warnings = listOf("Transaction could not be decoded: ${e.message}"),
                programsInvolved = emptyList(),
                accountsInvolved = emptyList(),
                estimatedFee = null
            )
        }
        
        // Store analysis in context metadata
        context.metadata[INTENT_ANALYSIS_KEY] = analysis
        context.metadata[RISK_LEVEL_KEY] = analysis.overallRisk.name
        context.metadata[WARNINGS_KEY] = analysis.warnings
        
        // Notify callback
        onAnalysis?.invoke(analysis)
        
        // Check risk level blocking
        if (blockCriticalRisk && analysis.overallRisk == RiskLevel.CRITICAL) {
            return TransactionPipeline.Result.Failure(
                error = com.selenus.artemis.core.SolanaError.InvalidInput(
                    message = "Transaction blocked: CRITICAL risk level detected. ${analysis.warnings.firstOrNull() ?: ""}",
                    field = "risk"
                ),
                context = context,
                durationMs = (System.nanoTime() - context.startTimeNanos) / 1_000_000
            )
        }
        
        if (blockHighRisk && analysis.overallRisk >= RiskLevel.HIGH) {
            return TransactionPipeline.Result.Failure(
                error = com.selenus.artemis.core.SolanaError.InvalidInput(
                    message = "Transaction blocked: HIGH risk level detected. ${analysis.warnings.firstOrNull() ?: ""}",
                    field = "risk"
                ),
                context = context,
                durationMs = (System.nanoTime() - context.startTimeNanos) / 1_000_000
            )
        }
        
        // Check custom approval callback
        requireApproval?.let { approval ->
            if (!approval(analysis)) {
                return TransactionPipeline.Result.Failure(
                    error = com.selenus.artemis.core.SolanaError.InvalidInput(
                        message = "Transaction rejected by approval callback",
                        field = "approval"
                    ),
                    context = context,
                    durationMs = (System.nanoTime() - context.startTimeNanos) / 1_000_000
                )
            }
        }
        
        // Proceed with the chain
        return chain.proceed(context)
    }
}

/**
 * Extension function to get intent analysis from pipeline result.
 */
fun TransactionPipeline.Result.getIntentAnalysis(): TransactionIntentAnalysis? {
    return when (this) {
        is TransactionPipeline.Result.Success -> 
            context.metadata[IntentInterceptor.INTENT_ANALYSIS_KEY] as? TransactionIntentAnalysis
        is TransactionPipeline.Result.Failure -> 
            context.metadata[IntentInterceptor.INTENT_ANALYSIS_KEY] as? TransactionIntentAnalysis
    }
}

/**
 * Extension function to get risk level from pipeline result.
 */
fun TransactionPipeline.Result.getRiskLevel(): RiskLevel? {
    val riskName = when (this) {
        is TransactionPipeline.Result.Success -> context.metadata[IntentInterceptor.RISK_LEVEL_KEY]
        is TransactionPipeline.Result.Failure -> context.metadata[IntentInterceptor.RISK_LEVEL_KEY]
    } as? String
    return riskName?.let { RiskLevel.valueOf(it) }
}

/**
 * Extension function to get warnings from pipeline result.
 */
@Suppress("UNCHECKED_CAST")
fun TransactionPipeline.Result.getWarnings(): List<String> {
    return when (this) {
        is TransactionPipeline.Result.Success -> 
            context.metadata[IntentInterceptor.WARNINGS_KEY] as? List<String> ?: emptyList()
        is TransactionPipeline.Result.Failure -> 
            context.metadata[IntentInterceptor.WARNINGS_KEY] as? List<String> ?: emptyList()
    }
}
