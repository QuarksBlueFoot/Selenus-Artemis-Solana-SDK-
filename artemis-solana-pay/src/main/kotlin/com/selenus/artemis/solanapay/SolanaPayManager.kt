package com.selenus.artemis.solanapay

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * SolanaPayManager - Complete Solana Pay implementation
 * 
 * Features:
 * - Transfer request creation and parsing
 * - Transaction request protocol support
 * - Payment verification and tracking
 * - QR code data generation
 * - Multi-token payment support
 * - Payment session management
 */
class SolanaPayManager(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val defaultCluster: Cluster = Cluster.MAINNET,
        val paymentTimeoutMs: Long = 300_000L,  // 5 minutes
        val pollingIntervalMs: Long = 2000L,
        val maxRetries: Int = 3
    )

    enum class Cluster {
        MAINNET,
        DEVNET,
        TESTNET
    }

    /**
     * Transfer request - Simple SOL or SPL token transfer
     */
    data class TransferRequest(
        val recipient: Pubkey,
        val amount: BigDecimal? = null,
        val splToken: Pubkey? = null,
        val reference: List<Pubkey> = emptyList(),
        val label: String? = null,
        val message: String? = null,
        val memo: String? = null
    ) {
        fun toUri(): String = SolanaPayUri.build(
            SolanaPayUri.Request(
                recipient = recipient,
                amount = amount,
                splToken = splToken,
                reference = reference,
                label = label,
                message = message,
                memo = memo
            )
        )

        fun toQRData(): String = toUri()
    }

    /**
     * Transaction request - Server-generated transaction
     */
    data class TransactionRequest(
        val link: String,
        val label: String? = null,
        val message: String? = null
    ) {
        fun toUri(): String {
            val sb = StringBuilder("solana:")
            sb.append(java.net.URLEncoder.encode(link, "UTF-8"))
            val params = mutableListOf<String>()
            label?.let { params.add("label=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            message?.let { params.add("message=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            if (params.isNotEmpty()) {
                sb.append("?")
                sb.append(params.joinToString("&"))
            }
            return sb.toString()
        }
    }

    /**
     * Payment session for tracking payments
     */
    data class PaymentSession(
        val sessionId: String,
        val request: TransferRequest,
        val reference: Pubkey,
        val createdAtMs: Long,
        val status: PaymentStatus,
        val signature: String?,
        val confirmedSlot: Long?,
        val payer: Pubkey?,
        val amountReceived: BigDecimal?
    )

    enum class PaymentStatus {
        PENDING,        // Waiting for payment
        DETECTED,       // Transaction detected on-chain
        CONFIRMED,      // Transaction confirmed
        FINALIZED,      // Transaction finalized
        EXPIRED,        // Payment timeout
        FAILED          // Payment failed
    }

    /**
     * Payment event for reactive UI
     */
    sealed class PaymentEvent {
        data class Created(val session: PaymentSession) : PaymentEvent()
        data class Detected(val sessionId: String, val signature: String) : PaymentEvent()
        data class Confirmed(val sessionId: String, val slot: Long) : PaymentEvent()
        data class Finalized(val sessionId: String) : PaymentEvent()
        data class Expired(val sessionId: String) : PaymentEvent()
        data class Failed(val sessionId: String, val error: String) : PaymentEvent()
    }

    // Session storage
    private val sessions = ConcurrentHashMap<String, PaymentSession>()
    private val referenceToSession = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()

    // Observable streams
    private val _events = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PaymentEvent> = _events.asSharedFlow()

    private val _activePayments = MutableStateFlow<List<PaymentSession>>(emptyList())
    val activePayments: StateFlow<List<PaymentSession>> = _activePayments.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Create a new payment session
     */
    suspend fun createPaymentSession(
        recipient: Pubkey,
        amount: BigDecimal? = null,
        splToken: Pubkey? = null,
        label: String? = null,
        message: String? = null,
        memo: String? = null
    ): PaymentSession = mutex.withLock {
        val sessionId = UUID.randomUUID().toString()
        val reference = Keypair.generate().publicKey  // Unique reference for this payment

        val request = TransferRequest(
            recipient = recipient,
            amount = amount,
            splToken = splToken,
            reference = listOf(reference),
            label = label,
            message = message,
            memo = memo
        )

        val session = PaymentSession(
            sessionId = sessionId,
            request = request,
            reference = reference,
            createdAtMs = System.currentTimeMillis(),
            status = PaymentStatus.PENDING,
            signature = null,
            confirmedSlot = null,
            payer = null,
            amountReceived = null
        )

        sessions[sessionId] = session
        referenceToSession[reference.toString()] = sessionId
        updateActivePayments()

        _events.tryEmit(PaymentEvent.Created(session))

        session
    }

    /**
     * Create a transfer request URI (no session tracking)
     */
    fun createTransferUri(
        recipient: Pubkey,
        amount: BigDecimal? = null,
        splToken: Pubkey? = null,
        label: String? = null,
        message: String? = null,
        memo: String? = null
    ): String {
        return TransferRequest(
            recipient = recipient,
            amount = amount,
            splToken = splToken,
            label = label,
            message = message,
            memo = memo
        ).toUri()
    }

    /**
     * Create a transaction request URI
     */
    fun createTransactionRequestUri(
        apiEndpoint: String,
        label: String? = null,
        message: String? = null
    ): String {
        return TransactionRequest(
            link = apiEndpoint,
            label = label,
            message = message
        ).toUri()
    }

    /**
     * Parse a Solana Pay URI
     */
    fun parseUri(uri: String): ParsedRequest {
        return try {
            val parsed = SolanaPayUri.parse(uri)
            ParsedRequest.Transfer(
                TransferRequest(
                    recipient = parsed.recipient,
                    amount = parsed.amount,
                    splToken = parsed.splToken,
                    reference = parsed.reference,
                    label = parsed.label,
                    message = parsed.message,
                    memo = parsed.memo
                )
            )
        } catch (e: Exception) {
            // Check if it's a transaction request
            if (uri.startsWith("solana:http")) {
                val decoded = java.net.URLDecoder.decode(uri.removePrefix("solana:"), "UTF-8")
                ParsedRequest.TransactionReq(decoded)
            } else {
                ParsedRequest.Invalid(e.message ?: "Invalid URI")
            }
        }
    }

    sealed class ParsedRequest {
        data class Transfer(val request: TransferRequest) : ParsedRequest()
        data class TransactionReq(val endpoint: String) : ParsedRequest()
        data class Invalid(val error: String) : ParsedRequest()
    }

    /**
     * Start polling for payment confirmations
     */
    fun startPolling(verifier: suspend (Pubkey) -> VerificationResult?) {
        pollingJob = scope.launch {
            while (isActive) {
                delay(config.pollingIntervalMs)
                checkPendingPayments(verifier)
            }
        }
    }

    /**
     * Stop polling
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Manual verification check for a session
     */
    suspend fun verifyPayment(
        sessionId: String,
        verifier: suspend (Pubkey) -> VerificationResult?
    ): PaymentSession? = mutex.withLock {
        val session = sessions[sessionId] ?: return@withLock null
        if (session.status != PaymentStatus.PENDING && session.status != PaymentStatus.DETECTED) {
            return@withLock session
        }

        val result = verifier(session.reference)
        if (result != null) {
            val updated = session.copy(
                status = when (result.confirmationStatus) {
                    "finalized" -> PaymentStatus.FINALIZED
                    "confirmed" -> PaymentStatus.CONFIRMED
                    else -> PaymentStatus.DETECTED
                },
                signature = result.signature,
                confirmedSlot = result.slot,
                payer = result.payer,
                amountReceived = result.amount
            )
            sessions[sessionId] = updated
            updateActivePayments()

            when (updated.status) {
                PaymentStatus.DETECTED -> _events.tryEmit(PaymentEvent.Detected(sessionId, result.signature))
                PaymentStatus.CONFIRMED -> _events.tryEmit(PaymentEvent.Confirmed(sessionId, result.slot))
                PaymentStatus.FINALIZED -> _events.tryEmit(PaymentEvent.Finalized(sessionId))
                else -> {}
            }

            return@withLock updated
        }

        // Check expiration
        if (System.currentTimeMillis() - session.createdAtMs > config.paymentTimeoutMs) {
            val expired = session.copy(status = PaymentStatus.EXPIRED)
            sessions[sessionId] = expired
            updateActivePayments()
            _events.tryEmit(PaymentEvent.Expired(sessionId))
            return@withLock expired
        }

        session
    }

    data class VerificationResult(
        val signature: String,
        val slot: Long,
        val confirmationStatus: String,
        val payer: Pubkey?,
        val amount: BigDecimal?
    )

    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): PaymentSession? = sessions[sessionId]

    /**
     * Get session by reference
     */
    fun getSessionByReference(reference: Pubkey): PaymentSession? {
        val sessionId = referenceToSession[reference.toString()] ?: return null
        return sessions[sessionId]
    }

    /**
     * Cancel a payment session
     */
    suspend fun cancelSession(sessionId: String) = mutex.withLock {
        val session = sessions[sessionId] ?: return@withLock
        if (session.status == PaymentStatus.PENDING) {
            val cancelled = session.copy(status = PaymentStatus.FAILED)
            sessions[sessionId] = cancelled
            referenceToSession.remove(session.reference.toString())
            updateActivePayments()
        }
    }

    /**
     * Get all sessions
     */
    fun getAllSessions(): List<PaymentSession> = sessions.values.toList()

    /**
     * Clean up expired sessions
     */
    suspend fun cleanupExpired() = mutex.withLock {
        val now = System.currentTimeMillis()
        val expired = sessions.entries.filter {
            it.value.status == PaymentStatus.PENDING &&
            now - it.value.createdAtMs > config.paymentTimeoutMs
        }

        for ((id, session) in expired) {
            sessions[id] = session.copy(status = PaymentStatus.EXPIRED)
            referenceToSession.remove(session.reference.toString())
            _events.tryEmit(PaymentEvent.Expired(id))
        }
        updateActivePayments()
    }

    private suspend fun checkPendingPayments(verifier: suspend (Pubkey) -> VerificationResult?) {
        val pending = sessions.values.filter {
            it.status == PaymentStatus.PENDING || it.status == PaymentStatus.DETECTED
        }

        for (session in pending) {
            verifyPayment(session.sessionId, verifier)
        }
    }

    private fun updateActivePayments() {
        _activePayments.value = sessions.values.filter {
            it.status == PaymentStatus.PENDING || it.status == PaymentStatus.DETECTED
        }.sortedByDescending { it.createdAtMs }
    }
}

/**
 * SolanaPayQR - QR code data generation utilities
 */
object SolanaPayQR {

    /**
     * Generate QR data for a transfer request
     */
    fun transferQR(
        recipient: Pubkey,
        amount: BigDecimal? = null,
        splToken: Pubkey? = null,
        label: String? = null,
        message: String? = null,
        memo: String? = null
    ): String {
        return SolanaPayUri.build(
            SolanaPayUri.Request(
                recipient = recipient,
                amount = amount,
                splToken = splToken,
                label = label,
                message = message,
                memo = memo
            )
        )
    }

    /**
     * Generate QR data for a transaction request
     */
    fun transactionRequestQR(apiEndpoint: String): String {
        return "solana:${java.net.URLEncoder.encode(apiEndpoint, "UTF-8")}"
    }

    /**
     * Estimate QR code complexity
     */
    fun estimateQRComplexity(data: String): QRComplexity {
        val length = data.length
        return when {
            length < 100 -> QRComplexity.LOW
            length < 300 -> QRComplexity.MEDIUM
            length < 500 -> QRComplexity.HIGH
            else -> QRComplexity.VERY_HIGH
        }
    }

    enum class QRComplexity {
        LOW,        // Simple, fast to scan
        MEDIUM,     // Normal
        HIGH,       // Complex, may need better camera
        VERY_HIGH   // Very complex, may have scanning issues
    }
}

/**
 * Extension for quick payment creation
 */
fun SolanaPayManager.quickPayment(
    recipient: Pubkey,
    amountSol: Double,
    label: String? = null
): SolanaPayManager.TransferRequest {
    return SolanaPayManager.TransferRequest(
        recipient = recipient,
        amount = BigDecimal.valueOf(amountSol),
        label = label
    )
}
