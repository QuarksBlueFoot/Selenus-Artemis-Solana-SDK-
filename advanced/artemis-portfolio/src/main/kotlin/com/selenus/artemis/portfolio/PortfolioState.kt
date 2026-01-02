/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Real-Time Portfolio Sync - Portfolio State and Events
 */

package com.selenus.artemis.portfolio

import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Represents the complete portfolio state at a point in time.
 */
data class PortfolioState(
    /** The wallet address being tracked */
    val wallet: String,
    
    /** All assets keyed by mint address */
    val assets: Map<String, Asset>,
    
    /** Native SOL balance (also in assets with key "native") */
    val solBalance: BigInteger,
    
    /** Whether the portfolio is fully loaded */
    val isLoaded: Boolean,
    
    /** Whether real-time sync is active */
    val isSyncing: Boolean,
    
    /** Last error if any */
    val lastError: String? = null,
    
    /** Timestamp of last update */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /** Create an empty initial state */
        fun empty(wallet: String): PortfolioState = PortfolioState(
            wallet = wallet,
            assets = emptyMap(),
            solBalance = BigInteger.ZERO,
            isLoaded = false,
            isSyncing = false
        )
    }
    
    /** Get SOL as an Asset */
    val solAsset: Asset
        get() = assets[Asset.NATIVE_SOL_MINT] ?: Asset.sol(solBalance, wallet)
    
    /** Get all non-zero assets */
    val nonZeroAssets: List<Asset>
        get() = assets.values.filter { !it.isEmpty }
    
    /** Get all SPL tokens (excluding native SOL) */
    val tokens: List<Asset>
        get() = assets.values.filter { !it.isNativeSol }
    
    /** Get all Token-2022 assets */
    val token2022Assets: List<Asset>
        get() = assets.values.filter { it.hasExtensions }
    
    /** Get portfolio summary */
    fun summary(): PortfolioSummary {
        val nonZero = nonZeroAssets
        val totalUsd = if (nonZero.all { it.priceUsd != null }) {
            nonZero.sumOf { it.totalValueUsd ?: 0.0 }
        } else null
        
        val solAsset = this.solAsset
        val solUsd = solAsset.totalValueUsd
        val solPct = if (totalUsd != null && totalUsd > 0 && solUsd != null) {
            (solUsd / totalUsd) * 100
        } else null
        
        val topHoldings = nonZero
            .filter { it.priceUsd != null }
            .sortedByDescending { it.totalValueUsd ?: 0.0 }
            .take(5)
        
        return PortfolioSummary(
            assetCount = assets.size,
            nonZeroAssetCount = nonZero.size,
            totalValueUsd = totalUsd,
            solBalance = solAsset.balance,
            solValueUsd = solUsd,
            solPercentage = solPct,
            topHoldings = topHoldings
        )
    }
}

/**
 * Events emitted by the portfolio tracker.
 */
sealed class PortfolioEvent {
    /** Portfolio initial load started */
    data class LoadingStarted(val wallet: String) : PortfolioEvent()
    
    /** Portfolio initial load completed */
    data class LoadingComplete(val wallet: String, val assetCount: Int) : PortfolioEvent()
    
    /** Real-time sync started */
    data class SyncStarted(val wallet: String) : PortfolioEvent()
    
    /** Real-time sync stopped */
    data class SyncStopped(val wallet: String, val reason: String) : PortfolioEvent()
    
    /** SOL balance changed */
    data class SolBalanceChanged(
        val wallet: String,
        val previousBalance: BigInteger,
        val newBalance: BigInteger,
        val delta: BigInteger
    ) : PortfolioEvent() {
        val isIncrease: Boolean get() = delta > BigInteger.ZERO
        val formattedDelta: String get() {
            val sign = if (isIncrease) "+" else ""
            val amount = BigDecimal(delta.abs()).divide(BigDecimal.TEN.pow(9))
            return "$sign$amount SOL"
        }
    }
    
    /** Token balance changed */
    data class TokenBalanceChanged(
        val wallet: String,
        val asset: Asset,
        val previousRawAmount: BigInteger,
        val newRawAmount: BigInteger,
        val delta: BigInteger
    ) : PortfolioEvent() {
        val isIncrease: Boolean get() = delta > BigInteger.ZERO
        val formattedDelta: String get() {
            val sign = if (isIncrease) "+" else ""
            val amount = BigDecimal(delta.abs()).divide(BigDecimal.TEN.pow(asset.decimals))
            return "$sign$amount ${asset.symbol}"
        }
    }
    
    /** New token discovered in wallet */
    data class TokenDiscovered(
        val wallet: String,
        val asset: Asset
    ) : PortfolioEvent()
    
    /** Token removed from wallet (account closed) */
    data class TokenRemoved(
        val wallet: String,
        val mint: String,
        val tokenAccount: String
    ) : PortfolioEvent()
    
    /** Token account frozen/unfrozen */
    data class TokenFreezeStateChanged(
        val wallet: String,
        val asset: Asset,
        val isFrozen: Boolean
    ) : PortfolioEvent()
    
    /** Error occurred during sync */
    data class SyncError(
        val wallet: String,
        val error: String,
        val recoverable: Boolean
    ) : PortfolioEvent()
    
    /** Reconnecting to WebSocket */
    data class Reconnecting(
        val wallet: String,
        val attempt: Int
    ) : PortfolioEvent()
}

/**
 * Configuration for portfolio tracking behavior.
 */
data class PortfolioConfig(
    /** Whether to track SOL balance changes */
    val trackSol: Boolean = true,
    
    /** Whether to track SPL token balance changes */
    val trackTokens: Boolean = true,
    
    /** Whether to include zero-balance tokens */
    val includeZeroBalances: Boolean = false,
    
    /** Whether to fetch token metadata (names, symbols, logos) */
    val fetchMetadata: Boolean = true,
    
    /** Minimum token value in USD to include (null = include all) */
    val minValueUsd: Double? = null,
    
    /** Specific mints to track (null = track all) */
    val mintFilter: Set<String>? = null,
    
    /** Commitment level for subscriptions */
    val commitment: String = "confirmed",
    
    /** Whether to auto-reconnect on disconnect */
    val autoReconnect: Boolean = true,
    
    /** Debounce rapid updates (ms) */
    val debounceMs: Long = 100
)

/**
 * Interface for external price feeds.
 */
interface PriceFeed {
    /**
     * Get USD prices for the given mints.
     * 
     * @param mints List of token mint addresses
     * @return Map of mint -> USD price (null if unavailable)
     */
    suspend fun getPrices(mints: List<String>): Map<String, Double?>
    
    /**
     * Subscribe to price updates for the given mints.
     * 
     * @param mints Mints to subscribe to
     * @param onUpdate Callback when prices update
     */
    suspend fun subscribePrices(
        mints: List<String>,
        onUpdate: suspend (Map<String, Double>) -> Unit
    )
}

/**
 * No-op price feed for when prices aren't needed.
 */
object NoPriceFeed : PriceFeed {
    override suspend fun getPrices(mints: List<String>): Map<String, Double?> = emptyMap()
    override suspend fun subscribePrices(mints: List<String>, onUpdate: suspend (Map<String, Double>) -> Unit) {}
}
