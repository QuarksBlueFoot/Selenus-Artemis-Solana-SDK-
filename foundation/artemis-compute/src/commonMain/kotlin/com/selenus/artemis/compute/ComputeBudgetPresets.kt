package com.selenus.artemis.compute

import com.selenus.artemis.tx.Instruction

/**
 * ComputeBudgetPresets
 * 
 * Provides preset tiers for compute budget configuration.
 * Use these for common transaction types without manually calculating
 * compute units and priority fees.
 * 
 * Note: Gaming-specific presets are in artemis-gaming ComputeBudgetPresets
 * This module provides general-purpose presets for all apps.
 */
object ComputeBudgetPresets {
    
    /**
     * Standard preset tiers for different transaction types.
     */
    enum class Tier(val units: Int, val microLamports: Long) {
        /** Simple transfers, memo-only transactions */
        STANDARD(units = 200_000, microLamports = 100),
        
        /** Token transfers, simple program calls */
        ENHANCED(units = 400_000, microLamports = 500),
        
        /** Complex DeFi operations, NFT mints */
        PRIORITY(units = 600_000, microLamports = 1_000),
        
        /** Time-sensitive operations, auction bids */
        URGENT(units = 800_000, microLamports = 5_000),
        
        /** Maximum priority for critical operations */
        MAXIMUM(units = 1_400_000, microLamports = 10_000)
    }
    
    /**
     * Create instructions for a preset tier.
     */
    fun preset(tier: Tier): List<Instruction> {
        return listOf(
            ComputeBudgetProgram.setComputeUnitLimit(tier.units),
            ComputeBudgetProgram.setComputeUnitPrice(tier.microLamports)
        )
    }
    
    /**
     * Create a compute unit limit instruction.
     */
    fun setComputeUnitLimit(units: Int): Instruction {
        return ComputeBudgetProgram.setComputeUnitLimit(units)
    }
    
    /**
     * Create a compute unit price instruction.
     */
    fun setComputeUnitPrice(microLamports: Long): Instruction {
        return ComputeBudgetProgram.setComputeUnitPrice(microLamports)
    }
    
    /**
     * Create both compute unit limit and price instructions.
     */
    fun custom(units: Int, microLamports: Long): List<Instruction> {
        return listOf(
            ComputeBudgetProgram.setComputeUnitLimit(units),
            ComputeBudgetProgram.setComputeUnitPrice(microLamports)
        )
    }
    
    /**
     * Estimate compute units for a transaction type.
     */
    fun estimateUnits(txType: TransactionType): Int {
        return when (txType) {
            TransactionType.SOL_TRANSFER -> 150_000
            TransactionType.TOKEN_TRANSFER -> 200_000
            TransactionType.TOKEN_MINT -> 300_000
            TransactionType.NFT_MINT -> 400_000
            TransactionType.SWAP -> 600_000
            TransactionType.COMPLEX_DEFI -> 800_000
            TransactionType.CUSTOM -> 400_000
        }
    }
    
    /**
     * Common transaction types for estimation.
     */
    enum class TransactionType {
        SOL_TRANSFER,
        TOKEN_TRANSFER,
        TOKEN_MINT,
        NFT_MINT,
        SWAP,
        COMPLEX_DEFI,
        CUSTOM
    }
}
