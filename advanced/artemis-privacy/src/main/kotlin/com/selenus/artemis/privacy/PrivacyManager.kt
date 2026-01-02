package com.selenus.artemis.privacy

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Keypair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-based Privacy Manager for mobile applications.
 * 
 * Provides a high-level interface for privacy features with:
 * - Ephemeral session keys (auto-rotated)
 * - Secure key storage abstraction
 * - Automatic cleanup on session end
 * - Mobile-optimized memory management
 * 
 * Usage:
 * ```kotlin
 * val privacy = PrivacyManager()
 * 
 * // Generate stealth keys for receiving private payments
 * val stealthKeys = privacy.generateStealthKeys("my-wallet")
 * 
 * // Encrypt a memo
 * val encrypted = privacy.encryptMemo("Hello!", recipientPubkey)
 * 
 * // End session and wipe all keys
 * privacy.endSession()
 * ```
 */
class PrivacyManager(
  private val keyStorage: KeyStorage = InMemoryKeyStorage()
) {
  
  private val mutex = Mutex()
  private var sessionActive = false
  private val sessionKeys = mutableListOf<ByteArray>()
  
  // ═══════════════════════════════════════════════════════════════════════════
  // SESSION MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Start a new privacy session.
   * 
   * Generates session-specific keys and initializes storage.
   */
  suspend fun startSession() {
    mutex.withLock {
      if (sessionActive) return
      sessionActive = true
    }
  }
  
  /**
   * End the current session and securely wipe all keys.
   */
  suspend fun endSession() {
    mutex.withLock {
      if (!sessionActive) return
      
      // Wipe all session keys
      sessionKeys.forEach { SecureCrypto.wipe(it) }
      sessionKeys.clear()
      
      // Clear storage
      keyStorage.clear()
      
      sessionActive = false
    }
  }
  
  /**
   * Check if a session is currently active.
   */
  fun isSessionActive(): Boolean = sessionActive
  
  // ═══════════════════════════════════════════════════════════════════════════
  // STEALTH ADDRESSES
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Generate and store stealth keys for a wallet identifier.
   * 
   * @param walletId Unique identifier for this wallet
   * @return The public stealth meta-address for sharing
   */
  suspend fun generateStealthKeys(walletId: String): StealthAddress.StealthMetaAddress {
    mutex.withLock {
      val keys = StealthAddress.generateKeys()
      
      // Store keys securely
      keyStorage.store("stealth:$walletId:scan_priv", keys.scanPrivkey)
      keyStorage.store("stealth:$walletId:scan_pub", keys.scanPubkey)
      keyStorage.store("stealth:$walletId:spend_priv", keys.spendPrivkey)
      keyStorage.store("stealth:$walletId:spend_pub", keys.spendPubkey)
      
      // Track for cleanup
      sessionKeys.add(keys.scanPrivkey)
      sessionKeys.add(keys.spendPrivkey)
      
      return keys.metaAddress
    }
  }
  
  /**
   * Get the stealth meta-address for a wallet.
   */
  suspend fun getStealthMetaAddress(walletId: String): StealthAddress.StealthMetaAddress? {
    val scanPub = keyStorage.retrieve("stealth:$walletId:scan_pub") ?: return null
    val spendPub = keyStorage.retrieve("stealth:$walletId:spend_pub") ?: return null
    return StealthAddress.StealthMetaAddress(scanPub, spendPub)
  }
  
  /**
   * Derive a stealth address for sending to a recipient.
   */
  fun deriveStealthAddressForSending(
    recipientMetaAddress: StealthAddress.StealthMetaAddress
  ): StealthAddress.StealthAddressResult {
    return StealthAddress.deriveStealthAddress(recipientMetaAddress)
  }
  
  /**
   * Check if a stealth address belongs to one of our wallets.
   * 
   * @param walletId Wallet to check
   * @param ephemeralPubkey Ephemeral pubkey from transaction
   * @param stealthAddress Address to check
   */
  suspend fun checkIncomingStealthPayment(
    walletId: String,
    ephemeralPubkey: ByteArray,
    stealthAddress: Pubkey
  ): Boolean {
    val keys = loadStealthKeys(walletId) ?: return false
    return StealthAddress.checkStealthAddress(keys, ephemeralPubkey, stealthAddress)
  }
  
  /**
   * Derive the keypair to spend from a stealth address.
   */
  suspend fun deriveStealthSpendingKey(
    walletId: String,
    ephemeralPubkey: ByteArray
  ): Keypair? {
    val keys = loadStealthKeys(walletId) ?: return null
    return StealthAddress.deriveStealthPrivateKey(keys, ephemeralPubkey)
  }
  
  private suspend fun loadStealthKeys(walletId: String): StealthAddress.StealthKeys? {
    val scanPriv = keyStorage.retrieve("stealth:$walletId:scan_priv") ?: return null
    val scanPub = keyStorage.retrieve("stealth:$walletId:scan_pub") ?: return null
    val spendPriv = keyStorage.retrieve("stealth:$walletId:spend_priv") ?: return null
    val spendPub = keyStorage.retrieve("stealth:$walletId:spend_pub") ?: return null
    return StealthAddress.StealthKeys(scanPriv, scanPub, spendPriv, spendPub)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // ENCRYPTED MEMOS
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Encrypt a memo for a recipient.
   */
  fun encryptMemo(
    message: String,
    recipientPubkey: ByteArray,
    memoType: String? = null,
    expirationSeconds: Long? = null
  ): String {
    return EncryptedMemo.encrypt(message, recipientPubkey, memoType, expirationSeconds)
  }
  
  /**
   * Decrypt a memo using a wallet's private key.
   */
  suspend fun decryptMemo(
    encryptedMemo: String,
    walletId: String
  ): EncryptedMemo.DecryptResult? {
    val spendPriv = keyStorage.retrieve("stealth:$walletId:spend_priv") ?: return null
    return EncryptedMemo.decrypt(encryptedMemo, spendPriv)
  }
  
  /**
   * Decrypt a memo with an explicit private key.
   */
  fun decryptMemo(
    encryptedMemo: String,
    privateKey: ByteArray
  ): EncryptedMemo.DecryptResult? {
    return EncryptedMemo.decrypt(encryptedMemo, privateKey)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // DATA ENCRYPTION (for local storage)
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Encrypt arbitrary data with a password.
   * 
   * Suitable for encrypting wallet backups, local data, etc.
   */
  fun encryptWithPassword(data: ByteArray, password: String): EncryptedData {
    val salt = SecureCrypto.generateSalt()
    val key = SecureCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), salt)
    val encrypted = SecureCrypto.encrypt(data, key)
    SecureCrypto.wipe(key)
    return EncryptedData(encrypted, salt)
  }
  
  /**
   * Decrypt data encrypted with [encryptWithPassword].
   */
  fun decryptWithPassword(encrypted: EncryptedData, password: String): ByteArray? {
    return try {
      val key = SecureCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), encrypted.salt)
      val decrypted = SecureCrypto.decrypt(encrypted.ciphertext, key)
      SecureCrypto.wipe(key)
      decrypted
    } catch (e: Exception) {
      null
    }
  }
  
  /**
   * Encrypted data container.
   */
  data class EncryptedData(
    val ciphertext: ByteArray,
    val salt: ByteArray
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is EncryptedData) return false
      return ciphertext.contentEquals(other.ciphertext) && salt.contentEquals(other.salt)
    }
    
    override fun hashCode(): Int {
      var result = ciphertext.contentHashCode()
      result = 31 * result + salt.contentHashCode()
      return result
    }
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // KEY STORAGE INTERFACE
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Interface for secure key storage.
   * 
   * Implement this for platform-specific secure storage:
   * - Android: EncryptedSharedPreferences / Keystore
   * - iOS: Keychain
   * - Desktop: OS keyring
   */
  interface KeyStorage {
    suspend fun store(key: String, value: ByteArray)
    suspend fun retrieve(key: String): ByteArray?
    suspend fun delete(key: String)
    suspend fun clear()
  }
  
  /**
   * In-memory key storage (for testing and short-lived sessions).
   * 
   * WARNING: Keys are not persisted. Use platform-specific secure storage
   * for production applications.
   */
  class InMemoryKeyStorage : KeyStorage {
    private val store = ConcurrentHashMap<String, ByteArray>()
    
    override suspend fun store(key: String, value: ByteArray) {
      store[key] = value.copyOf()
    }
    
    override suspend fun retrieve(key: String): ByteArray? {
      return store[key]?.copyOf()
    }
    
    override suspend fun delete(key: String) {
      store.remove(key)?.let { SecureCrypto.wipe(it) }
    }
    
    override suspend fun clear() {
      store.values.forEach { SecureCrypto.wipe(it) }
      store.clear()
    }
  }
}
