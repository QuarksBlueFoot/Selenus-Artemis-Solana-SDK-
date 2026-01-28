/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * RpcEntityResolver - Resolves NLP entities using RPC calls.
 * 
 * Provides real-world entity resolution:
 * - Domain resolution via SNS (Solana Name Service)
 * - Token symbol lookup via Jupiter token list
 * - Program ID resolution for known programs
 * - Validator name resolution
 */
package com.selenus.artemis.nlp

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.*
import java.net.URL

/**
 * RPC-backed entity resolver with caching.
 */
class RpcEntityResolver(
    private val rpc: RpcApi,
    private val jupiterApiUrl: String = "https://token.jup.ag/all"
) : EntityResolver {
    
    // Token cache: symbol -> mint address
    private val tokenCache = mutableMapOf<String, String>()
    private var tokenCacheLoaded = false
    
    // Program cache: name -> program ID
    private val programCache = mapOf(
        "system" to "11111111111111111111111111111111",
        "token" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
        "token-2022" to "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
        "associated-token" to "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
        "memo" to "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
        "stake" to "Stake11111111111111111111111111111111111111",
        "vote" to "Vote111111111111111111111111111111111111111",
        "bpf-loader" to "BPFLoaderUpgradeab1e11111111111111111111111",
        "metaplex" to "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s",
        "candy-machine" to "CndyV3LdqHUfDLmE5naZjVN8rBZz4tqhdefbAnjHG3JR",
        "jupiter" to "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
        "marinade" to "MarBmsSgKXdrN1egZf5sqe1TMai9K1rChYNDJgjq7aD",
        "raydium" to "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
        "orca" to "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc",
        "serum" to "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin",
        "mango" to "mv3ekLzLbnVPNxjSKvqBpU3ZeZXPQdEC3bp5MDEBG68",
        "tensor" to "TCMPhJdwDryooaGtiocG1u3xcYbRpiJzb283XfCZsDp",
        "magic-eden" to "M2mx93ekt1fmXSVkTrUL9xVFHkmME8HTUi5Cyc5aF7K"
    )
    
    // Known validators cache
    private val validatorCache = mapOf(
        "marinade" to "mrgn2vsZ3EpqABMvvVgVKMbf7HFZAJhMRpdAz3VeLAQ",
        "jito" to "J1to3DokTqGzLi6j5W9qzr8bNVZrxQtKpKSYf6aYqQcP",
        "figment" to "FigHJrGvNK7EiVKHXJVbhSQDpfCzJKHmVv5zqWuKt3Bv",
        "everstake" to "Ev1WSxvyj4c3k4vQqr7CdAJiT9dZ8VZ7puFqrJ8rA8N",
        "solflare" to "SF1aG1aXC2TnuqbcLc2c7S6pPfJJvaJJqXc5Qd3qXBv"
    )
    
    /**
     * Resolve a .sol domain to its owner address.
     */
    override suspend fun resolveDomain(domain: String): String? {
        if (!domain.endsWith(".sol")) return null
        
        try {
            // SNS domain resolution
            // The SNS program stores domain -> owner mappings
            val domainName = domain.removeSuffix(".sol")
            
            // Derive the domain account address
            val hashedName = hashDomainName(domainName)
            val nameAccountKey = findNameAccountKey(hashedName)
            
            // Fetch the account data
            val accountData = rpc.getAccountInfoBase64(nameAccountKey)
            if (accountData != null && accountData.size >= 64) {
                // The owner is stored at offset 32
                val ownerBytes = accountData.copyOfRange(32, 64)
                return Pubkey(ownerBytes).toBase58()
            }
        } catch (e: Exception) {
            // Fall through to return null
        }
        
        return null
    }
    
    /**
     * Resolve a token symbol to its mint address.
     */
    override suspend fun resolveTokenSymbol(symbol: String): String? {
        val upperSymbol = symbol.uppercase()
        
        // Check built-in cache first
        KNOWN_TOKENS[upperSymbol]?.let { return it }
        
        // Check loaded cache
        tokenCache[upperSymbol]?.let { return it }
        
        // Load Jupiter token list if not loaded
        if (!tokenCacheLoaded) {
            loadJupiterTokenList()
            tokenCacheLoaded = true
        }
        
        return tokenCache[upperSymbol]
    }
    
    /**
     * Resolve a program name to its ID.
     */
    override suspend fun resolveProgramName(name: String): String? {
        return programCache[name.lowercase()]
    }
    
    /**
     * Resolve a validator name to its vote account.
     */
    override suspend fun resolveValidatorName(name: String): String? {
        return validatorCache[name.lowercase()]
    }
    
    /**
     * Get SOL balance for an address.
     */
    suspend fun getBalance(address: String): Double {
        val result = rpc.getBalance(address)
        return result.lamports / 1_000_000_000.0
    }
    
    /**
     * Get token balance for an address and mint.
     */
    suspend fun getTokenBalance(owner: String, mint: String): Double? {
        try {
            val ataAddress = findAssociatedTokenAddress(Pubkey.fromBase58(owner), Pubkey.fromBase58(mint))
            val accountInfo = rpc.getTokenAccountInfoParsed(ataAddress.toBase58())
            val decimals = accountInfo?.decimals ?: 9
            return accountInfo?.amount?.toDouble()?.div(Math.pow(10.0, decimals.toDouble()))
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Check if an account exists.
     */
    suspend fun accountExists(address: String): Boolean {
        return try {
            val info = rpc.getAccountInfoBase64(address)
            info != null
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun loadJupiterTokenList() {
        try {
            // Simple HTTP fetch - in production use proper HTTP client
            val json = URL(jupiterApiUrl).readText()
            val tokens = Json.decodeFromString<List<JupiterToken>>(json)
            
            for (token in tokens) {
                tokenCache[token.symbol.uppercase()] = token.address
            }
        } catch (e: Exception) {
            // Silently fail - cache will be empty
        }
    }
    
    private fun hashDomainName(name: String): ByteArray {
        // SNS uses SHA256 hash of the domain name
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(name.toByteArray())
    }
    
    private fun findNameAccountKey(hashedName: ByteArray): String {
        // SNS Name Service program
        val snsProgram = Pubkey.fromBase58("namesLPneVptA9Z5rqUDD9tMTWEJwofgaYwp8cawRkX")
        
        // Derive PDA
        val seeds = listOf(hashedName)
        val (pda, _) = Pubkey.findProgramAddress(seeds, snsProgram)
        return pda.toBase58()
    }
    
    private fun findAssociatedTokenAddress(owner: Pubkey, mint: Pubkey): Pubkey {
        val associatedTokenProgram = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
        val tokenProgram = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        
        val seeds = listOf(
            owner.bytes,
            tokenProgram.bytes,
            mint.bytes
        )
        val (pda, _) = Pubkey.findProgramAddress(seeds, associatedTokenProgram)
        return pda
    }
    
    @kotlinx.serialization.Serializable
    private data class JupiterToken(
        val address: String,
        val symbol: String,
        val name: String = "",
        val decimals: Int = 9
    )
    
    companion object {
        // Built-in token registry
        val KNOWN_TOKENS = mapOf(
            "SOL" to "So11111111111111111111111111111111111111112",
            "WSOL" to "So11111111111111111111111111111111111111112",
            "USDC" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            "USDT" to "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            "BONK" to "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
            "JUP" to "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",
            "RAY" to "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R",
            "ORCA" to "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE",
            "PYTH" to "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3",
            "RENDER" to "rndrizKT3MK1iimdxRdWabcF7Zg7AR5T4nud4EkHBof",
            "JITO" to "jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL",
            "WIF" to "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm",
            "SAMO" to "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
            "MNGO" to "MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac",
            "SRM" to "SRMuApVNdxXokk5GT7XD5cUUgXMBCoAz2LHeuAoKWRt",
            "COPE" to "8HGyAAB1yoM1ttS7pXjHMa3dukTFGQggnFFH3hJZgzQh",
            "STEP" to "StepAscQoEioFxxWGnh2sLBDFp9d8rvKz2Yp39iDpyT",
            "ATLAS" to "ATLASXmbPQxBUYbxPsV97usA3fPQYEqzQBUHgiFCUsXx",
            "POLIS" to "poLisWXnNRwC6oBu1vHiuKQzFjGL4XDSu4g9qjz9qVk",
            "GRAPE" to "8upjSpvjcdpuzhfR1zriwg5NXkwDruejqNE9WNbPRtyA",
            "DUST" to "DUSTawucrTsGU8hcqRdHDCbuYhCPADMLM2VcCb8VnFnQ",
            "MEAN" to "MEANeD3XDdUmNMsRGjASkSWdC8prLYsoRJ61pPeHctD",
            "PRISM" to "PRSMNsEPqhGVCH1TtWiJqPjJyh2cKrLostPZTNy1fxw"
        )
    }
}
