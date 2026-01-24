package com.selenus.artemis.wallet

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Artemis Session & Authorization Management
 * 
 * Provides MWA-compatible session management with enhancements:
 * - Multi-chain authorization support
 * - Session cloning for cross-device use
 * - Automatic reauthorization handling
 * - Token encryption and validation
 * - Scope-based permission management
 * 
 * Compatible with Solana Mobile MWA but adds:
 * - Session persistence helpers
 * - Cross-chain support
 * - Enhanced security features
 * - Automatic token refresh
 */
object SessionManager {
    
    private val sessions = ConcurrentHashMap<String, AuthSession>()
    private val sessionLock = ReentrantLock()
    
    // Session secret for HMAC validation - rotated per app instance
    private val sessionSecret = ByteArray(32).also { SecureRandom().nextBytes(it) }
    
    /**
     * Supported blockchain chains.
     */
    enum class Chain(val identifier: String) {
        SOLANA_MAINNET("solana:mainnet"),
        SOLANA_DEVNET("solana:devnet"),
        SOLANA_TESTNET("solana:testnet"),
        SOLANA_LOCALNET("solana:localnet");
        
        companion object {
            fun fromIdentifier(id: String): Chain? = entries.find { it.identifier == id }
            
            /** Legacy cluster name conversion */
            fun fromCluster(cluster: String): Chain = when (cluster) {
                "mainnet-beta" -> SOLANA_MAINNET
                "devnet" -> SOLANA_DEVNET
                "testnet" -> SOLANA_TESTNET
                else -> SOLANA_MAINNET
            }
        }
    }
    
    /**
     * Authorization scope defines what operations are permitted.
     */
    data class AuthScope(
        val canSignTransactions: Boolean = true,
        val canSignMessages: Boolean = true,
        val canSignAndSendTransactions: Boolean = true,
        val maxTransactionsPerRequest: Int = 10,
        val maxMessagesPerRequest: Int = 10,
        val allowedPrograms: Set<String>? = null, // null = all programs allowed
        val customPermissions: Map<String, Boolean> = emptyMap()
    ) {
        companion object {
            val Full = AuthScope()
            val SignOnly = AuthScope(canSignAndSendTransactions = false)
            val ReadOnly = AuthScope(
                canSignTransactions = false,
                canSignMessages = false,
                canSignAndSendTransactions = false
            )
        }
        
        fun toBytes(): ByteArray {
            val builder = StringBuilder()
            builder.append("tx:$canSignTransactions,")
            builder.append("msg:$canSignMessages,")
            builder.append("send:$canSignAndSendTransactions,")
            builder.append("maxTx:$maxTransactionsPerRequest,")
            builder.append("maxMsg:$maxMessagesPerRequest")
            return builder.toString().toByteArray(Charsets.UTF_8)
        }
    }
    
    /**
     * Authorized account information.
     */
    data class AuthorizedAccount(
        val address: String,
        val publicKeyBytes: ByteArray,
        val label: String? = null,
        val displayAddress: String? = null,
        val chains: List<Chain> = listOf(Chain.SOLANA_MAINNET)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthorizedAccount) return false
            return address == other.address
        }
        
        override fun hashCode(): Int = address.hashCode()
    }
    
    /**
     * Represents an authorized session with a wallet.
     */
    data class AuthSession(
        val authToken: String,
        val accounts: List<AuthorizedAccount>,
        val chain: Chain,
        val scope: AuthScope,
        val walletUriBase: String? = null,
        val issuedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + DEFAULT_AUTH_VALIDITY_MS,
        val reauthorizeExpiresAt: Long = System.currentTimeMillis() + DEFAULT_REAUTH_VALIDITY_MS,
        val features: Set<String> = emptySet()
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expiresAt
        
        val canReauthorize: Boolean
            get() = System.currentTimeMillis() <= reauthorizeExpiresAt
        
        val primaryAccount: AuthorizedAccount?
            get() = accounts.firstOrNull()
        
        /** Check if a feature is supported */
        fun hasFeature(feature: String): Boolean = features.contains(feature)
        
        companion object {
            const val DEFAULT_AUTH_VALIDITY_MS = 60L * 60L * 1000L // 1 hour
            const val DEFAULT_REAUTH_VALIDITY_MS = 30L * 24L * 60L * 60L * 1000L // 30 days
        }
    }
    
    /**
     * App identity for authorization requests.
     */
    data class AppIdentity(
        val name: String,
        val uri: String? = null,
        val iconUri: String? = null
    )
    
    /**
     * Result of an authorization request.
     */
    data class AuthResult(
        val session: AuthSession,
        val isNewAuthorization: Boolean,
        val signInResult: SignInResult? = null
    )
    
    /**
     * Sign-In with Solana result.
     */
    data class SignInResult(
        val address: String,
        val signedMessage: ByteArray,
        val signature: ByteArray,
        val signatureType: String = "ed25519"
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignInResult) return false
            return address == other.address && signedMessage.contentEquals(other.signedMessage)
        }
        
        override fun hashCode(): Int = address.hashCode()
    }
    
    // ========================================================================
    // Session Management
    // ========================================================================
    
    /**
     * Stores an authorized session.
     */
    fun storeSession(session: AuthSession) = sessionLock.withLock {
        sessions[session.authToken] = session
    }
    
    /**
     * Retrieves a session by auth token.
     */
    fun getSession(authToken: String): AuthSession? {
        return sessions[authToken]?.takeIf { !it.isExpired }
    }
    
    /**
     * Validates and returns a session if valid.
     */
    fun validateSession(authToken: String): AuthSession? {
        val session = sessions[authToken] ?: return null
        if (session.isExpired) {
            sessions.remove(authToken)
            return null
        }
        return session
    }
    
    /**
     * Revokes a session (deauthorize).
     */
    fun revokeSession(authToken: String) = sessionLock.withLock {
        sessions.remove(authToken)
    }
    
    /**
     * Reissues a session with a new token and extended expiry.
     */
    fun reauthorize(currentToken: String): AuthSession? = sessionLock.withLock {
        val current = sessions[currentToken] ?: return@withLock null
        if (!current.canReauthorize) return@withLock null
        
        val newToken = generateAuthToken()
        val reissued = current.copy(
            authToken = newToken,
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + AuthSession.DEFAULT_AUTH_VALIDITY_MS
        )
        
        sessions.remove(currentToken)
        sessions[newToken] = reissued
        reissued
    }
    
    /**
     * Clones a session for cross-device sharing.
     * The cloned token has reduced capabilities for security.
     */
    fun cloneAuthorization(currentToken: String): String? = sessionLock.withLock {
        val current = sessions[currentToken] ?: return@withLock null
        if (current.isExpired) return@withLock null
        
        val clonedToken = generateAuthToken()
        val clonedSession = current.copy(
            authToken = clonedToken,
            issuedAt = System.currentTimeMillis(),
            // Cloned sessions have shorter validity
            expiresAt = System.currentTimeMillis() + (AuthSession.DEFAULT_AUTH_VALIDITY_MS / 2),
            // Cannot be reauthorized
            reauthorizeExpiresAt = System.currentTimeMillis()
        )
        
        sessions[clonedToken] = clonedSession
        clonedToken
    }
    
    /**
     * Gets all active sessions for a specific chain.
     */
    fun getSessionsForChain(chain: Chain): List<AuthSession> {
        return sessions.values.filter { it.chain == chain && !it.isExpired }
    }
    
    /**
     * Clears all expired sessions.
     */
    fun cleanupExpiredSessions() = sessionLock.withLock {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expiresAt < now }
    }
    
    // ========================================================================
    // Token Generation & Validation
    // ========================================================================
    
    /**
     * Generates a secure auth token with HMAC validation.
     */
    fun generateAuthToken(): String {
        val random = SecureRandom()
        val tokenBytes = ByteArray(32)
        random.nextBytes(tokenBytes)
        
        val timestamp = System.currentTimeMillis().toString()
        val payload = tokenBytes + timestamp.toByteArray()
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionSecret, "HmacSHA256"))
        val hmac = mac.doFinal(payload)
        
        // Token format: base64(payload) + "." + base64(hmac)
        val payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        val hmacB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hmac.take(16).toByteArray())
        
        return "$payloadB64.$hmacB64"
    }
    
    /**
     * Validates an auth token's integrity.
     */
    fun isValidTokenFormat(authToken: String): Boolean {
        val parts = authToken.split(".")
        if (parts.size != 2) return false
        
        return try {
            val payload = java.util.Base64.getUrlDecoder().decode(parts[0])
            val providedHmac = java.util.Base64.getUrlDecoder().decode(parts[1])
            
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(sessionSecret, "HmacSHA256"))
            val expectedHmac = mac.doFinal(payload).take(16).toByteArray()
            
            providedHmac.contentEquals(expectedHmac)
        } catch (e: Exception) {
            false
        }
    }
    
    // ========================================================================
    // Wallet Capabilities
    // ========================================================================
    
    /**
     * Standard wallet capability features.
     */
    object Features {
        const val SIGN_TRANSACTIONS = "solana:signTransactions"
        const val SIGN_MESSAGES = "solana:signMessages"
        const val SIGN_AND_SEND_TRANSACTIONS = "solana:signAndSendTransactions"
        const val CLONE_AUTHORIZATION = "clone_authorization"
        const val SIGN_IN = "solana:signIn"
    }
    
    /**
     * Wallet capabilities from get_capabilities response.
     */
    data class WalletCapabilities(
        val maxTransactionsPerRequest: Int = 10,
        val maxMessagesPerRequest: Int = 10,
        val supportedTransactionVersions: List<String> = listOf("legacy", "0"),
        val features: Set<String> = emptySet(),
        @Deprecated("Use features instead")
        val supportsCloneAuthorization: Boolean = false,
        @Deprecated("Use features instead") 
        val supportsSignAndSendTransactions: Boolean = true
    ) {
        fun hasFeature(feature: String): Boolean = features.contains(feature)
    }
}
