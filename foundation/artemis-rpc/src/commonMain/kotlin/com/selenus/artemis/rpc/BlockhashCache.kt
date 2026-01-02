package com.selenus.artemis.rpc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.selenus.artemis.runtime.currentTimeMillis

/**
 * Auto-refreshing blockhash cache.
 *
 * Maintains a fresh blockhash in the background, eliminating the need for
 * callers to manually fetch and refresh blockhashes. Automatically detects
 * expiry and refreshes ahead of time.
 *
 * ```kotlin
 * val cache = BlockhashCache(connection)
 * cache.start()
 *
 * // Always returns a valid, recent blockhash
 * val bh = cache.get()
 *
 * // When done
 * cache.close()
 * ```
 */
class BlockhashCache(
    private val rpc: RpcApi,
    private val config: Config = Config(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) {

    data class Config(
        /** Refresh interval in ms. Solana blockhashes last ~60-90s; refresh well before. */
        val refreshIntervalMs: Long = 30_000,
        /** Commitment level for blockhash queries. */
        val commitment: String = "finalized",
        /** Max age in ms before a cached blockhash is considered stale. */
        val maxAgeMs: Long = 60_000
    )

    data class CachedBlockhash(
        val blockhash: String,
        val lastValidBlockHeight: Long,
        val fetchedAtMs: Long
    ) {
        fun isStale(maxAgeMs: Long): Boolean =
            currentTimeMillis() - fetchedAtMs > maxAgeMs
    }

    private val mutex = Mutex()
    private var cached: CachedBlockhash? = null
    private var refreshJob: Job? = null

    private val _current = MutableStateFlow<CachedBlockhash?>(null)
    /** Observable blockhash state. Emits null until the first fetch completes. */
    val current: StateFlow<CachedBlockhash?> = _current

    /**
     * Start the background refresh loop. Safe to call multiple times.
     */
    fun start() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            while (isActive) {
                try {
                    refresh()
                } catch (_: Throwable) {
                    // Retry on next cycle
                }
                delay(config.refreshIntervalMs)
            }
        }
    }

    /**
     * Get a fresh blockhash. If the cache is stale or empty, fetches synchronously.
     */
    suspend fun get(): CachedBlockhash {
        val current = cached
        if (current != null && !current.isStale(config.maxAgeMs)) return current
        return refresh()
    }

    /**
     * Get just the blockhash string.
     */
    suspend fun getBlockhash(): String = get().blockhash

    /**
     * Force a refresh of the cached blockhash.
     */
    suspend fun refresh(): CachedBlockhash {
        mutex.withLock {
            val result = rpc.getLatestBlockhash(config.commitment)
            val entry = CachedBlockhash(
                blockhash = result.blockhash,
                lastValidBlockHeight = result.lastValidBlockHeight,
                fetchedAtMs = currentTimeMillis()
            )
            cached = entry
            _current.value = entry
            return entry
        }
    }

    fun close() {
        refreshJob?.cancel()
        scope.cancel()
    }
}
