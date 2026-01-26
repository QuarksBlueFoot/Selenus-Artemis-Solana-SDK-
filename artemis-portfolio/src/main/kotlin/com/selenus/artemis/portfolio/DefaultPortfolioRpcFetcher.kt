/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Real-Time Portfolio Sync - Default RPC Fetcher Implementation
 */

package com.selenus.artemis.portfolio

import com.selenus.artemis.rpc.RpcApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import java.math.BigInteger

/**
 * Default implementation of PortfolioRpcFetcher using RpcApi.
 * 
 * This fetches portfolio data using standard Solana JSON-RPC methods.
 */
class DefaultPortfolioRpcFetcher(
    private val rpc: RpcApi,
    private val commitment: String = "confirmed"
) : PortfolioRpcFetcher {
    
    companion object {
        private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    }
    
    override suspend fun getSolBalance(wallet: String): BigInteger {
        val result = rpc.getBalance(wallet, commitment)
        return BigInteger.valueOf(result.lamports)
    }
    
    override suspend fun getTokenAccounts(wallet: String): List<PortfolioTokenAccount> {
        val accounts = mutableListOf<PortfolioTokenAccount>()
        
        // Fetch SPL Token accounts
        val splAccounts = fetchTokenAccountsByProgram(wallet, TOKEN_PROGRAM, isToken2022 = false)
        accounts.addAll(splAccounts)
        
        // Fetch Token-2022 accounts
        val token2022Accounts = fetchTokenAccountsByProgram(wallet, TOKEN_2022_PROGRAM, isToken2022 = true)
        accounts.addAll(token2022Accounts)
        
        return accounts
    }
    
    private suspend fun fetchTokenAccountsByProgram(
        wallet: String, 
        programId: String,
        isToken2022: Boolean
    ): List<PortfolioTokenAccount> {
        val response = rpc.getTokenAccountsByOwner(wallet, programId = programId, commitment = commitment)
        val value = response["value"]
        if (value == null || value is JsonNull) return emptyList()
        
        val accountsArray = value.jsonArray
        return accountsArray.mapNotNull { element ->
            parseTokenAccount(element.jsonObject, isToken2022)
        }
    }
    
    private fun parseTokenAccount(json: JsonObject, isToken2022: Boolean): PortfolioTokenAccount? {
        try {
            val pubkey = json["pubkey"]?.jsonPrimitive?.content ?: return null
            val account = json["account"]?.jsonObject ?: return null
            val data = account["data"]?.jsonObject ?: return null
            val parsed = data["parsed"]?.jsonObject ?: return null
            val info = parsed["info"]?.jsonObject ?: return null
            
            val mint = info["mint"]?.jsonPrimitive?.content ?: return null
            val owner = info["owner"]?.jsonPrimitive?.content ?: return null
            val tokenAmount = info["tokenAmount"]?.jsonObject ?: return null
            
            val amount = tokenAmount["amount"]?.jsonPrimitive?.content?.let { 
                BigInteger(it) 
            } ?: BigInteger.ZERO
            
            val decimals = tokenAmount["decimals"]?.jsonPrimitive?.intOrNull ?: 0
            val state = info["state"]?.jsonPrimitive?.content ?: "initialized"
            val isFrozen = state == "frozen"
            
            // Parse Token-2022 extensions if present
            val extensions = if (isToken2022) {
                parseExtensions(info["extensions"]?.jsonArray)
            } else emptyList()
            
            return PortfolioTokenAccount(
                address = pubkey,
                mint = mint,
                owner = owner,
                amount = amount,
                decimals = decimals,
                isFrozen = isFrozen,
                isToken2022 = isToken2022,
                extensions = extensions
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseExtensions(extensionsArray: JsonArray?): List<TokenExtension> {
        if (extensionsArray == null) return emptyList()
        
        val extensions = mutableListOf<TokenExtension>()
        
        for (element in extensionsArray) {
            try {
                val ext = element.jsonObject
                val extensionType = ext["extension"]?.jsonPrimitive?.content ?: continue
                val state = ext["state"]?.jsonObject
                
                when (extensionType) {
                    "transferFeeConfig", "transferFeeAmount" -> {
                        val newerFee = state?.get("newerTransferFee")?.jsonObject
                        val fee = newerFee ?: state
                        val basisPoints = fee?.get("transferFeeBasisPoints")?.jsonPrimitive?.intOrNull ?: 0
                        val maxFee = fee?.get("maximumFee")?.jsonPrimitive?.longOrNull ?: 0L
                        extensions.add(TokenExtension.TransferFee(basisPoints, maxFee))
                    }
                    "interestBearingConfig" -> {
                        val rate = state?.get("currentRate")?.jsonPrimitive?.intOrNull ?: 0
                        val timestamp = state?.get("initializationTimestamp")?.jsonPrimitive?.longOrNull ?: 0L
                        extensions.add(TokenExtension.InterestBearing(rate, timestamp))
                    }
                    "nonTransferable" -> {
                        extensions.add(TokenExtension.NonTransferable)
                    }
                    "permanentDelegate" -> {
                        val delegate = state?.get("delegate")?.jsonPrimitive?.content ?: ""
                        extensions.add(TokenExtension.PermanentDelegate(delegate))
                    }
                    "confidentialTransferAccount", "confidentialTransferMint" -> {
                        extensions.add(TokenExtension.ConfidentialTransfer)
                    }
                    "transferHook" -> {
                        val program = state?.get("programId")?.jsonPrimitive?.content ?: ""
                        extensions.add(TokenExtension.TransferHook(program))
                    }
                    "metadataPointer" -> {
                        val authority = state?.get("authority")?.jsonPrimitive?.content
                        val metadata = state?.get("metadataAddress")?.jsonPrimitive?.content
                        extensions.add(TokenExtension.MetadataPointer(authority, metadata))
                    }
                    "mintCloseAuthority" -> {
                        val authority = state?.get("closeAuthority")?.jsonPrimitive?.content ?: ""
                        extensions.add(TokenExtension.MintCloseAuthority(authority))
                    }
                    "defaultAccountState" -> {
                        val frozen = state?.get("state")?.jsonPrimitive?.content == "frozen"
                        extensions.add(TokenExtension.DefaultAccountState(frozen))
                    }
                    "memoTransfer" -> {
                        val required = state?.get("requireIncomingTransferMemos")?.jsonPrimitive?.booleanOrNull ?: true
                        if (required) extensions.add(TokenExtension.MemoRequired)
                    }
                }
            } catch (e: Exception) {
                // Skip malformed extensions
            }
        }
        
        return extensions
    }
    
    override suspend fun getTokenMetadata(mint: String): PortfolioTokenMetadata? {
        // First try to get on-chain metadata (Token-2022 metadata extension)
        val onChainMetadata = getOnChainMetadata(mint)
        if (onChainMetadata != null) return onChainMetadata
        
        // Fall back to Metaplex metadata
        return getMetaplexMetadata(mint)
    }
    
    private suspend fun getOnChainMetadata(mint: String): PortfolioTokenMetadata? {
        try {
            val accountInfo = rpc.getAccountInfo(mint, commitment, encoding = "jsonParsed")
            val value = accountInfo["value"]
            if (value == null || value is JsonNull) return null
            
            val data = value.jsonObject["data"]?.jsonObject ?: return null
            val parsed = data["parsed"]?.jsonObject ?: return null
            val info = parsed["info"]?.jsonObject ?: return null
            val extensions = info["extensions"]?.jsonArray ?: return null
            
            for (ext in extensions) {
                val extObj = ext.jsonObject
                if (extObj["extension"]?.jsonPrimitive?.content == "tokenMetadata") {
                    val state = extObj["state"]?.jsonObject ?: continue
                    val name = state["name"]?.jsonPrimitive?.content ?: ""
                    val symbol = state["symbol"]?.jsonPrimitive?.content ?: ""
                    val uri = state["uri"]?.jsonPrimitive?.content
                    
                    if (name.isNotEmpty() || symbol.isNotEmpty()) {
                        return PortfolioTokenMetadata(name, symbol, uri)
                    }
                }
            }
        } catch (e: Exception) {
            // Metadata fetch failed
        }
        return null
    }
    
    private suspend fun getMetaplexMetadata(mint: String): PortfolioTokenMetadata? {
        // Calculate Metaplex metadata PDA
        // Metadata Program: metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s
        // Seeds: ["metadata", metadata_program_id, mint]
        
        // For now, we don't have PDA derivation in this module
        // In production, you'd use the artemis-metaplex module
        // This is a simplified implementation that works without additional deps
        
        return null // Metaplex metadata lookup requires additional implementation
    }
    
    override suspend fun getMintInfo(mint: String): PortfolioMintInfo? {
        try {
            val accountInfo = rpc.getAccountInfo(mint, commitment, encoding = "jsonParsed")
            val value = accountInfo["value"]
            if (value == null || value is JsonNull) return null
            
            val account = value.jsonObject
            val owner = account["owner"]?.jsonPrimitive?.content ?: return null
            val isToken2022 = owner == TOKEN_2022_PROGRAM
            
            val data = account["data"]?.jsonObject ?: return null
            val parsed = data["parsed"]?.jsonObject ?: return null
            val info = parsed["info"]?.jsonObject ?: return null
            
            val decimals = info["decimals"]?.jsonPrimitive?.intOrNull ?: 0
            val supply = info["supply"]?.jsonPrimitive?.content?.let { BigInteger(it) } ?: BigInteger.ZERO
            val mintAuthority = info["mintAuthority"]?.jsonPrimitive?.content
            val freezeAuthority = info["freezeAuthority"]?.jsonPrimitive?.content
            
            return PortfolioMintInfo(
                decimals = decimals,
                supply = supply,
                mintAuthority = mintAuthority,
                freezeAuthority = freezeAuthority,
                isToken2022 = isToken2022
            )
        } catch (e: Exception) {
            return null
        }
    }
}
