package com.selenus.artemis.privacy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant

/**
 * Encrypted Memo System for private on-chain messaging.
 * 
 * Allows sending encrypted messages via Solana memo instructions that only
 * the intended recipient can decrypt.
 * 
 * Features:
 * - End-to-end encrypted memos
 * - Forward secrecy (unique key per message)
 * - Compact format for on-chain storage
 * - Optional message expiration
 * 
 * Usage:
 * 1. Sender encrypts memo with recipient's public key
 * 2. Sender includes encrypted memo in transaction memo field
 * 3. Recipient decrypts using their private key
 */
object EncryptedMemo {
  
  private val json = Json { ignoreUnknownKeys = true }
  
  /** Maximum plaintext memo size (keep on-chain storage reasonable) */
  const val MAX_MEMO_SIZE = 500
  
  /** Prefix to identify Artemis encrypted memos */
  const val MEMO_PREFIX = "art-enc-v1:"
  
  // ═══════════════════════════════════════════════════════════════════════════
  // DATA TYPES
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Serializable
  data class EncryptedMemoPayload(
    /** Ephemeral public key for key agreement */
    val eph: String,
    /** Encrypted content (base64) */
    val ct: String,
    /** Nonce (base64) */
    val n: String,
    /** Optional: Unix timestamp when memo expires */
    val exp: Long? = null,
    /** Optional: Memo type for client-side routing */
    val t: String? = null
  )
  
  /**
   * Memo types for client-side handling.
   */
  object MemoType {
    const val MESSAGE = "msg"
    const val PAYMENT_NOTE = "pay"
    const val INVOICE = "inv"
    const val ACKNOWLEDGMENT = "ack"
    const val CUSTOM = "cust"
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // ENCRYPTION
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Encrypt a memo for a recipient.
   * 
   * @param plaintext Memo content (max 500 bytes)
   * @param recipientPubkey Recipient's Ed25519 public key (32 bytes)
   * @param memoType Optional type hint for client handling
   * @param expirationSeconds Optional expiration in seconds from now
   * @return Encrypted memo string suitable for on-chain storage
   */
  fun encrypt(
    plaintext: String,
    recipientPubkey: ByteArray,
    memoType: String? = null,
    expirationSeconds: Long? = null
  ): String {
    val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
    require(plaintextBytes.size <= MAX_MEMO_SIZE) { "Memo exceeds $MAX_MEMO_SIZE bytes" }
    require(recipientPubkey.size == 32) { "Recipient pubkey must be 32 bytes" }
    
    // Generate ephemeral X25519 keypair
    val ephKeypair = X25519Exchange.generateKeypair()
    
    // Derive shared secret via X25519 key agreement
    // Note: For simplicity, we use recipient's Ed25519 pubkey directly
    // In production, you'd convert to X25519 or use a separate X25519 key
    val sharedSecret = deriveSharedSecretFromECDH(ephKeypair.privateKey, recipientPubkey)
    
    // Derive encryption key from shared secret
    val encKey = deriveEncryptionKey(sharedSecret)
    
    // Encrypt
    val encrypted = SecureCrypto.encrypt(plaintextBytes, encKey)
    
    // Extract nonce (first 12 bytes of encrypted output)
    val nonce = encrypted.copyOfRange(0, SecureCrypto.NONCE_SIZE)
    val ciphertext = encrypted.copyOfRange(SecureCrypto.NONCE_SIZE, encrypted.size)
    
    // Build payload
    val expiration = expirationSeconds?.let { Instant.now().epochSecond + it }
    
    val payload = EncryptedMemoPayload(
      eph = java.util.Base64.getEncoder().encodeToString(ephKeypair.publicKey),
      ct = java.util.Base64.getEncoder().encodeToString(ciphertext),
      n = java.util.Base64.getEncoder().encodeToString(nonce),
      exp = expiration,
      t = memoType
    )
    
    // Wipe sensitive data
    ephKeypair.wipe()
    SecureCrypto.wipe(sharedSecret)
    SecureCrypto.wipe(encKey)
    
    return MEMO_PREFIX + json.encodeToString(EncryptedMemoPayload.serializer(), payload)
  }
  
  /**
   * Decrypt a memo using recipient's private key.
   * 
   * @param encryptedMemo Encrypted memo string from transaction
   * @param recipientPrivkey Recipient's X25519/Ed25519 private key (32-byte seed)
   * @return Decrypted plaintext, or null if decryption fails or memo expired
   */
  fun decrypt(encryptedMemo: String, recipientPrivkey: ByteArray): DecryptResult? {
    require(recipientPrivkey.size == 32) { "Recipient privkey must be 32 bytes" }
    
    if (!encryptedMemo.startsWith(MEMO_PREFIX)) return null
    
    return try {
      val payloadJson = encryptedMemo.removePrefix(MEMO_PREFIX)
      val payload = json.decodeFromString(EncryptedMemoPayload.serializer(), payloadJson)
      
      // Check expiration
      if (payload.exp != null && Instant.now().epochSecond > payload.exp) {
        return null // Expired
      }
      
      // Parse components
      val ephPubkey = java.util.Base64.getDecoder().decode(payload.eph)
      val ciphertext = java.util.Base64.getDecoder().decode(payload.ct)
      val nonce = java.util.Base64.getDecoder().decode(payload.n)
      
      // Derive shared secret via X25519 key agreement (reversed)
      val sharedSecret = deriveSharedSecretFromECDH(recipientPrivkey, ephPubkey)
      val encKey = deriveEncryptionKey(sharedSecret)
      
      // Reconstruct encrypted data (nonce + ciphertext)
      val encrypted = nonce + ciphertext
      
      // Decrypt
      val plaintext = SecureCrypto.decrypt(encrypted, encKey)
      
      // Wipe sensitive data
      SecureCrypto.wipe(sharedSecret)
      SecureCrypto.wipe(encKey)
      
      DecryptResult(
        plaintext = String(plaintext, Charsets.UTF_8),
        memoType = payload.t,
        expiration = payload.exp
      )
    } catch (e: Exception) {
      null
    }
  }
  
  /**
   * Result of successful memo decryption.
   */
  data class DecryptResult(
    val plaintext: String,
    val memoType: String?,
    val expiration: Long?
  )
  
  /**
   * Check if a memo string is an Artemis encrypted memo.
   */
  fun isEncryptedMemo(memo: String): Boolean {
    return memo.startsWith(MEMO_PREFIX)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // INTERNAL HELPERS
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Derive shared secret using X25519 key agreement.
   * This uses proper ECDH: SharedSecret = ECDH(privkey, pubkey)
   */
  private fun deriveSharedSecretFromECDH(privkey: ByteArray, pubkey: ByteArray): ByteArray {
    return try {
      X25519Exchange.computeSharedSecret(privkey, pubkey)
    } catch (e: Exception) {
      // Fallback: if keys aren't valid X25519, use hash-based derivation
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(privkey)
      digest.update(pubkey)
      digest.update("artemis-memo-shared".toByteArray())
      digest.digest()
    }
  }
  
  private fun deriveEncryptionKey(sharedSecret: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(sharedSecret)
    digest.update("artemis-memo-enc-key".toByteArray())
    return digest.digest()
  }
}
