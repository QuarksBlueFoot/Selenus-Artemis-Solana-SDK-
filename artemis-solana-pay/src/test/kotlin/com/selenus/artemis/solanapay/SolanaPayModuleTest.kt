package com.selenus.artemis.solanapay

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.Assume
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertContains
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58
import java.math.BigDecimal

/**
 * Comprehensive tests for artemis-solana-pay module v1.2.0 enhancements
 * 
 * Tests SolanaPayManager with payment sessions, URI generation,
 * QR codes, and verification
 */
class SolanaPayModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== SolanaPayManager Core Tests ====================

    @Test
    fun `SolanaPayManager - create instance with default config`() {
        runBlocking {
            val manager = SolanaPayManager(testScope)
            assertNotNull(manager)
        }
    }

    @Test
    fun `SolanaPayManager - create instance with custom config`() {
        runBlocking {
            val manager = SolanaPayManager(
                testScope,
                SolanaPayManager.Config(
                    defaultCluster = SolanaPayManager.Cluster.DEVNET,
                    paymentTimeoutMs = 120_000L,
                    pollingIntervalMs = 1000L,
                    maxRetries = 5
                )
            )
            assertNotNull(manager)
        }
    }

    // ==================== TransferRequest Tests ====================

    @Test
    fun `TransferRequest - create simple SOL transfer`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val request = SolanaPayManager.TransferRequest(
            recipient = keypair.publicKey,
            amount = BigDecimal("1.5"),
            label = "Test Payment",
            message = "Payment for test services"
        )

        assertNotNull(request)
        assertEquals(keypair.publicKey, request.recipient)
        assertEquals(BigDecimal("1.5"), request.amount)
        assertEquals("Test Payment", request.label)
    }

    @Test
    fun `TransferRequest - generate URI`() {
        val recipient = Keypair.generate().publicKey

        val request = SolanaPayManager.TransferRequest(
            recipient = recipient,
            amount = BigDecimal("0.001")
        )

        val uri = request.toUri()

        assertNotNull(uri)
        assertTrue(uri.startsWith("solana:"))
        assertContains(uri, recipient.toBase58())
    }

    @Test
    fun `TransferRequest - generate URI with all parameters`() {
        val recipient = Keypair.generate().publicKey
        val splToken = Keypair.generate().publicKey
        val reference = Keypair.generate().publicKey

        val request = SolanaPayManager.TransferRequest(
            recipient = recipient,
            amount = BigDecimal("10.5"),
            splToken = splToken,
            reference = listOf(reference),
            label = "Product Purchase",
            message = "Thank you for your order",
            memo = "order-12345"
        )

        val uri = request.toUri()

        assertNotNull(uri)
        assertTrue(uri.startsWith("solana:"))
        assertContains(uri, "amount=")
        assertContains(uri, "spl-token=")
        assertContains(uri, "reference=")
        assertContains(uri, "label=")
    }

    @Test
    fun `TransferRequest - toQRData returns URI`() {
        val recipient = Keypair.generate().publicKey

        val request = SolanaPayManager.TransferRequest(
            recipient = recipient,
            amount = BigDecimal("1.0")
        )

        val qrData = request.toQRData()
        val uri = request.toUri()

        assertEquals(uri, qrData)
    }

    // ==================== TransactionRequest Tests ====================

    @Test
    fun `TransactionRequest - create and generate URI`() {
        val request = SolanaPayManager.TransactionRequest(
            link = "https://example.com/api/pay/session-123",
            label = "Checkout",
            message = "Complete your purchase"
        )

        val uri = request.toUri()

        assertNotNull(uri)
        assertTrue(uri.startsWith("solana:"))
        // Link should be URL encoded
        assertContains(uri, "https%3A")
    }

    @Test
    fun `TransactionRequest - minimal request`() {
        val request = SolanaPayManager.TransactionRequest(
            link = "https://api.example.com/transaction"
        )

        val uri = request.toUri()

        assertNotNull(uri)
        assertTrue(uri.startsWith("solana:"))
    }

    // ==================== SolanaPayUri Tests ====================

    @Test
    fun `SolanaPayUri - build simple URI`() {
        val recipient = Keypair.generate().publicKey

        val uri = SolanaPayUri.build(
            SolanaPayUri.Request(
                recipient = recipient,
                amount = BigDecimal("5.0")
            )
        )

        assertNotNull(uri)
        assertTrue(uri.startsWith("solana:"))
        assertContains(uri, recipient.toBase58())
        assertContains(uri, "amount=5")
    }

    @Test
    fun `SolanaPayUri - parse transfer URI`() {
        val recipient = Keypair.generate().publicKey
        val originalUri = "solana:${recipient.toBase58()}?amount=2.5&label=Test"

        val parsed = SolanaPayUri.parse(originalUri)

        assertNotNull(parsed)
        assertEquals(recipient, parsed.recipient)
        assertEquals(BigDecimal("2.5"), parsed.amount)
        assertEquals("Test", parsed.label)
    }

    @Test
    fun `SolanaPayUri - parse URI with token`() {
        val recipient = Keypair.generate().publicKey
        val token = Keypair.generate().publicKey
        val originalUri = "solana:${recipient.toBase58()}?amount=100&spl-token=${token.toBase58()}"

        val parsed = SolanaPayUri.parse(originalUri)

        assertNotNull(parsed)
        assertEquals(recipient, parsed.recipient)
        assertEquals(token, parsed.splToken)
    }

    @Test
    fun `SolanaPayUri - parse valid URI`() {
        val recipient = Keypair.generate().publicKey
        val uri = "solana:${recipient.toBase58()}?amount=1.0"

        val parsed = SolanaPayUri.parse(uri)
        
        assertNotNull(parsed)
        assertEquals(recipient, parsed.recipient)
        assertEquals(BigDecimal("1.0"), parsed.amount)
    }

    @Test
    fun `SolanaPayUri - parse invalid URI throws`() {
        var threw = false
        try {
            SolanaPayUri.parse("http://example.com")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ==================== Cluster Tests ====================

    @Test
    fun `Cluster - all values available`() {
        val mainnet = SolanaPayManager.Cluster.MAINNET
        val devnet = SolanaPayManager.Cluster.DEVNET
        val testnet = SolanaPayManager.Cluster.TESTNET

        assertNotNull(mainnet)
        assertNotNull(devnet)
        assertNotNull(testnet)
    }

    // ==================== PaymentSession Tests ====================

    @Test
    fun `PaymentSession - structure validation`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)
        val reference = Keypair.generate().publicKey

        val session = SolanaPayManager.PaymentSession(
            sessionId = "session-001",
            request = SolanaPayManager.TransferRequest(
                recipient = keypair.publicKey,
                amount = BigDecimal("1.0")
            ),
            reference = reference,
            createdAtMs = System.currentTimeMillis(),
            status = SolanaPayManager.PaymentStatus.PENDING,
            signature = null,
            confirmedSlot = null,
            payer = null,
            amountReceived = null
        )

        assertNotNull(session)
        assertEquals("session-001", session.sessionId)
        assertEquals(SolanaPayManager.PaymentStatus.PENDING, session.status)
        assertTrue(session.signature == null)
    }

    // ==================== PaymentStatus Tests ====================

    @Test
    fun `PaymentStatus - all values available`() {
        val pending = SolanaPayManager.PaymentStatus.PENDING
        val confirmed = SolanaPayManager.PaymentStatus.CONFIRMED
        val failed = SolanaPayManager.PaymentStatus.FAILED
        val expired = SolanaPayManager.PaymentStatus.EXPIRED

        assertNotNull(pending)
        assertNotNull(confirmed)
        assertNotNull(failed)
        assertNotNull(expired)
    }

    // ==================== Reference Generation Tests ====================

    @Test
    fun `Reference - generate unique references`() {
        val ref1 = Keypair.generate()
        val ref2 = Keypair.generate()

        // Each reference should be unique
        assertTrue(ref1.publicKey != ref2.publicKey)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `SolanaPayManager Integration - create payment request`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)

            val manager = SolanaPayManager(
                testScope,
                SolanaPayManager.Config(
                    defaultCluster = SolanaPayManager.Cluster.DEVNET
                )
            )

            val request = SolanaPayManager.TransferRequest(
                recipient = keypair.publicKey,
                amount = BigDecimal("0.001"),
                label = "Integration Test",
                message = "Testing Solana Pay integration"
            )

            println("Solana Pay Integration Test:")
            println("  Recipient: ${keypair.publicKey.toBase58()}")
            println("  Amount: 0.001 SOL")
            println("  URI: ${request.toUri()}")

            val uri = request.toUri()
            assertNotNull(uri)
            assertTrue(uri.startsWith("solana:"))
        }
    }
}
