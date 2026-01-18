package com.selenus.artemis.privacy

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.security.SecureRandom

/**
 * Artemis Privacy Module - Comprehensive cryptographic utilities for mobile-first privacy.
 * 
 * Features:
 * - AES-256-GCM encryption/decryption
 * - Memory-hard key derivation (scrypt)
 * - Secure random generation
 * - Constant-time comparison
 * - Secure memory wiping
 * 
 * Designed for mobile SDK usage with emphasis on:
 * - Battery efficiency (minimal CPU cycles)
 * - Memory safety (secure wipe after use)
 * - Side-channel resistance (constant-time operations)
 */
object SecureCrypto {
  
  private val secureRandom = SecureRandom()
  
  /** AES-256 key size in bytes */
  const val AES_KEY_SIZE = 32
  
  /** AES-GCM nonce size (96 bits recommended) */
  const val NONCE_SIZE = 12
  
  /** AES-GCM tag size in bits */
  const val TAG_SIZE_BITS = 128
  
  // ═══════════════════════════════════════════════════════════════════════════
  // SYMMETRIC ENCRYPTION (AES-256-GCM)
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Encrypts data using AES-256-GCM.
   * 
   * Output format: [12-byte nonce][ciphertext][16-byte tag]
   * 
   * @param plaintext Data to encrypt
   * @param key 32-byte AES key
   * @param associatedData Optional AAD for authenticated encryption
   * @return Encrypted data with prepended nonce
   */
  fun encrypt(
    plaintext: ByteArray,
    key: ByteArray,
    associatedData: ByteArray = ByteArray(0)
  ): ByteArray {
    require(key.size == AES_KEY_SIZE) { "Key must be $AES_KEY_SIZE bytes" }
    
    // Generate random nonce
    val nonce = ByteArray(NONCE_SIZE)
    secureRandom.nextBytes(nonce)
    
    // Setup GCM cipher
    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
    val params = AEADParameters(KeyParameter(key), TAG_SIZE_BITS, nonce, associatedData)
    cipher.init(true, params)
    
    // Encrypt
    val output = ByteArray(cipher.getOutputSize(plaintext.size))
    val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
    cipher.doFinal(output, len)
    
    // Prepend nonce to output
    return nonce + output
  }
  
  /**
   * Decrypts AES-256-GCM encrypted data.
   * 
   * @param ciphertext Data encrypted by [encrypt] (nonce prepended)
   * @param key 32-byte AES key
   * @param associatedData Optional AAD (must match encryption)
   * @return Decrypted plaintext
   * @throws IllegalArgumentException if ciphertext is invalid
   * @throws SecurityException if authentication fails (tampered data)
   */
  fun decrypt(
    ciphertext: ByteArray,
    key: ByteArray,
    associatedData: ByteArray = ByteArray(0)
  ): ByteArray {
    require(key.size == AES_KEY_SIZE) { "Key must be $AES_KEY_SIZE bytes" }
    require(ciphertext.size > NONCE_SIZE + 16) { "Ciphertext too short" }
    
    // Extract nonce and encrypted data
    val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
    val encrypted = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
    
    // Setup GCM cipher
    val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
    val params = AEADParameters(KeyParameter(key), TAG_SIZE_BITS, nonce, associatedData)
    cipher.init(false, params)
    
    try {
      val output = ByteArray(cipher.getOutputSize(encrypted.size))
      val len = cipher.processBytes(encrypted, 0, encrypted.size, output, 0)
      cipher.doFinal(output, len)
      return output
    } catch (e: Exception) {
      throw SecurityException("Decryption failed: authentication tag mismatch or corrupted data")
    }
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // KEY DERIVATION (scrypt - memory-hard)
  // ═══════════════════════════════════════════════════════════════════════════
  
  /** scrypt parameters - balanced for mobile performance */
  object ScryptParams {
    /** CPU/memory cost (2^14 = 16384) - balanced for mobile */
    const val N = 16384
    /** Block size */
    const val r = 8
    /** Parallelization factor */
    const val p = 1
    /** Output key length */
    const val keyLength = 32
    /** Salt length */
    const val saltLength = 32
  }
  
  /**
   * Derives an encryption key from a password using scrypt.
   * 
   * scrypt is memory-hard, making brute-force attacks expensive on GPUs/ASICs.
   * 
   * @param password User password
   * @param salt Random salt (use [generateSalt] for new keys)
   * @param params Optional custom scrypt parameters
   * @return 32-byte derived key
   */
  fun deriveKey(
    password: ByteArray,
    salt: ByteArray,
    n: Int = ScryptParams.N,
    r: Int = ScryptParams.r,
    p: Int = ScryptParams.p
  ): ByteArray {
    return SCrypt.generate(password, salt, n, r, p, ScryptParams.keyLength)
  }
  
  /**
   * Generates a cryptographically secure random salt for key derivation.
   */
  fun generateSalt(): ByteArray {
    val salt = ByteArray(ScryptParams.saltLength)
    secureRandom.nextBytes(salt)
    return salt
  }
  
  /**
   * Generates a random AES-256 key.
   */
  fun generateKey(): ByteArray {
    val key = ByteArray(AES_KEY_SIZE)
    secureRandom.nextBytes(key)
    return key
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // SECURITY UTILITIES
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Constant-time comparison to prevent timing attacks.
   * 
   * Always takes the same amount of time regardless of where mismatches occur.
   */
  fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
      result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
  }
  
  /**
   * Securely wipes sensitive data from memory.
   * 
   * Overwrites with zeros to prevent sensitive data from lingering in RAM.
   * Note: Not 100% guaranteed due to JVM memory management, but best effort.
   */
  fun wipe(data: ByteArray) {
    data.fill(0)
  }
  
  /**
   * Generates cryptographically secure random bytes.
   */
  fun randomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    secureRandom.nextBytes(bytes)
    return bytes
  }
  
  /**
   * Keccak-256 hash (used by Ethereum, some Solana applications).
   */
  fun keccak256(data: ByteArray): ByteArray {
    val digest = Keccak.Digest256()
    digest.update(data)
    return digest.digest()
  }
}
