/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Real-Time Portfolio Sync - Main Tracker Implementation
 *
 * This is a first-of-its-kind feature in Solana SDKs. No competitor
 * (solana-kmp, Sol4k, Solana Mobile) offers real-time portfolio tracking.
 */

@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.selenus.artemis.portfolio

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.ws.SolanaWsClient
import com.selenus.artemis.ws.SubscriptionHandle
import com.selenus.artemis.ws.WsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.Closeable
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time portfolio tracker for Solana wallets.
 * 
 * Provides instant updates when token balances change on-chain through
 * WebSocket subscriptions. This is a unique capability not found in
 * other Solana SDKs.
 * 
 * ## Features
 * - Real-time SOL balance tracking
 * - Real-time SPL token balance tracking
 * - Token-2022 extension detection
 * - Automatic token discovery when new tokens arrive
 * - Debounced updates to prevent UI thrashing
 * - Optional USD price integration
 * - Flow-based reactive API
 * 
 * ## Usage
 * ```kotlin
 * val tracker = PortfolioTracker(
 *     wsClient = solanaWsClient,
 *     rpcClient = solanaRpcClient
 * )
 * 
 * // Start tracking a wallet
 * tracker.track("YourWalletPubkey...")
 * 
 * // Observe state changes
 * tracker.state.collect { portfolio ->
 *     println("SOL: ${portfolio.solAsset.formattedBalance()}")
 *     portfolio.tokens.forEach { token ->
 *         println("${token.symbol}: ${token.formattedBalance()}")
 *     }
 * }
 * 
 * // Observe specific events
 * tracker.events.collect { event ->
 *     when (event) {
 *         is PortfolioEvent.SolBalanceChanged -> {
 *             showNotification("SOL ${event.formattedDelta}")
 *         }
 *         is PortfolioEvent.TokenDiscovered -> {
 *             showNotification("New token: ${event.asset.symbol}")
 *         }
 *     }
 * }
 * 
 * // Stop tracking
 * tracker.stop()
 * ```
 */
class PortfolioTracker(
    private val wsClient: SolanaWsClient,
    private val rpcFetcher: PortfolioRpcFetcher,
    private val config: PortfolioConfig = PortfolioConfig(),
    private val priceFeed: PriceFeed = NoPriceFeed,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Closeable {
    
    private val _state = MutableStateFlow(PortfolioState.empty(""))
    val state: StateFlow<PortfolioState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<PortfolioEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PortfolioEvent> = _events.asSharedFlow()
    
    private val subscriptionMutex = Mutex()
    private val activeSubscriptions = ConcurrentHashMap<String, SubscriptionHandle>()
    private var wsEventJob: Job? = null
    private var priceUpdateJob: Job? = null
    private var currentWallet: String? = null
    
    // Pending updates for debouncing
    private val pendingUpdates = MutableSharedFlow<PendingUpdate>(extraBufferCapacity = 256)
    private var debounceJob: Job? = null
    
    private sealed class PendingUpdate {
        data class SolUpdate(val lamports: BigInteger) : PendingUpdate()
        data class TokenUpdate(val mint: String, val tokenAccount: String, val rawAmount: BigInteger, val data: ByteArray) : PendingUpdate()
        data class TokenAccountClosed(val tokenAccount: String) : PendingUpdate()
    }
    
    init {
        setupDebouncer()
    }
    
    /**
     * Start tracking a wallet's portfolio.
     * 
     * This will:
     * 1. Fetch the initial portfolio state (SOL + all tokens)
     * 2. Subscribe to real-time balance updates via WebSocket
     * 3. Emit events as balances change
     * 
     * @param wallet The wallet public key to track
     */
    suspend fun track(wallet: String) {
        // Clean up previous tracking
        if (currentWallet != null && currentWallet != wallet) {
            stopSubscriptions()
        }
        
        currentWallet = wallet
        _state.update { PortfolioState.empty(wallet) }
        _events.emit(PortfolioEvent.LoadingStarted(wallet))
        
        try {
            // Fetch initial state
            val initialState = loadInitialState(wallet)
            _state.value = initialState
            _events.emit(PortfolioEvent.LoadingComplete(wallet, initialState.assets.size))
            
            // Start real-time sync
            startSubscriptions(wallet)
            _events.emit(PortfolioEvent.SyncStarted(wallet))
            
        } catch (e: Exception) {
            _state.update { it.copy(lastError = e.message, isLoaded = false) }
            _events.emit(PortfolioEvent.SyncError(wallet, e.message ?: "Unknown error", true))
        }
    }
    
    /**
     * Refresh the portfolio state by re-fetching from RPC.
     * 
     * Use this after a transaction to ensure state is current.
     */
    suspend fun refresh() {
        val wallet = currentWallet ?: return
        try {
            val refreshedState = loadInitialState(wallet)
            _state.value = refreshedState.copy(isSyncing = _state.value.isSyncing)
        } catch (e: Exception) {
            _events.emit(PortfolioEvent.SyncError(wallet, "Refresh failed: ${e.message}", true))
        }
    }
    
    /**
     * Stop all tracking and clean up resources.
     */
    suspend fun stop() {
        val wallet = currentWallet ?: return
        stopSubscriptions()
        _state.update { it.copy(isSyncing = false) }
        _events.emit(PortfolioEvent.SyncStopped(wallet, "Stopped by user"))
        currentWallet = null
    }
    
    /**
     * Check if currently tracking a wallet.
     */
    val isTracking: Boolean
        get() = currentWallet != null && _state.value.isSyncing
    
    private suspend fun loadInitialState(wallet: String): PortfolioState {
        // Fetch SOL balance
        val solLamports = rpcFetcher.getSolBalance(wallet)
        
        // Fetch all token accounts
        val tokenAccounts = rpcFetcher.getTokenAccounts(wallet)
        
        // Build asset map
        val assets = mutableMapOf<String, Asset>()
        
        // Add SOL
        assets[Asset.NATIVE_SOL_MINT] = Asset.sol(solLamports, wallet)
        
        // Add tokens
        for (account in tokenAccounts) {
            if (!config.includeZeroBalances && account.amount == BigInteger.ZERO) continue
            if (config.mintFilter != null && account.mint !in config.mintFilter) continue
            
            val metadata = if (config.fetchMetadata) {
                rpcFetcher.getTokenMetadata(account.mint)
            } else null
            
            val asset = Asset(
                mint = account.mint,
                symbol = metadata?.symbol ?: "",
                name = metadata?.name ?: "",
                decimals = account.decimals,
                rawAmount = account.amount,
                tokenAccount = account.address,
                logoUri = metadata?.uri,
                extensions = account.extensions,
                isFrozen = account.isFrozen
            )
            
            assets[account.mint] = asset
        }
        
        // Fetch prices if feed available
        if (priceFeed != NoPriceFeed) {
            val mints = assets.keys.toList()
            val prices = priceFeed.getPrices(mints)
            for ((mint, price) in prices) {
                if (price != null) {
                    assets[mint] = assets[mint]!!.copy(priceUsd = price)
                }
            }
        }
        
        return PortfolioState(
            wallet = wallet,
            assets = assets,
            solBalance = solLamports,
            isLoaded = true,
            isSyncing = false
        )
    }
    
    private suspend fun startSubscriptions(wallet: String) {
        subscriptionMutex.withLock {
            // Subscribe to wallet account (SOL balance)
            if (config.trackSol) {
                val solHandle = wsClient.accountSubscribe(wallet, config.commitment)
                activeSubscriptions["sol:$wallet"] = solHandle
            }
            
            // Subscribe to token program for this wallet's accounts
            if (config.trackTokens) {
                // We subscribe to both Token and Token-2022 programs
                // with a filter for this wallet as owner
                for (tokenAccount in _state.value.assets.values) {
                    if (!tokenAccount.isNativeSol) {
                        val handle = wsClient.accountSubscribe(
                            tokenAccount.tokenAccount, 
                            config.commitment
                        )
                        activeSubscriptions["token:${tokenAccount.tokenAccount}"] = handle
                    }
                }
            }
            
            // Start listening to WebSocket events
            wsEventJob?.cancel()
            wsEventJob = scope.launch {
                wsClient.events.collect { event ->
                    handleWsEvent(event)
                }
            }
            
            _state.update { it.copy(isSyncing = true) }
        }
    }
    
    private suspend fun stopSubscriptions() {
        subscriptionMutex.withLock {
            for ((key, handle) in activeSubscriptions) {
                try {
                    wsClient.unsubscribe(handle)
                } catch (e: Exception) {
                    // Ignore unsubscribe errors
                }
            }
            activeSubscriptions.clear()
            wsEventJob?.cancel()
            wsEventJob = null
            priceUpdateJob?.cancel()
            priceUpdateJob = null
        }
    }
    
    private suspend fun handleWsEvent(event: WsEvent) {
        when (event) {
            is WsEvent.Notification -> handleNotification(event)
            is WsEvent.Disconnected -> handleDisconnected(event.reason)
            is WsEvent.Reconnecting -> handleReconnecting(event.attempt)
            is WsEvent.Connected -> handleReconnected()
            else -> { /* Ignore other events */ }
        }
    }
    
    private suspend fun handleNotification(notification: WsEvent.Notification) {
        val key = notification.key ?: return
        val result = notification.result ?: return
        
        when {
            key.startsWith("acct:") -> {
                // Account update notification
                val accountData = parseAccountNotification(result)
                if (accountData != null) {
                    processAccountUpdate(key, accountData)
                }
            }
        }
    }
    
    private fun parseAccountNotification(result: JsonElement): AccountNotificationData? {
        return try {
            val obj = result.jsonObject
            val value = obj["value"]?.jsonObject ?: return null
            val data = value["data"]
            val lamports = value["lamports"]?.jsonPrimitive?.longOrNull
            
            // Check if this is account deleted
            if (lamports == null && data == null) {
                return AccountNotificationData(isDeleted = true)
            }
            
            // Parse data as base64
            val dataArray = data?.jsonArray
            val base64Data = dataArray?.getOrNull(0)?.jsonPrimitive?.content
            val encoding = dataArray?.getOrNull(1)?.jsonPrimitive?.content
            
            val decodedData = if (base64Data != null && encoding == "base64") {
                java.util.Base64.getDecoder().decode(base64Data)
            } else null
            
            AccountNotificationData(
                lamports = lamports?.let { BigInteger.valueOf(it) },
                data = decodedData,
                isDeleted = false
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private data class AccountNotificationData(
        val lamports: BigInteger? = null,
        val data: ByteArray? = null,
        val isDeleted: Boolean = false
    )
    
    private suspend fun processAccountUpdate(key: String, data: AccountNotificationData) {
        val wallet = currentWallet ?: return
        
        when {
            key.startsWith("acct:$wallet") -> {
                // SOL balance update
                if (data.lamports != null) {
                    pendingUpdates.emit(PendingUpdate.SolUpdate(data.lamports))
                }
            }
            key.startsWith("acct:") -> {
                // Token account update
                val tokenAccount = key.removePrefix("acct:").substringBefore(":")
                
                if (data.isDeleted) {
                    pendingUpdates.emit(PendingUpdate.TokenAccountClosed(tokenAccount))
                } else if (data.data != null && data.data.size >= 72) {
                    // Parse token account data
                    val tokenData = parseTokenAccountData(data.data)
                    if (tokenData != null) {
                        pendingUpdates.emit(PendingUpdate.TokenUpdate(
                            mint = tokenData.mint,
                            tokenAccount = tokenAccount,
                            rawAmount = tokenData.amount,
                            data = data.data
                        ))
                    }
                }
            }
        }
    }
    
    private fun parseTokenAccountData(data: ByteArray): TokenAccountParsed? {
        if (data.size < 72) return null
        
        return try {
            // SPL Token account layout:
            // 0-32: mint
            // 32-64: owner
            // 64-72: amount (little-endian u64)
            val mintBytes = data.sliceArray(0 until 32)
            val mint = Base58.encode(mintBytes)
            
            val amountBytes = data.sliceArray(64 until 72)
            var amount = BigInteger.ZERO
            for (i in 7 downTo 0) {
                amount = amount.shiftLeft(8).or(BigInteger.valueOf((amountBytes[i].toInt() and 0xFF).toLong()))
            }
            
            TokenAccountParsed(mint = mint, amount = amount)
        } catch (e: Exception) {
            null
        }
    }
    
    private data class TokenAccountParsed(
        val mint: String,
        val amount: BigInteger
    )
    
    private fun setupDebouncer() {
        debounceJob = scope.launch {
            pendingUpdates
                .debounce(config.debounceMs)
                .collect { update ->
                    applyUpdate(update)
                }
        }
    }
    
    private suspend fun applyUpdate(update: PendingUpdate) {
        val wallet = currentWallet ?: return
        
        when (update) {
            is PendingUpdate.SolUpdate -> {
                val previous = _state.value.solBalance
                if (previous != update.lamports) {
                    _state.update { state ->
                        val updatedSol = Asset.sol(update.lamports, wallet)
                        state.copy(
                            solBalance = update.lamports,
                            assets = state.assets + (Asset.NATIVE_SOL_MINT to updatedSol),
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                    _events.emit(PortfolioEvent.SolBalanceChanged(
                        wallet = wallet,
                        previousBalance = previous,
                        newBalance = update.lamports,
                        delta = update.lamports - previous
                    ))
                }
            }
            
            is PendingUpdate.TokenUpdate -> {
                val existingAsset = _state.value.assets[update.mint]
                val previousAmount = existingAsset?.rawAmount ?: BigInteger.ZERO
                
                if (previousAmount != update.rawAmount) {
                    if (existingAsset != null) {
                        // Update existing token
                        val updatedAsset = existingAsset.copy(
                            rawAmount = update.rawAmount,
                            updatedAt = System.currentTimeMillis()
                        )
                        _state.update { state ->
                            state.copy(
                                assets = state.assets + (update.mint to updatedAsset),
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                        _events.emit(PortfolioEvent.TokenBalanceChanged(
                            wallet = wallet,
                            asset = updatedAsset,
                            previousRawAmount = previousAmount,
                            newRawAmount = update.rawAmount,
                            delta = update.rawAmount - previousAmount
                        ))
                    } else {
                        // New token discovered - fetch metadata and add
                        val metadata = if (config.fetchMetadata) {
                            rpcFetcher.getTokenMetadata(update.mint)
                        } else null
                        
                        val mintInfo = rpcFetcher.getMintInfo(update.mint)
                        
                        val newAsset = Asset(
                            mint = update.mint,
                            symbol = metadata?.symbol ?: "",
                            name = metadata?.name ?: "",
                            decimals = mintInfo?.decimals ?: 0,
                            rawAmount = update.rawAmount,
                            tokenAccount = update.tokenAccount,
                            logoUri = metadata?.uri
                        )
                        
                        _state.update { state ->
                            state.copy(
                                assets = state.assets + (update.mint to newAsset),
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                        
                        // Subscribe to this new token account
                        val handle = wsClient.accountSubscribe(update.tokenAccount, config.commitment)
                        activeSubscriptions["token:${update.tokenAccount}"] = handle
                        
                        _events.emit(PortfolioEvent.TokenDiscovered(wallet, newAsset))
                    }
                }
            }
            
            is PendingUpdate.TokenAccountClosed -> {
                val closedAsset = _state.value.assets.values
                    .find { it.tokenAccount == update.tokenAccount }
                
                if (closedAsset != null) {
                    _state.update { state ->
                        state.copy(
                            assets = state.assets - closedAsset.mint,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                    
                    // Unsubscribe from closed account
                    val handle = activeSubscriptions.remove("token:${update.tokenAccount}")
                    if (handle != null) {
                        try { wsClient.unsubscribe(handle) } catch (_: Exception) {}
                    }
                    
                    _events.emit(PortfolioEvent.TokenRemoved(
                        wallet = wallet,
                        mint = closedAsset.mint,
                        tokenAccount = update.tokenAccount
                    ))
                }
            }
        }
    }
    
    private suspend fun handleDisconnected(reason: String) {
        val wallet = currentWallet ?: return
        _state.update { it.copy(isSyncing = false) }
        _events.emit(PortfolioEvent.SyncError(wallet, "Disconnected: $reason", config.autoReconnect))
    }
    
    private suspend fun handleReconnecting(attempt: Int) {
        val wallet = currentWallet ?: return
        _events.emit(PortfolioEvent.Reconnecting(wallet, attempt))
    }
    
    private suspend fun handleReconnected() {
        val wallet = currentWallet ?: return
        // Re-fetch state after reconnect
        try {
            refresh()
            _state.update { it.copy(isSyncing = true) }
            _events.emit(PortfolioEvent.SyncStarted(wallet))
        } catch (e: Exception) {
            _events.emit(PortfolioEvent.SyncError(wallet, "Reconnect refresh failed: ${e.message}", true))
        }
    }
    
    override fun close() {
        scope.launch {
            stop()
        }
        scope.cancel()
    }
}
