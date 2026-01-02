/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for Compute Budget Program instructions.
 * 
 * Decodes compute unit limit, price, and heap frame size instructions
 * for transaction priority and resource allocation.
 */
object ComputeBudgetDecoder : InstructionDecoder {
    
    // Compute Budget instruction discriminators
    private const val REQUEST_HEAP_FRAME = 1
    private const val SET_COMPUTE_UNIT_LIMIT = 2
    private const val SET_COMPUTE_UNIT_PRICE = 3
    private const val SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT = 4
    
    // Typical priority fee thresholds (in microlamports per CU)
    private const val LOW_PRIORITY_THRESHOLD = 1_000L      // 0.001 lamports per 1000 CU
    private const val MEDIUM_PRIORITY_THRESHOLD = 10_000L  // 0.01 lamports per 1000 CU
    private const val HIGH_PRIORITY_THRESHOLD = 100_000L   // 0.1 lamports per 1000 CU
    private const val VERY_HIGH_PRIORITY_THRESHOLD = 1_000_000L // 1 lamport per 1000 CU
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        if (data.isEmpty()) return null
        
        val discriminator = data[0].toInt() and 0xFF
        
        return when (discriminator) {
            REQUEST_HEAP_FRAME -> decodeRequestHeapFrame(data, instructionIndex)
            SET_COMPUTE_UNIT_LIMIT -> decodeSetComputeUnitLimit(data, instructionIndex)
            SET_COMPUTE_UNIT_PRICE -> decodeSetComputeUnitPrice(data, instructionIndex)
            SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> decodeSetLoadedAccountsDataSizeLimit(data, instructionIndex)
            else -> createUnknownIntent(discriminator, instructionIndex)
        }
    }
    
    private fun decodeRequestHeapFrame(
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val heapBytes = if (data.size >= 5) {
            ByteBuffer.wrap(data.sliceArray(1..4))
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
        } else 0
        
        val heapKb = heapBytes / 1024
        
        // Default is 32KB, max is 256KB
        val isLargeHeap = heapBytes > 32 * 1024
        val isMaxHeap = heapBytes >= 256 * 1024
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Compute Budget",
            programId = ProgramRegistry.COMPUTE_BUDGET_PROGRAM,
            method = "requestHeapFrame",
            summary = "Request ${heapKb}KB heap memory",
            accounts = emptyList(),
            args = mapOf(
                "heapBytes" to heapBytes,
                "heapKilobytes" to heapKb
            ),
            riskLevel = if (isMaxHeap) RiskLevel.MEDIUM else RiskLevel.INFO,
            warnings = when {
                isMaxHeap -> listOf("Maximum heap size requested - complex computation")
                isLargeHeap -> listOf("Large heap request - may increase transaction cost")
                else -> emptyList()
            }
        )
    }
    
    private fun decodeSetComputeUnitLimit(
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val units = if (data.size >= 5) {
            ByteBuffer.wrap(data.sliceArray(1..4))
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
        } else 0
        
        // Default is 200,000 per instruction, max is 1,400,000 per transaction
        val isHighLimit = units > 400_000
        val isMaxLimit = units >= 1_400_000
        
        val formattedUnits = when {
            units >= 1_000_000 -> "${"%.2f".format(units / 1_000_000.0)}M"
            units >= 1_000 -> "${"%.1f".format(units / 1_000.0)}K"
            else -> units.toString()
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Compute Budget",
            programId = ProgramRegistry.COMPUTE_BUDGET_PROGRAM,
            method = "setComputeUnitLimit",
            summary = "Set compute limit: $formattedUnits units",
            accounts = emptyList(),
            args = mapOf(
                "computeUnits" to units,
                "formattedUnits" to formattedUnits
            ),
            riskLevel = if (isMaxLimit) RiskLevel.MEDIUM else RiskLevel.INFO,
            warnings = when {
                isMaxLimit -> listOf("Maximum compute limit - complex transaction")
                isHighLimit -> listOf("High compute limit may indicate complex operations")
                else -> emptyList()
            }
        )
    }
    
    private fun decodeSetComputeUnitPrice(
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val microLamportsPerCu = if (data.size >= 9) {
            ByteBuffer.wrap(data.sliceArray(1..8))
                .order(ByteOrder.LITTLE_ENDIAN)
                .long
        } else 0L
        
        // Calculate approximate cost for 200K compute units (typical transaction)
        val typicalCostLamports = (microLamportsPerCu * 200_000) / 1_000_000
        val typicalCostSol = typicalCostLamports / 1_000_000_000.0
        
        val priorityLevel = when {
            microLamportsPerCu >= VERY_HIGH_PRIORITY_THRESHOLD -> "🚀 VERY HIGH"
            microLamportsPerCu >= HIGH_PRIORITY_THRESHOLD -> "⚡ HIGH"
            microLamportsPerCu >= MEDIUM_PRIORITY_THRESHOLD -> "📈 MEDIUM"
            microLamportsPerCu >= LOW_PRIORITY_THRESHOLD -> "📊 LOW"
            else -> "📉 MINIMAL"
        }
        
        val warnings = mutableListOf<String>()
        val riskLevel = when {
            microLamportsPerCu >= VERY_HIGH_PRIORITY_THRESHOLD -> {
                warnings.add("⚠️ Very high priority fee - ~${"%.6f".format(typicalCostSol)} SOL extra")
                RiskLevel.MEDIUM
            }
            microLamportsPerCu >= HIGH_PRIORITY_THRESHOLD -> {
                warnings.add("High priority fee for faster confirmation")
                RiskLevel.LOW
            }
            else -> RiskLevel.INFO
        }
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Compute Budget",
            programId = ProgramRegistry.COMPUTE_BUDGET_PROGRAM,
            method = "setComputeUnitPrice",
            summary = "$priorityLevel priority: $microLamportsPerCu µL/CU",
            accounts = emptyList(),
            args = mapOf(
                "microLamportsPerComputeUnit" to microLamportsPerCu,
                "priorityLevel" to priorityLevel,
                "estimatedCostLamports" to typicalCostLamports,
                "estimatedCostSol" to typicalCostSol
            ),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }
    
    private fun decodeSetLoadedAccountsDataSizeLimit(
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent {
        val sizeLimit = if (data.size >= 5) {
            ByteBuffer.wrap(data.sliceArray(1..4))
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
        } else 0
        
        val sizeMb = sizeLimit / (1024.0 * 1024.0)
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Compute Budget",
            programId = ProgramRegistry.COMPUTE_BUDGET_PROGRAM,
            method = "setLoadedAccountsDataSizeLimit",
            summary = "Limit account data to ${"%.2f".format(sizeMb)}MB",
            accounts = emptyList(),
            args = mapOf(
                "sizeLimit" to sizeLimit,
                "sizeMegabytes" to sizeMb
            ),
            riskLevel = RiskLevel.INFO,
            warnings = emptyList()
        )
    }
    
    private fun createUnknownIntent(
        discriminator: Int,
        instructionIndex: Int
    ): TransactionIntent {
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Compute Budget",
            programId = ProgramRegistry.COMPUTE_BUDGET_PROGRAM,
            method = "unknown($discriminator)",
            summary = "Unknown compute budget instruction",
            accounts = emptyList(),
            args = mapOf("discriminator" to discriminator),
            riskLevel = RiskLevel.MEDIUM,
            warnings = listOf("⚠️ Unknown instruction - review carefully")
        )
    }
}
