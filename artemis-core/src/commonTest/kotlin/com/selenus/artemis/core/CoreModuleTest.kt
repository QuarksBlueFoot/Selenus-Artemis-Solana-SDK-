package com.selenus.artemis.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-core module.
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
        val error = object : SolanaError {
            override val code: String = "test_error"
            override val message: String = "Test error"
            override fun toException(): Exception = RuntimeException(message)
        }
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Failure(error)
        
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertNotNull(result.errorOrNull())
        assertEquals("test_error", result.errorOrNull()?.code)
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
        val error = object : SolanaError {
            override val code: String = "err"
            override val message: String = "Error"
            override fun toException(): Exception = RuntimeException(message)
        }
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Failure(error)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isFailure)
        assertEquals("err", mapped.errorOrNull()?.code)
    }

    @Test
    fun testSolanaResultGetOrElse() {
        val error = object : SolanaError {
            override val code: String = "test"
            override val message: String = "Test"
            override fun toException(): Exception = RuntimeException(message)
        }
        val result: SolanaResult<Int, SolanaError> = SolanaResult.Failure(error)
        val value = result.getOrElse { -1 }
        
        assertEquals(-1, value)
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
        assertEquals(Network.MAINNET, state.network)
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
    }

    @Test
    fun testNetworkEnum() {
        assertEquals(3, Network.values().size)
        assertTrue(Network.MAINNET in Network.values())
        assertTrue(Network.DEVNET in Network.values())
        assertTrue(Network.TESTNET in Network.values())
    }

    // ===== PendingTransaction Tests =====

    @Test
    fun testPendingTransactionCreation() {
        val pending = PendingTransaction(
            signature = "abc123",
            createdAtMs = System.currentTimeMillis(),
            status = TransactionStatus.PENDING
        )
        
        assertEquals("abc123", pending.signature)
        assertEquals(TransactionStatus.PENDING, pending.status)
    }

    @Test
    fun testTransactionStatusEnum() {
        assertTrue(TransactionStatus.PENDING in TransactionStatus.values())
        assertTrue(TransactionStatus.CONFIRMED in TransactionStatus.values())
        assertTrue(TransactionStatus.FAILED in TransactionStatus.values())
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
        
        assertTrue(info.exists)
        assertEquals("test", info.pubkey)
        assertEquals(1000L, info.lamports)
    }

    @Test
    fun testAccountInfoNotExists() {
        val info = AccountFlow.AccountInfo(
            pubkey = "test",
            data = null,
            lamports = 0,
            owner = null
        )
        
        assertFalse(info.exists)
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
        
        assertEquals(info1, info2)
        assertEquals(info1.hashCode(), info2.hashCode())
    }

    // ===== SolanaError Interface Tests =====

    @Test
    fun testCustomSolanaError() {
        val error = object : SolanaError {
            override val code: String = "custom_error"
            override val message: String = "Custom error message"
            override fun toException(): Exception = RuntimeException(message)
        }
        
        assertEquals("custom_error", error.code)
        assertEquals("Custom error message", error.message)
        assertTrue(error.toException() is RuntimeException)
    }
}
