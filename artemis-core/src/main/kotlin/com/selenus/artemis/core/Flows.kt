package com.selenus.artemis.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Flow-based reactive primitives for Solana data.
 * 
 * Provides reactive streams for monitoring accounts, balances, and network state.
 * Uses Kotlin Flows for backpressure handling and cancellation support.
 * 
 * Example:
 * ```kotlin
 * // Monitor balance changes
 * AccountFlow.balance(rpc, myWallet)
 *     .distinctUntilChanged()
 *     .collect { balance ->
 *         println("Balance: $balance SOL")
 *     }
 * 
 * // Monitor multiple accounts efficiently
 * AccountFlow.accounts(rpc, listOf(acc1, acc2, acc3))
 *     .collect { accounts ->
 *         accounts.forEach { println("${it.pubkey}: ${it.data?.size} bytes") }
 *     }
 * ```
 */
object AccountFlow {
    
    /**
     * Account data snapshot.
     */
    data class AccountInfo(
        val pubkey: String,
        val data: ByteArray?,
        val lamports: Long,
        val owner: String?,
        val executable: Boolean = false,
        val rentEpoch: Long = 0
    ) {
        val exists: Boolean get() = data != null
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccountInfo) return false
            return pubkey == other.pubkey &&
                   data?.contentEquals(other.data) ?: (other.data == null) &&
                   lamports == other.lamports &&
                   owner == other.owner
        }
        
        override fun hashCode(): Int {
            var result = pubkey.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + lamports.hashCode()
            result = 31 * result + (owner?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * Interface for RPC operations needed by AccountFlow.
     */
    interface RpcProvider {
        suspend fun getBalance(pubkey: String): Long
        suspend fun getAccountInfo(pubkey: String): AccountInfo?
        suspend fun getMultipleAccounts(pubkeys: List<String>): List<AccountInfo?>
        suspend fun getSlot(): Long
    }
    
    /**
     * Create a flow that polls balance at the given interval.
     */
    fun balance(
        rpc: RpcProvider,
        pubkey: String,
        intervalMs: Long = 5000L
    ): Flow<Long> = flow {
        while (true) {
            emit(rpc.getBalance(pubkey))
            delay(intervalMs)
        }
    }.distinctUntilChanged()
    
    /**
     * Create a flow that polls account info at the given interval.
     */
    fun account(
        rpc: RpcProvider,
        pubkey: String,
        intervalMs: Long = 5000L
    ): Flow<AccountInfo?> = flow {
        while (true) {
            emit(rpc.getAccountInfo(pubkey))
            delay(intervalMs)
        }
    }.distinctUntilChanged()
    
    /**
     * Create a flow that polls multiple accounts efficiently.
     */
    fun accounts(
        rpc: RpcProvider,
        pubkeys: List<String>,
        intervalMs: Long = 5000L,
        chunkSize: Int = 100
    ): Flow<List<AccountInfo?>> = flow {
        while (true) {
            val results = pubkeys.chunked(chunkSize).flatMap { chunk ->
                rpc.getMultipleAccounts(chunk)
            }
            emit(results)
            delay(intervalMs)
        }
    }
    
    /**
     * Create a flow that polls slot number at the given interval.
     */
    fun slot(
        rpc: RpcProvider,
        intervalMs: Long = 1000L
    ): Flow<Long> = flow {
        while (true) {
            emit(rpc.getSlot())
            delay(intervalMs)
        }
    }.distinctUntilChanged()
    
    /**
     * Create a flow that monitors account existence.
     */
    fun exists(
        rpc: RpcProvider,
        pubkey: String,
        intervalMs: Long = 5000L
    ): Flow<Boolean> = account(rpc, pubkey, intervalMs)
        .map { it?.exists ?: false }
        .distinctUntilChanged()
    
    /**
     * Wait for an account to be created (have data).
     */
    suspend fun awaitAccountCreation(
        rpc: RpcProvider,
        pubkey: String,
        timeoutMs: Long = 30000L,
        pollIntervalMs: Long = 1000L
    ): SolanaResult<AccountInfo, SolanaError.Timeout> {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val account = rpc.getAccountInfo(pubkey)
                if (account?.exists == true) {
                    return@withTimeoutOrNull SolanaResult.success(account)
                }
                delay(pollIntervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            SolanaResult.failure(SolanaError.Timeout("Unreachable", timeoutMs))
        } ?: SolanaResult.failure(SolanaError.Timeout("Account creation timed out", timeoutMs))
    }
    
    /**
     * Wait for a balance change.
     */
    suspend fun awaitBalanceChange(
        rpc: RpcProvider,
        pubkey: String,
        expectedBalance: Long,
        comparison: (Long, Long) -> Boolean = { current, expected -> current >= expected },
        timeoutMs: Long = 30000L,
        pollIntervalMs: Long = 1000L
    ): SolanaResult<Long, SolanaError.Timeout> {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val balance = rpc.getBalance(pubkey)
                if (comparison(balance, expectedBalance)) {
                    return@withTimeoutOrNull SolanaResult.success(balance)
                }
                delay(pollIntervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            SolanaResult.failure(SolanaError.Timeout("Unreachable", timeoutMs))
        } ?: SolanaResult.failure(SolanaError.Timeout("Balance change timed out", timeoutMs))
    }
}

/**
 * Network state monitoring using Flows.
 */
object NetworkFlow {
    
    /**
     * Network health status.
     */
    enum class HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
    
    /**
     * Network state snapshot.
     */
    data class NetworkState(
        val status: HealthStatus,
        val currentSlot: Long,
        val slotBehind: Long? = null,
        val latencyMs: Long? = null,
        val tps: Double? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Interface for network health checks.
     */
    interface HealthProvider {
        suspend fun getHealth(): String
        suspend fun getSlot(): Long
        suspend fun getRecentPerformanceSamples(): List<Long>
    }
    
    /**
     * Create a flow that monitors network health.
     */
    fun health(
        provider: HealthProvider,
        intervalMs: Long = 10000L
    ): Flow<NetworkState> = flow {
        while (true) {
            val startTime = System.currentTimeMillis()
            try {
                val health = provider.getHealth()
                val slot = provider.getSlot()
                val latencyMs = System.currentTimeMillis() - startTime
                
                val status = when (health.lowercase()) {
                    "ok" -> HealthStatus.HEALTHY
                    "behind" -> HealthStatus.DEGRADED
                    else -> HealthStatus.UNHEALTHY
                }
                
                emit(NetworkState(
                    status = status,
                    currentSlot = slot,
                    latencyMs = latencyMs
                ))
            } catch (e: Exception) {
                emit(NetworkState(
                    status = HealthStatus.UNKNOWN,
                    currentSlot = 0,
                    latencyMs = System.currentTimeMillis() - startTime
                ))
            }
            delay(intervalMs)
        }
    }
    
    /**
     * Create a flow that monitors TPS (transactions per second).
     */
    fun tps(
        provider: HealthProvider,
        intervalMs: Long = 5000L
    ): Flow<Double> = flow {
        while (true) {
            try {
                val samples = provider.getRecentPerformanceSamples()
                if (samples.isNotEmpty()) {
                    emit(samples.average())
                }
            } catch (e: Exception) {
                // Skip on error
            }
            delay(intervalMs)
        }
    }
}

/**
 * Transaction confirmation monitoring.
 */
object ConfirmationFlow {
    
    /**
     * Confirmation status.
     */
    enum class ConfirmationStatus {
        PENDING,
        PROCESSED,
        CONFIRMED,
        FINALIZED,
        FAILED,
        TIMEOUT
    }
    
    /**
     * Confirmation state.
     */
    data class ConfirmationState(
        val signature: String,
        val status: ConfirmationStatus,
        val slot: Long? = null,
        val confirmations: Int? = null,
        val error: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Interface for signature status checks.
     */
    interface StatusProvider {
        suspend fun getSignatureStatus(signature: String): Pair<String?, String?>
    }
    
    /**
     * Create a flow that monitors transaction confirmation.
     */
    fun confirmationStatus(
        provider: StatusProvider,
        signature: String,
        intervalMs: Long = 1000L,
        timeoutMs: Long = 60000L
    ): Flow<ConfirmationState> = flow {
        val startTime = System.currentTimeMillis()
        
        emit(ConfirmationState(signature, ConfirmationStatus.PENDING))
        
        while (true) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                emit(ConfirmationState(signature, ConfirmationStatus.TIMEOUT))
                break
            }
            
            try {
                val (status, error) = provider.getSignatureStatus(signature)
                
                when {
                    error != null -> {
                        emit(ConfirmationState(signature, ConfirmationStatus.FAILED, error = error))
                        break
                    }
                    status == "finalized" -> {
                        emit(ConfirmationState(signature, ConfirmationStatus.FINALIZED))
                        break
                    }
                    status == "confirmed" -> {
                        emit(ConfirmationState(signature, ConfirmationStatus.CONFIRMED))
                    }
                    status == "processed" -> {
                        emit(ConfirmationState(signature, ConfirmationStatus.PROCESSED))
                    }
                }
            } catch (e: Exception) {
                // Continue polling on transient errors
            }
            
            delay(intervalMs)
        }
    }
    
    /**
     * Await finalized confirmation.
     */
    suspend fun awaitFinalized(
        provider: StatusProvider,
        signature: String,
        timeoutMs: Long = 60000L,
        pollIntervalMs: Long = 1000L
    ): SolanaResult<ConfirmationState, SolanaError> {
        return confirmationStatus(provider, signature, pollIntervalMs, timeoutMs)
            .first { state ->
                state.status in listOf(
                    ConfirmationStatus.FINALIZED,
                    ConfirmationStatus.FAILED,
                    ConfirmationStatus.TIMEOUT
                )
            }
            .let { state ->
                when (state.status) {
                    ConfirmationStatus.FINALIZED -> SolanaResult.success(state)
                    ConfirmationStatus.FAILED -> SolanaResult.failure(
                        SolanaError.TransactionFailed(state.error ?: "Unknown error", signature)
                    )
                    ConfirmationStatus.TIMEOUT -> SolanaResult.failure(
                        SolanaError.Timeout("Confirmation timed out", timeoutMs)
                    )
                    else -> SolanaResult.failure(SolanaError.Unknown("Unexpected state"))
                }
            }
    }
}
