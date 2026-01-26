/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Real-Time Portfolio Sync - Asset Representation
 * 
 * This module provides real-time portfolio tracking with instant updates
 * when token balances change on-chain. No competitor SDK offers this.
 */

package com.selenus.artemis.portfolio

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Represents a token asset in the portfolio.
 * 
 * Supports SOL, SPL tokens, and Token-2022 assets with extension metadata.
 * 
 * Note: This class is not @Serializable because it uses BigInteger for precision.
 * If you need to serialize, convert rawAmount to String first.
 */
data class Asset(
    /** The mint address (or "native" for SOL) */
    val mint: String,
    
    /** Token symbol (e.g., "SOL", "USDC") - may be empty if not fetched */
    val symbol: String,
    
    /** Human-readable name (e.g., "Solana", "USD Coin") */
    val name: String,
    
    /** Token decimals (9 for SOL, 6 for USDC, etc.) */
    val decimals: Int,
    
    /** Raw token amount as BigInteger (for precision) */
    val rawAmount: BigInteger,
    
    /** Owner's token account address (ATA or custom) */
    val tokenAccount: String,
    
    /** Token logo URI (if available from metadata) */
    val logoUri: String? = null,
    
    /** Token-2022 extensions detected on this token */
    val extensions: List<TokenExtension> = emptyList(),
    
    /** Whether this is a frozen token account */
    val isFrozen: Boolean = false,
    
    /** Optional USD value per token (from external price feed) */
    val priceUsd: Double? = null,
    
    /** Timestamp when this asset was last updated */
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** The "mint" address used for native SOL */
        const val NATIVE_SOL_MINT = "native"
        
        /** SOL decimals */
        const val SOL_DECIMALS = 9
        
        /** Create a native SOL asset */
        fun sol(lamports: BigInteger, walletAddress: String): Asset = Asset(
            mint = NATIVE_SOL_MINT,
            symbol = "SOL",
            name = "Solana",
            decimals = SOL_DECIMALS,
            rawAmount = lamports,
            tokenAccount = walletAddress
        )
    }
    
    /**
     * Get the human-readable balance with proper decimal formatting.
     */
    val balance: BigDecimal
        get() {
            if (rawAmount == BigInteger.ZERO) return BigDecimal.ZERO
            return BigDecimal(rawAmount).divide(
                BigDecimal.TEN.pow(decimals),
                decimals,
                RoundingMode.HALF_UP
            )
        }
    
    /**
     * Get the formatted balance as a string (e.g., "1.234567890" for SOL).
     */
    fun formattedBalance(maxDecimals: Int = 4): String {
        return balance.setScale(maxDecimals.coerceAtMost(decimals), RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
    }
    
    /**
     * Get the total USD value of this holding.
     */
    val totalValueUsd: Double?
        get() = priceUsd?.let { it * balance.toDouble() }
    
    /**
     * Check if this is native SOL (not wrapped SOL token).
     */
    val isNativeSol: Boolean
        get() = mint == NATIVE_SOL_MINT
    
    /**
     * Check if the balance is zero.
     */
    val isEmpty: Boolean
        get() = rawAmount == BigInteger.ZERO
    
    /**
     * Check if this token has any Token-2022 extensions.
     */
    val hasExtensions: Boolean
        get() = extensions.isNotEmpty()
    
    /**
     * Check if this token has transfer fees (Token-2022 extension).
     */
    val hasTransferFee: Boolean
        get() = extensions.any { it is TokenExtension.TransferFee }
    
    /**
     * Get the transfer fee configuration if present.
     */
    val transferFee: TokenExtension.TransferFee?
        get() = extensions.filterIsInstance<TokenExtension.TransferFee>().firstOrNull()
}

/**
 * Token-2022 extensions that may be present on an asset.
 */
@Serializable
sealed class TokenExtension {
    
    /** Transfer fee extension - takes a percentage on each transfer */
    @Serializable
    data class TransferFee(
        /** Fee in basis points (100 = 1%) */
        val feeBasisPoints: Int,
        /** Maximum fee amount (in raw token units) */
        val maxFee: Long
    ) : TokenExtension()
    
    /** Interest-bearing token extension */
    @Serializable
    data class InterestBearing(
        /** Annual interest rate in basis points */
        val rateBasisPoints: Int,
        /** Timestamp when interest started accruing */
        val initializationTimestamp: Long
    ) : TokenExtension()
    
    /** Non-transferable token (soulbound) */
    @Serializable
    data object NonTransferable : TokenExtension()
    
    /** Permanent delegate - can transfer without owner approval */
    @Serializable
    data class PermanentDelegate(
        val delegate: String
    ) : TokenExtension()
    
    /** Confidential transfers enabled */
    @Serializable
    data object ConfidentialTransfer : TokenExtension()
    
    /** Transfer hook - calls a program on transfer */
    @Serializable
    data class TransferHook(
        val programId: String
    ) : TokenExtension()
    
    /** Metadata pointer */
    @Serializable
    data class MetadataPointer(
        val authority: String?,
        val metadataAddress: String?
    ) : TokenExtension()
    
    /** Mint close authority */
    @Serializable
    data class MintCloseAuthority(
        val authority: String
    ) : TokenExtension()
    
    /** Default account state */
    @Serializable
    data class DefaultAccountState(
        val frozen: Boolean
    ) : TokenExtension()
    
    /** Memo required for transfers */
    @Serializable
    data object MemoRequired : TokenExtension()
}

/**
 * Portfolio summary statistics.
 */
data class PortfolioSummary(
    /** Total number of assets (including zero balances if shown) */
    val assetCount: Int,
    
    /** Number of assets with non-zero balance */
    val nonZeroAssetCount: Int,
    
    /** Total portfolio value in USD (null if prices unavailable) */
    val totalValueUsd: Double?,
    
    /** SOL balance */
    val solBalance: BigDecimal,
    
    /** SOL value in USD (null if price unavailable) */
    val solValueUsd: Double?,
    
    /** Percentage of portfolio in SOL */
    val solPercentage: Double?,
    
    /** Top 5 holdings by USD value */
    val topHoldings: List<Asset>,
    
    /** Timestamp of this summary */
    val timestamp: Long = System.currentTimeMillis()
)
