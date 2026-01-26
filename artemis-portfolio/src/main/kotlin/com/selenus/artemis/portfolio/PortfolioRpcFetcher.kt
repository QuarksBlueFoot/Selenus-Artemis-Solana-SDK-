/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Real-Time Portfolio Sync - RPC Data Fetcher
 */

package com.selenus.artemis.portfolio

import java.math.BigInteger

/**
 * Interface for fetching portfolio data from Solana RPC.
 * 
 * This abstraction allows the PortfolioTracker to work with
 * any RPC client implementation.
 */
interface PortfolioRpcFetcher {
    
    /**
     * Get the SOL balance for a wallet.
     * 
     * @param wallet The wallet public key
     * @return Balance in lamports
     */
    suspend fun getSolBalance(wallet: String): BigInteger
    
    /**
     * Get all token accounts owned by a wallet.
     * 
     * This should include both SPL Token and Token-2022 accounts.
     * 
     * @param wallet The wallet public key
     * @return List of token account info
     */
    suspend fun getTokenAccounts(wallet: String): List<PortfolioTokenAccount>
    
    /**
     * Get metadata for a token mint.
     * 
     * @param mint The token mint address
     * @return Token metadata or null if unavailable
     */
    suspend fun getTokenMetadata(mint: String): PortfolioTokenMetadata?
    
    /**
     * Get mint info (decimals, supply, etc.)
     * 
     * @param mint The token mint address
     * @return Mint info or null if not found
     */
    suspend fun getMintInfo(mint: String): PortfolioMintInfo?
}

/**
 * Information about a token account for portfolio tracking.
 */
data class PortfolioTokenAccount(
    /** The token account address */
    val address: String,
    
    /** The token mint */
    val mint: String,
    
    /** The owner wallet */
    val owner: String,
    
    /** Raw token amount */
    val amount: BigInteger,
    
    /** Token decimals */
    val decimals: Int,
    
    /** Whether the account is frozen */
    val isFrozen: Boolean = false,
    
    /** Whether this is a Token-2022 account */
    val isToken2022: Boolean = false,
    
    /** Token-2022 extensions on this account */
    val extensions: List<TokenExtension> = emptyList()
)

/**
 * Token metadata (from Metaplex or on-chain metadata extension).
 */
data class PortfolioTokenMetadata(
    /** Token name (e.g., "USD Coin") */
    val name: String,
    
    /** Token symbol (e.g., "USDC") */
    val symbol: String,
    
    /** Metadata URI (for logo, etc.) */
    val uri: String? = null
)

/**
 * Mint account information for portfolio.
 */
data class PortfolioMintInfo(
    /** Token decimals */
    val decimals: Int,
    
    /** Total supply */
    val supply: BigInteger,
    
    /** Mint authority (null if frozen) */
    val mintAuthority: String?,
    
    /** Freeze authority (null if none) */
    val freezeAuthority: String?,
    
    /** Whether this is Token-2022 */
    val isToken2022: Boolean = false
)
