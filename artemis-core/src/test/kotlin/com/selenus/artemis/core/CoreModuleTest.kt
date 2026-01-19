package com.selenus.artemis.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-core module (now merged into artemis-runtime).
 * Tests SolanaResult, StateManager, and Flow utilities.
 */
class CoreModuleTest {

    // ===== SolanaResult Tests =====

    @Test
    fun testSolanaResultSuccess() {
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Success(42)
        
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(42, result.getOrNull())
        assertNull(result.errorOrNull())
        assertEquals(42, result.getOrThrow())
        assertEquals(42, result.getOrDefault(0))
    }

    @Test
    fun testSolanaResultFailure() {
        val error = SolanaError.RpcError("Test error", code = 500)
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Failure(error)
        
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertNotNull(result.errorOrNull())
        assertEquals("Test error", result.errorOrNull()?.message)
        assertEquals(0, result.getOrDefault(0))
    }

    @Test
    fun testSolanaResultMap() {
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Success(10)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isSuccess)
        assertEquals(20, mapped.getOrNull())
    }

    @Test
    fun testSolanaResultMapOnFailure() {
        val error = SolanaError.Unknown("Error")
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Failure(error)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isFailure)
        assertEquals("Error", mapped.errorOrNull()?.message)
    }

    @Test
    fun testSolanaResultGetOrElse() {
        val error = SolanaError.NetworkError("Connection failed", isRetryable = true)
        val result: SolanaResult<Int, SolanaError.NetworkError> = SolanaResult.Failure(error)
        val value = result.getOrElse { -1 }
        
        assertEquals(-1, value)
    }

    @Test
    fun testSolanaResultCatching() {
        val success = SolanaResult.catching { 42 }
        assertTrue(success.isSuccess)
        assertEquals(42, success.getOrNull())
        
        val failure = SolanaResult.catching { throw RuntimeException("Oops") }
        assertTrue(failure.isFailure)
        assertEquals("Oops", failure.errorOrNull()?.message)
    }

    @Test
    fun testSolanaResultCombine() {
        val r1 = SolanaResult.Success(1)
        val r2 = SolanaResult.Success(2)
        val r3 = SolanaResult.Success(3)
        
        val combined = SolanaResult.combine(r1, r2, r3)
        assertTrue(combined.isSuccess)
        assertEquals(listOf(1, 2, 3), combined.getOrNull())
    }

    @Test
    fun testSolanaResultCombineWithFailure() {
        val r1 = SolanaResult.Success(1)
        val r2 = SolanaResult.Failure(SolanaError.Timeout())
        val r3 = SolanaResult.Success(3)
        
        val combined = SolanaResult.combine(r1, r2, r3)
        assertTrue(combined.isFailure)
        assertTrue(combined.errorOrNull() is SolanaError.Timeout)
    }

    // ===== StateManager Tests =====

    @Test
    fun testStateManagerInitialValue() {
        val manager = StateManager(100)
        assertEquals(100, manager.value)
    }

    @Test
    fun testStateManagerUpdate() {
        val manager = StateManager(10)
        manager.update { this + 5 }
        assertEquals(15, manager.value)
    }

    @Test
    fun testStateManagerSet() {
        val manager = StateManager("initial")
        manager.set("updated")
        assertEquals("updated", manager.value)
    }

    @Test
    fun testStateManagerStateFlow() {
        runBlocking {
            val manager = StateManager(0)
            val initialValue = manager.state.first()
            assertEquals(0, initialValue)
            
            manager.update { this + 1 }
            assertEquals(1, manager.value)
        }
    }

    @Test
    fun testStateManagerSelect() {
        runBlocking {
            data class TestState(val count: Int, val name: String)
            
            val manager = StateManager(TestState(0, "test"))
            val countFlow = manager.select { it.count }
            
            assertEquals(0, countFlow.first())
            
            manager.update { copy(count = 5) }
            assertEquals(5, manager.value.count)
        }
    }

    // ===== WalletState Tests =====

    @Test
    fun testWalletStateDefaults() {
        val state = WalletState()
        
        assertNull(state.publicKey)
        assertEquals(0L, state.balanceLamports)
        assertFalse(state.isConnected)
        assertFalse(state.isLoading)
        assertNull(state.lastError)
        assertEquals(WalletState.Network.MAINNET, state.network)
        assertTrue(state.pendingTransactions.isEmpty())
    }

    @Test
    fun testWalletStateUpdate() {
        val state = WalletState()
            .copy(
                publicKey = "11111111111111111111111111111111",
                balanceLamports = 1_000_000_000,
                isConnected = true
            )
        
        assertEquals("11111111111111111111111111111111", state.publicKey)
        assertEquals(1_000_000_000L, state.balanceLamports)
        assertTrue(state.isConnected)
        assertEquals(1.0, state.balanceSol, 0.0001)
        assertTrue(state.hasBalance)
    }

    @Test
    fun testNetworkEnum() {
        val networks = WalletState.Network.values()
        assertEquals(4, networks.size)
        assertTrue(WalletState.Network.MAINNET in networks)
        assertTrue(WalletState.Network.DEVNET in networks)
        assertTrue(WalletState.Network.TESTNET in networks)
        assertTrue(WalletState.Network.LOCALNET in networks)
    }

    // ===== PendingTransaction Tests =====

    @Test
    fun testPendingTransactionCreation() {
        val pending = PendingTransaction(
            signature = "abc123",
            status = PendingTransaction.TransactionStatus.PENDING,
            description = "Test transaction"
        )
        
        assertEquals("abc123", pending.signature)
        assertEquals(PendingTransaction.TransactionStatus.PENDING, pending.status)
        assertEquals("Test transaction", pending.description)
        assertTrue(pending.createdAt > 0)
    }

    @Test
    fun testTransactionStatusEnum() {
        val statuses = PendingTransaction.TransactionStatus.values()
        assertTrue(PendingTransaction.TransactionStatus.PENDING in statuses)
        assertTrue(PendingTransaction.TransactionStatus.SENT in statuses)
        assertTrue(PendingTransaction.TransactionStatus.CONFIRMED in statuses)
        assertTrue(PendingTransaction.TransactionStatus.FINALIZED in statuses)
        assertTrue(PendingTransaction.TransactionStatus.FAILED in statuses)
    }

    // ===== AccountFlow.AccountInfo Tests =====

    @Test
    fun testAccountInfoExists() {
        val info = AccountFlow.AccountInfo(
            pubkey = "test",
            data = byteArrayOf(1, 2, 3),
            lamports = 1000,
            owner = "owner"
        )
        
        assertEquals("test", info.pubkey)
        assertEquals(3, info.data?.size)
        assertEquals(1000L, info.lamports)
        assertEquals("owner", info.owner)
        assertTrue(info.exists)
    }

    @Test
    fun testAccountInfoEquality() {
        val info1 = AccountFlow.AccountInfo(
            pubkey = "test",
            data = byteArrayOf(1, 2, 3),
            lamports = 1000,
            owner = "owner"
        )
        val info2 = AccountFlow.AccountInfo(
            pubkey = "test",
            data = byteArrayOf(1, 2, 3),
            lamports = 1000,
            owner = "owner"
        )
        
        // Note: data class equality doesn't compare arrays by content
        assertEquals(info1.pubkey, info2.pubkey)
        assertEquals(info1.lamports, info2.lamports)
        assertEquals(info1.owner, info2.owner)
    }

    // ===== SolanaError Tests =====

    @Test
    fun testSolanaErrorSubclasses() {
        // Test RpcError
        val rpcError = SolanaError.RpcError("RPC failed", code = 500)
        assertEquals("RPC failed", rpcError.message)
        assertEquals(500, rpcError.code)
        assertTrue(rpcError.toException() is SolanaException)
        
        // Test InsufficientFunds
        val fundsError = SolanaError.InsufficientFunds(
            message = "Not enough SOL",
            required = 1000000,
            available = 500000
        )
        assertEquals("Not enough SOL", fundsError.message)
        assertEquals(1000000, fundsError.required)
        assertEquals(500000, fundsError.available)
        
        // Test Unknown with cause
        val cause = RuntimeException("underlying cause")
        val unknownError = SolanaError.Unknown("Something went wrong", cause)
        assertEquals("Something went wrong", unknownError.message)
        assertEquals(cause, unknownError.cause)
    }

    @Test
    fun testSolanaErrorTypes() {
        // Verify all error types can be instantiated
        val errors = listOf(
            SolanaError.RpcError("RPC error"),
            SolanaError.TransactionFailed("Tx failed"),
            SolanaError.BlockhashExpired(),
            SolanaError.InsufficientFunds("Not enough"),
            SolanaError.AccountNotFound("Not found", pubkey = "test"),
            SolanaError.InvalidAccountData("Invalid data"),
            SolanaError.NetworkError("Network error"),
            SolanaError.Timeout(),
            SolanaError.InvalidInput("Invalid input"),
            SolanaError.SignatureFailed("Signature failed"),
            SolanaError.Unknown("Unknown error")
        )
        
        assertEquals(11, errors.size)
        errors.forEach { error ->
            assertNotNull(error.message)
            assertNotNull(error.toException())
        }
    }

    @Test
    fun testSolanaException() {
        val error = SolanaError.Timeout(message = "Request timed out", timeoutMs = 5000)
        val exception = error.toException()
        
        assertTrue(exception is SolanaException)
        assertEquals(error, exception.error)
        assertEquals("Request timed out", exception.message)
    }
}
