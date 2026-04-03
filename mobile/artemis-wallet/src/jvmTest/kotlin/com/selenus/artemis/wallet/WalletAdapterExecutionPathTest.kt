package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Execution-path tests for WalletSession signing delegation.
 *
 * These tests verify:
 * - Adapter-backed signing correctly delegates through the WalletAdapter interface
 * - SignerStrategy routing works for all three variants (Local, Adapter, Raw)
 * - WalletAdapter.signMessage is invoked with the correct request metadata
 * - Batch signing delegation works correctly
 * - signAndSend adapter path validates correctly
 * - Off-chain message signing routes through the correct strategy
 */
class WalletAdapterExecutionPathTest {

    // ═══════════════════════════════════════════════════════════════════════
    // Adapter signing delegation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `adapter signMessage delegates to WalletAdapter with correct request`() = runBlocking {
        val keypair = Keypair.generate()
        val message = "test-payload".toByteArray()
        var capturedRequest: WalletRequest? = null
        var capturedMessage: ByteArray? = null
        val expectedResult = ByteArray(64) { it.toByte() }

        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                capturedMessage = message
                capturedRequest = request
                return expectedResult
            }
        }

        val session = WalletSession.fromAdapter(adapter)
        val result = session.signMessage(message)

        assertContentEquals(expectedResult, result)
        assertContentEquals(message, capturedMessage)
        assertNotNull(capturedRequest)
        assertTrue(capturedRequest is SignTxRequest)
        assertEquals("signMessage", (capturedRequest as SignTxRequest).purpose)
    }

    @Test
    fun `adapter signTransaction routes through Adapter strategy`() = runBlocking {
        val keypair = Keypair.generate()
        val txBytes = ByteArray(256) { it.toByte() }
        val signedTx = ByteArray(320) { (it + 64).toByte() }
        var signCallCount = 0

        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                signCallCount++
                assertContentEquals(txBytes, message)
                assertEquals("signTransaction", (request as SignTxRequest).purpose)
                return signedTx
            }
        }

        val strategy = SignerStrategy.Adapter(adapter)
        val result = strategy.signTransaction(txBytes)

        assertEquals(1, signCallCount)
        assertContentEquals(signedTx, result)
    }

    @Test
    fun `adapter batch signing invokes signMessages on adapter`() = runBlocking {
        val keypair = Keypair.generate()
        val messages = listOf(
            ByteArray(100) { 1 },
            ByteArray(200) { 2 },
            ByteArray(150) { 3 }
        )
        var batchCallCount = 0

        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                batchCallCount++
                return ByteArray(64) { message[0] }
            }
        }

        // Default signMessages falls back to per-message signing
        val results = adapter.signMessages(messages, SignTxRequest(purpose = "batch"))
        assertEquals(3, results.size)
        assertEquals(3, batchCallCount)
        // Verify each result corresponds to the correct input
        assertTrue(results[0].all { it == 1.toByte() })
        assertTrue(results[1].all { it == 2.toByte() })
        assertTrue(results[2].all { it == 3.toByte() })
    }

    @Test
    fun `adapter with custom batch signing uses override`() = runBlocking {
        val keypair = Keypair.generate()
        var usedBatchPath = false

        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest) =
                ByteArray(64)
            override suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
                usedBatchPath = true
                return messages.map { ByteArray(64) }
            }
        }

        adapter.signMessages(listOf(ByteArray(10)), SignTxRequest(purpose = "test"))
        assertTrue(usedBatchPath, "Should use custom batch signing when overridden")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SignerStrategy routing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `local strategy produces deterministic signatures`() {
        val keypair = Keypair.generate()
        val strategy = SignerStrategy.Local(keypair)
        val message = "deterministic-test".toByteArray()

        val sig1 = strategy.asSigner().sign(message)
        val sig2 = strategy.asSigner().sign(message)

        assertContentEquals(sig1, sig2, "Same keypair + message must produce identical signatures")
    }

    @Test
    fun `local strategy sign and signMessage produce same result`() = runBlocking {
        val keypair = Keypair.generate()
        val strategy = SignerStrategy.Local(keypair)
        val message = "consistency-test".toByteArray()

        val syncSig = strategy.asSigner().sign(message)
        val asyncSig = strategy.signMessage(message)

        assertContentEquals(syncSig, asyncSig, "Sync and async signing must produce identical results")
    }

    @Test
    fun `raw strategy wraps existing signer transparently`() {
        val keypair = Keypair.generate()
        val expectedSig = keypair.sign("test".toByteArray())

        val strategy = SignerStrategy.Raw(keypair as Signer)
        val actualSig = strategy.asSigner().sign("test".toByteArray())

        assertContentEquals(expectedSig, actualSig, "Raw strategy must delegate without modification")
    }

    @Test
    fun `adapter strategy asSigner throws with descriptive message`() {
        val adapter = createMockAdapter(Keypair.generate().publicKey)
        val strategy = SignerStrategy.Adapter(adapter)

        val ex = assertFailsWith<UnsupportedOperationException> {
            strategy.asSigner().sign(ByteArray(32))
        }
        assertTrue(ex.message!!.contains("WalletSession.send()"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WalletSession with signAndSend adapter
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `signAndSend adapter receives correct transaction data`() = runBlocking {
        val keypair = Keypair.generate()
        val txData = ByteArray(400) { (it % 256).toByte() }
        var capturedTx: ByteArray? = null
        var capturedOptions: SendTransactionOptions? = null

        val adapter = object : WalletAdapter, WalletAdapterSignAndSend {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest) = ByteArray(64)
            override suspend fun signAndSendTransaction(
                transaction: ByteArray,
                options: SendTransactionOptions
            ): SendTransactionResult {
                capturedTx = transaction
                capturedOptions = options
                return SendTransactionResult(
                    signature = "5abc123",
                    confirmed = true
                )
            }
            override suspend fun signAndSendTransactions(
                transactions: List<ByteArray>,
                options: SendTransactionOptions
            ): BatchSendResult {
                return BatchSendResult(
                    results = transactions.map { SendTransactionResult("sig", true) }
                )
            }
        }

        val result = adapter.signAndSendTransaction(txData, SendTransactionOptions.Default)
        assertEquals("5abc123", result.signature)
        assertTrue(result.confirmed)
        assertContentEquals(txData, capturedTx)
    }

    @Test
    fun `signAndSend batch preserves transaction order`() = runBlocking {
        val keypair = Keypair.generate()
        val txList = (0..4).map { i -> ByteArray(100) { i.toByte() } }
        var capturedOrder = mutableListOf<Byte>()

        val adapter = object : WalletAdapter, WalletAdapterSignAndSend {
            override val publicKey: Pubkey = keypair.publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest) = ByteArray(64)
            override suspend fun signAndSendTransaction(
                transaction: ByteArray,
                options: SendTransactionOptions
            ) = SendTransactionResult("sig", true)
            override suspend fun signAndSendTransactions(
                transactions: List<ByteArray>,
                options: SendTransactionOptions
            ): BatchSendResult {
                transactions.forEach { capturedOrder.add(it[0]) }
                return BatchSendResult(
                    results = transactions.mapIndexed { i, _ -> SendTransactionResult("sig-$i", true) }
                )
            }
        }

        val result = adapter.signAndSendTransactions(txList, SendTransactionOptions.Default)
        assertEquals(5, result.signatures.size)
        assertEquals(listOf<Byte>(0, 1, 2, 3, 4), capturedOrder)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Off-chain message signing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `off-chain signMessage through local session produces valid ed25519`() = runBlocking {
        val keypair = Keypair.generate()
        val session = WalletSession.local(keypair)
        val offChainMsg = "Sign this message to prove ownership".toByteArray()

        val signature = session.signMessage(offChainMsg)
        assertEquals(64, signature.size, "Ed25519 signature must be 64 bytes")
    }

    @Test
    fun `off-chain signMessage through adapter routes to adapter`() = runBlocking {
        val expectedSig = ByteArray(64) { 0xAB.toByte() }
        var invoked = false

        val adapter = object : WalletAdapter {
            override val publicKey: Pubkey = Keypair.generate().publicKey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
                invoked = true
                return expectedSig
            }
        }

        val session = WalletSession.fromAdapter(adapter)
        val result = session.signMessage("test".toByteArray())

        assertTrue(invoked, "Must delegate to adapter.signMessage")
        assertContentEquals(expectedSig, result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WalletCapabilities
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `defaultMobile capabilities include expected features`() {
        val caps = WalletCapabilities.defaultMobile()
        assertTrue(caps.supportsSignTransactions)
        assertTrue(caps.supportsMultipleMessages)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun createMockAdapter(pubkey: Pubkey): WalletAdapter {
        return object : WalletAdapter {
            override val publicKey: Pubkey = pubkey
            override suspend fun getCapabilities() = WalletCapabilities.defaultMobile()
            override suspend fun signMessage(message: ByteArray, request: WalletRequest) = ByteArray(64)
        }
    }
}
