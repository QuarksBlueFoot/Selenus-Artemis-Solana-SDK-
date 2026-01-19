package com.selenus.artemis.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * StateManager - Reactive state management for Solana wallet applications.
 * 
 * Provides a centralized, observable state container that integrates with
 * Kotlin Flows for reactive UI updates. Inspired by Redux patterns but
 * optimized for Kotlin coroutines and Solana-specific use cases.
 * 
 * Example:
 * ```kotlin
 * val walletState = StateManager<WalletState>(WalletState())
 * 
 * // Observe state changes
 * walletState.state.collect { state ->
 *     updateUI(state)
 * }
 * 
 * // Update state
 * walletState.update { copy(balance = newBalance) }
 * ```
 */
class StateManager<T>(initialState: T) {
    
    private val _state = MutableStateFlow(initialState)
    
    /**
     * Observable state flow.
     */
    val state: StateFlow<T> = _state.asStateFlow()
    
    /**
     * Current state value.
     */
    val value: T get() = _state.value
    
    /**
     * Update state atomically.
     */
    fun update(transform: T.() -> T) {
        _state.update { it.transform() }
    }
    
    /**
     * Set state directly.
     */
    fun set(newState: T) {
        _state.value = newState
    }
    
    /**
     * Observe a specific field.
     */
    fun <R> select(selector: (T) -> R): Flow<R> = state.map(selector).distinctUntilChanged()
    
    /**
     * Combine with another state manager.
     */
    fun <U, R> combineWith(other: StateManager<U>, combine: (T, U) -> R): Flow<R> {
        return state.combine(other.state, combine).distinctUntilChanged()
    }
}

/**
 * Wallet state for mobile wallet applications.
 */
data class WalletState(
    val publicKey: String? = null,
    val balanceLamports: Long = 0,
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val lastError: SolanaError? = null,
    val network: Network = Network.MAINNET,
    val pendingTransactions: List<PendingTransaction> = emptyList()
) {
    val balanceSol: Double get() = balanceLamports / 1_000_000_000.0
    val hasBalance: Boolean get() = balanceLamports > 0
    
    enum class Network(val rpcUrl: String) {
        MAINNET("https://api.mainnet-beta.solana.com"),
        DEVNET("https://api.devnet.solana.com"),
        TESTNET("https://api.testnet.solana.com"),
        LOCALNET("http://localhost:8899")
    }
}

/**
 * Pending transaction state.
 */
data class PendingTransaction(
    val signature: String,
    val status: TransactionStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String? = null
) {
    enum class TransactionStatus {
        PENDING,
        SENT,
        CONFIRMED,
        FINALIZED,
        FAILED
    }
}

/**
 * Token balance state.
 */
data class TokenBalance(
    val mint: String,
    val amount: Long,
    val decimals: Int,
    val symbol: String? = null,
    val name: String? = null,
    val logoUri: String? = null
) {
    val uiAmount: Double get() = amount / Math.pow(10.0, decimals.toDouble())
}

/**
 * NFT state.
 */
data class NftState(
    val mint: String,
    val name: String?,
    val symbol: String?,
    val uri: String?,
    val imageUri: String? = null,
    val collection: String? = null,
    val isCompressed: Boolean = false
)

/**
 * Cache manager for efficient data fetching.
 */
class CacheManager<K, V>(
    private val scope: CoroutineScope,
    private val ttlMs: Long = 30_000L
) {
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttlMs: Long): Boolean = 
            System.currentTimeMillis() - timestamp > ttlMs
    }
    
    private val cache = mutableMapOf<K, CacheEntry<V>>()
    private val mutex = Mutex()
    
    /**
     * Get cached value or compute it.
     */
    suspend fun getOrPut(key: K, compute: suspend () -> V): V {
        val existingEntry = mutex.withLock {
            val entry = cache[key]
            if (entry != null && !entry.isExpired(ttlMs)) entry else null
        }
        
        if (existingEntry != null) {
            return existingEntry.value
        }
        
        val value = compute()
        
        mutex.withLock {
            cache[key] = CacheEntry(value)
        }
        
        return value
    }
    
    /**
     * Get cached value if present and not expired.
     */
    suspend fun get(key: K): V? {
        return mutex.withLock {
            val entry = cache[key]
            if (entry != null && !entry.isExpired(ttlMs)) {
                entry.value
            } else {
                cache.remove(key)
                null
            }
        }
    }
    
    /**
     * Put value in cache.
     */
    suspend fun put(key: K, value: V) {
        mutex.withLock {
            cache[key] = CacheEntry(value)
        }
    }
    
    /**
     * Invalidate a specific key.
     */
    suspend fun invalidate(key: K) {
        mutex.withLock {
            cache.remove(key)
        }
    }
    
    /**
     * Clear all cache.
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }
    
    /**
     * Remove expired entries.
     */
    suspend fun cleanup() {
        mutex.withLock {
            val expired = cache.entries.filter { it.value.isExpired(ttlMs) }.map { it.key }
            expired.forEach { cache.remove(it) }
        }
    }
    
    /**
     * Start periodic cleanup.
     */
    fun startPeriodicCleanup(intervalMs: Long = 60_000L): Job {
        return scope.launch {
            while (isActive) {
                delay(intervalMs)
                cleanup()
            }
        }
    }
}

/**
 * Event bus for decoupled communication.
 */
object EventBus {
    
    private val _events = MutableSharedFlow<SolanaEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    
    val events: SharedFlow<SolanaEvent> = _events.asSharedFlow()
    
    /**
     * Emit an event.
     */
    suspend fun emit(event: SolanaEvent) {
        _events.emit(event)
    }
    
    /**
     * Observe events of a specific type.
     */
    inline fun <reified E : SolanaEvent> observe(): Flow<E> = events.filterIsInstance()
}

/**
 * Base class for Solana events.
 */
sealed class SolanaEvent {
    abstract val timestamp: Long
    
    data class Connected(
        val publicKey: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class Disconnected(
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class BalanceChanged(
        val publicKey: String,
        val oldBalance: Long,
        val newBalance: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class TransactionSent(
        val signature: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class TransactionConfirmed(
        val signature: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class TransactionFailed(
        val signature: String?,
        val error: SolanaError,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class NetworkChanged(
        val network: WalletState.Network,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
    
    data class AccountUpdated(
        val publicKey: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SolanaEvent()
}
