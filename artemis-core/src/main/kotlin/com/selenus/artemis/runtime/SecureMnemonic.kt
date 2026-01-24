package com.selenus.artemis.runtime

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Artemis Secure Mnemonic Handling
 * 
 * Provides secure storage and handling of mnemonic phrases with:
 * - Memory protection (zeroization)
 * - Encryption at rest
 * - Secure comparison (timing-safe)
 * - Auto-expiry
 * 
 * This goes beyond the basic Solana Mobile SDK which lacks:
 * - Mnemonic encryption helpers
 * - Secure memory handling
 * - Entropy quality validation
 * - Passphrase strength estimation
 */
object SecureMnemonic {
    
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val KEY_SIZE_BITS = 256
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100_000
    
    /**
     * A secure wrapper for mnemonic phrases that provides:
     * - Automatic zeroization on close
     * - One-time access pattern
     * - Time-bound expiry
     */
    class SecurePhraseHandle private constructor(
        private val encryptedData: ByteArray,
        private val nonce: ByteArray,
        private val key: SecretKey,
        private val expiryTimeMs: Long
    ) : AutoCloseable {
        
        private val consumed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expiryTimeMs
        
        val isConsumed: Boolean
            get() = consumed.get()
        
        val isValid: Boolean
            get() = !closed.get() && !isExpired && !isConsumed
        
        /**
         * Decrypt and return the mnemonic phrase.
         * Can only be called once - after this the handle is consumed.
         */
        fun consume(): String {
            check(!closed.get()) { "Handle has been closed" }
            check(!isExpired) { "Handle has expired" }
            check(consumed.compareAndSet(false, true)) { "Handle already consumed" }
            
            return decrypt()
        }
        
        /**
         * Peek at the mnemonic without consuming (for validation).
         * Use sparingly as it keeps the plaintext accessible.
         */
        fun peek(): String {
            check(!closed.get()) { "Handle has been closed" }
            check(!isExpired) { "Handle has expired" }
            return decrypt()
        }
        
        private fun decrypt(): String {
            val cipher = Cipher.getInstance(AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decrypted = cipher.doFinal(encryptedData)
            val result = String(decrypted, Charsets.UTF_8)
            decrypted.fill(0) // Zeroize decrypted bytes
            return result
        }
        
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                // Zeroize sensitive data
                encryptedData.fill(0)
                nonce.fill(0)
            }
        }
        
        companion object {
            /**
             * Create a secure handle for a mnemonic phrase.
             * 
             * @param phrase The mnemonic phrase to protect
             * @param password Password for encrypting the phrase
             * @param ttlSeconds Time-to-live in seconds (default 5 minutes)
             */
            fun create(
                phrase: String,
                password: CharArray,
                ttlSeconds: Long = 300
            ): SecurePhraseHandle {
                val random = SecureRandom()
                
                // Derive encryption key from password
                val salt = ByteArray(16).also { random.nextBytes(it) }
                val keySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
                val key = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")
                
                // Encrypt the phrase
                val nonce = ByteArray(GCM_NONCE_LENGTH).also { random.nextBytes(it) }
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
                
                val phraseBytes = phrase.toByteArray(Charsets.UTF_8)
                val encrypted = cipher.doFinal(phraseBytes)
                phraseBytes.fill(0) // Zeroize plaintext
                
                // Clear password from memory
                password.fill('\u0000')
                keySpec.clearPassword()
                
                return SecurePhraseHandle(
                    encryptedData = encrypted,
                    nonce = nonce,
                    key = key,
                    expiryTimeMs = System.currentTimeMillis() + (ttlSeconds * 1000)
                )
            }
            
            /**
             * Create a secure handle without password (uses random key).
             * Useful for short-lived operations.
             */
            fun createEphemeral(phrase: String, ttlSeconds: Long = 60): SecurePhraseHandle {
                val random = SecureRandom()
                val keyBytes = ByteArray(32).also { random.nextBytes(it) }
                val key = SecretKeySpec(keyBytes, "AES")
                keyBytes.fill(0)
                
                val nonce = ByteArray(GCM_NONCE_LENGTH).also { random.nextBytes(it) }
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
                
                val phraseBytes = phrase.toByteArray(Charsets.UTF_8)
                val encrypted = cipher.doFinal(phraseBytes)
                phraseBytes.fill(0)
                
                return SecurePhraseHandle(
                    encryptedData = encrypted,
                    nonce = nonce,
                    key = key,
                    expiryTimeMs = System.currentTimeMillis() + (ttlSeconds * 1000)
                )
            }
        }
    }
    
    /**
     * Entropy quality assessment for mnemonic generation.
     */
    enum class EntropyQuality {
        /** System SecureRandom with hardware backing */
        EXCELLENT,
        /** System SecureRandom without hardware */
        GOOD,
        /** User-provided entropy that passed validation */
        ACCEPTABLE,
        /** Insufficient randomness detected */
        POOR
    }
    
    /**
     * Estimates the quality of the system's entropy source.
     */
    fun assessEntropyQuality(): EntropyQuality {
        return try {
            val random = SecureRandom.getInstanceStrong()
            val sample = ByteArray(32)
            random.nextBytes(sample)
            
            // Simple entropy estimation - check for obviously poor randomness
            val uniqueBytes = sample.toSet().size
            when {
                uniqueBytes >= 28 -> EntropyQuality.EXCELLENT
                uniqueBytes >= 20 -> EntropyQuality.GOOD
                uniqueBytes >= 12 -> EntropyQuality.ACCEPTABLE
                else -> EntropyQuality.POOR
            }
        } catch (e: Exception) {
            EntropyQuality.GOOD // Fallback assumption
        }
    }
    
    /**
     * Passphrase strength estimation.
     */
    enum class PassphraseStrength {
        VERY_WEAK,
        WEAK,
        MODERATE,
        STRONG,
        VERY_STRONG
    }
    
    /**
     * Estimates the strength of a BIP-39 passphrase.
     */
    fun estimatePassphraseStrength(passphrase: String): PassphraseStrength {
        if (passphrase.isEmpty()) return PassphraseStrength.VERY_WEAK
        
        var score = 0
        
        // Length scoring
        score += when {
            passphrase.length >= 20 -> 4
            passphrase.length >= 14 -> 3
            passphrase.length >= 10 -> 2
            passphrase.length >= 6 -> 1
            else -> 0
        }
        
        // Character class diversity
        if (passphrase.any { it.isUpperCase() }) score += 1
        if (passphrase.any { it.isLowerCase() }) score += 1
        if (passphrase.any { it.isDigit() }) score += 1
        if (passphrase.any { !it.isLetterOrDigit() }) score += 2
        
        // Penalize common patterns
        val lowerPass = passphrase.lowercase()
        if (COMMON_PASSWORDS.any { lowerPass.contains(it) }) score -= 3
        if (lowerPass.matches(Regex("^[a-z]+$"))) score -= 1
        if (lowerPass.matches(Regex("^[0-9]+$"))) score -= 2
        
        return when {
            score >= 8 -> PassphraseStrength.VERY_STRONG
            score >= 6 -> PassphraseStrength.STRONG
            score >= 4 -> PassphraseStrength.MODERATE
            score >= 2 -> PassphraseStrength.WEAK
            else -> PassphraseStrength.VERY_WEAK
        }
    }
    
    private val COMMON_PASSWORDS = setOf(
        "password", "123456", "qwerty", "bitcoin", "solana",
        "crypto", "wallet", "seed", "phrase", "secret"
    )
    
    /**
     * Timing-safe comparison for mnemonic phrases.
     * Prevents timing attacks when verifying backup phrases.
     */
    fun secureEquals(phrase1: String, phrase2: String): Boolean {
        val bytes1 = phrase1.toByteArray(Charsets.UTF_8)
        val bytes2 = phrase2.toByteArray(Charsets.UTF_8)
        
        var result = bytes1.size xor bytes2.size
        val minLen = minOf(bytes1.size, bytes2.size)
        
        for (i in 0 until minLen) {
            result = result or (bytes1[i].toInt() xor bytes2[i].toInt())
        }
        
        bytes1.fill(0)
        bytes2.fill(0)
        
        return result == 0
    }
    
    /**
     * Secure mnemonic verification workflow.
     * Returns true if the user correctly verified their backup.
     * 
     * @param originalPhrase The original phrase to verify against
     * @param verifyWordIndices Which word indices to verify (0-based)
     * @param providedWords The words provided by the user at those indices
     */
    fun verifyBackup(
        originalPhrase: String,
        verifyWordIndices: List<Int>,
        providedWords: List<String>
    ): Boolean {
        require(verifyWordIndices.size == providedWords.size) {
            "Index and word lists must have same size"
        }
        
        val words = originalPhrase.split(" ")
        
        var allMatch = true
        for ((idx, expectedIndex) in verifyWordIndices.withIndex()) {
            if (expectedIndex < 0 || expectedIndex >= words.size) {
                allMatch = false
                continue
            }
            if (!secureEquals(words[expectedIndex], providedWords[idx])) {
                allMatch = false
            }
        }
        
        return allMatch
    }
    
    /**
     * Generates random word indices for backup verification.
     * 
     * @param wordCount Total words in the phrase (12, 15, 18, 21, 24)
     * @param verifyCount How many words to verify
     */
    fun generateVerificationIndices(wordCount: Int, verifyCount: Int = 3): List<Int> {
        require(verifyCount in 1..wordCount) {
            "Verify count must be between 1 and $wordCount"
        }
        
        val random = SecureRandom()
        return (0 until wordCount)
            .shuffled(random)
            .take(verifyCount)
            .sorted()
    }
}
