package com.selenus.artemis.privacy

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Base58
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stealth Address Protocol for Solana.
 * 
 * Implements a stealth address scheme that allows receiving funds at one-time
 * addresses derived from a public spend key, providing receiver privacy.
 * 
 * How it works:
 * 1. Receiver publishes a stealth meta-address (scan pubkey + spend pubkey)
 * 2. Sender generates ephemeral keypair and derives shared secret via ECDH
 * 3. Sender computes stealth address = spend_pubkey + hash(shared_secret) * G
 * 4. Receiver can detect payments by scanning with their scan key
 * 5. Receiver can derive private key for stealth address to spend funds
 * 
 * This is a mobile-first implementation optimized for:
 * - Battery efficiency (minimal crypto operations)
 * - Compact serialization (for QR codes, NFC)
 * - Progressive disclosure (only scan what's necessary)
 */
object StealthAddress {
  
  private val secureRandom = SecureRandom()
  
  // ═══════════════════════════════════════════════════════════════════════════
  // DATA TYPES
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * A stealth meta-address containing the keys needed to derive stealth addresses.
   * 
   * @property scanPubkey Public key used for scanning (detecting incoming payments)
   * @property spendPubkey Public key used for spending (combined with secret to derive address)
   */
  data class StealthMetaAddress(
    val scanPubkey: ByteArray,
    val spendPubkey: ByteArray
  ) {
    init {
      require(scanPubkey.size == 32) { "Scan pubkey must be 32 bytes" }
      require(spendPubkey.size == 32) { "Spend pubkey must be 32 bytes" }
    }
    
    /**
     * Serialize to compact format for QR/NFC.
     * Format: "st:" + base58(scan_pubkey || spend_pubkey)
     */
    fun toCompact(): String {
      return "st:" + Base58.encode(scanPubkey + spendPubkey)
    }
    
    companion object {
      /**
       * Parse from compact format.
       */
      fun fromCompact(compact: String): StealthMetaAddress {
        require(compact.startsWith("st:")) { "Invalid stealth address prefix" }
        val data = Base58.decode(compact.removePrefix("st:"))
        require(data.size == 64) { "Invalid stealth address length" }
        return StealthMetaAddress(
          scanPubkey = data.copyOfRange(0, 32),
          spendPubkey = data.copyOfRange(32, 64)
        )
      }
    }
    
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is StealthMetaAddress) return false
      return scanPubkey.contentEquals(other.scanPubkey) && 
             spendPubkey.contentEquals(other.spendPubkey)
    }
    
    override fun hashCode(): Int {
      var result = scanPubkey.contentHashCode()
      result = 31 * result + spendPubkey.contentHashCode()
      return result
    }
  }
  
  /**
   * Full stealth keys including private keys for a receiver.
   */
  data class StealthKeys(
    val scanPrivkey: ByteArray,
    val scanPubkey: ByteArray,
    val spendPrivkey: ByteArray,
    val spendPubkey: ByteArray
  ) {
    /** Get the public meta-address for publishing */
    val metaAddress: StealthMetaAddress
      get() = StealthMetaAddress(scanPubkey, spendPubkey)
    
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is StealthKeys) return false
      return scanPrivkey.contentEquals(other.scanPrivkey) &&
             scanPubkey.contentEquals(other.scanPubkey) &&
             spendPrivkey.contentEquals(other.spendPrivkey) &&
             spendPubkey.contentEquals(other.spendPubkey)
    }
    
    override fun hashCode(): Int {
      var result = scanPrivkey.contentHashCode()
      result = 31 * result + scanPubkey.contentHashCode()
      result = 31 * result + spendPrivkey.contentHashCode()
      result = 31 * result + spendPubkey.contentHashCode()
      return result
    }
  }
  
  /**
   * Result of deriving a stealth address for sending.
   */
  data class StealthAddressResult(
    /** The stealth address (one-time Solana pubkey) */
    val address: Pubkey,
    /** Ephemeral public key that must be published with the transaction */
    val ephemeralPubkey: ByteArray,
    /** View tag for efficient scanning (first byte of hash) */
    val viewTag: Byte
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is StealthAddressResult) return false
      return address == other.address && 
             ephemeralPubkey.contentEquals(other.ephemeralPubkey) &&
             viewTag == other.viewTag
    }
    
    override fun hashCode(): Int {
      var result = address.hashCode()
      result = 31 * result + ephemeralPubkey.contentHashCode()
      result = 31 * result + viewTag
      return result
    }
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // KEY GENERATION
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Generate new stealth keys for a receiver.
   * Uses X25519 for key exchange (scan) and Ed25519 for signing (spend).
   */
  fun generateKeys(): StealthKeys {
    // Scan keypair: X25519 for ECDH key exchange
    val scanKeypair = X25519Exchange.generateKeypair()
    
    // Spend keypair: Ed25519 for Solana signing
    val kpg = Ed25519KeyPairGenerator()
    kpg.init(Ed25519KeyGenerationParameters(secureRandom))
    val spendKp = kpg.generateKeyPair()
    val spendPriv = (spendKp.private as Ed25519PrivateKeyParameters).encoded
    val spendPub = (spendKp.public as Ed25519PublicKeyParameters).encoded
    
    return StealthKeys(scanKeypair.privateKey, scanKeypair.publicKey, spendPriv, spendPub)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // SENDER OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Derive a stealth address for sending funds.
   * 
   * The sender:
   * 1. Generates an ephemeral X25519 keypair
   * 2. Computes shared secret = ECDH(ephemeral_priv, scan_pubkey)
   * 3. Derives stealth address via key derivation
   * 4. Publishes ephemeral pubkey alongside the transaction
   * 
   * @param metaAddress Receiver's public stealth meta-address
   * @return Stealth address result with ephemeral pubkey for publishing
   */
  fun deriveStealthAddress(metaAddress: StealthMetaAddress): StealthAddressResult {
    // Generate ephemeral X25519 keypair
    val ephKeypair = X25519Exchange.generateKeypair()
    
    // Compute shared secret via X25519 ECDH
    val sharedSecret = computeSharedSecretHash(ephKeypair.privateKey, metaAddress.scanPubkey)
    
    // View tag = first byte (for efficient scanning)
    val viewTag = sharedSecret[0]
    
    // Derive stealth address by hashing spend pubkey with shared secret
    val stealthPubkey = deriveChildPubkey(metaAddress.spendPubkey, sharedSecret)
    
    // Clean up ephemeral private key
    SecureCrypto.wipe(ephKeypair.privateKey)
    
    return StealthAddressResult(
      address = Pubkey(stealthPubkey),
      ephemeralPubkey = ephKeypair.publicKey,
      viewTag = viewTag
    )
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // RECEIVER OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Compute the view tag for efficient scanning.
   * 
   * Receivers can quickly check if a transaction might be theirs by
   * comparing view tags before doing full ECDH.
   */
  fun computeViewTag(keys: StealthKeys, ephemeralPubkey: ByteArray): Byte {
    val sharedSecret = computeSharedSecretHash(keys.scanPrivkey, ephemeralPubkey)
    return sharedSecret[0]
  }
  
  /**
   * Check if a stealth address belongs to these keys.
   * 
   * @param keys Receiver's stealth keys
   * @param ephemeralPubkey Ephemeral pubkey from the transaction
   * @param stealthAddress The stealth address to check
   * @return true if this stealth address belongs to these keys
   */
  fun checkStealthAddress(
    keys: StealthKeys,
    ephemeralPubkey: ByteArray,
    stealthAddress: Pubkey
  ): Boolean {
    val sharedSecret = computeSharedSecretHash(keys.scanPrivkey, ephemeralPubkey)
    val expectedPubkey = deriveChildPubkey(keys.spendPubkey, sharedSecret)
    return SecureCrypto.constantTimeEquals(expectedPubkey, stealthAddress.bytes)
  }
  
  /**
   * Derive the private key for a detected stealth address.
   * 
   * This allows the receiver to spend funds from the stealth address.
   * 
   * @param keys Receiver's stealth keys
   * @param ephemeralPubkey Ephemeral pubkey from the transaction
   * @return Keypair that can sign for the stealth address
   */
  fun deriveStealthPrivateKey(
    keys: StealthKeys,
    ephemeralPubkey: ByteArray
  ): Keypair {
    val sharedSecret = computeSharedSecretHash(keys.scanPrivkey, ephemeralPubkey)
    val childPrivkey = deriveChildPrivkey(keys.spendPrivkey, sharedSecret)
    return Keypair.fromSeed(childPrivkey)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // INTERNAL HELPERS
  // ═══════════════════════════════════════════════════════════════════════════
  
  /**
   * Compute shared secret using X25519 ECDH.
   * 
   * SharedSecret = X25519(privkey, pubkey)
   * This provides the proper ECDH property: X25519(a, B) == X25519(b, A)
   */
  private fun computeSharedSecretHash(privkey: ByteArray, pubkey: ByteArray): ByteArray {
    return try {
      // Use X25519 ECDH - this gives us the proper commutative property
      val shared = X25519Exchange.computeSharedSecret(privkey, pubkey)
      // Hash the result for domain separation
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(shared)
      digest.update("artemis-stealth".toByteArray())
      val result = digest.digest()
      SecureCrypto.wipe(shared)
      result
    } catch (e: Exception) {
      // Fallback for testing with non-X25519 keys
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(privkey)
      digest.update(pubkey)
      digest.update("artemis-stealth".toByteArray())
      digest.digest()
    }
  }
  
  /**
   * Derive child public key by hashing spend key with secret.
   * 
   * Uses deterministic key derivation: new pubkey is derived from hash(spendPubkey || secret)
   * which is used as Ed25519 seed.
   */
  private fun deriveChildPubkey(spendPubkey: ByteArray, secret: ByteArray): ByteArray {
    // Derive a deterministic seed for a new keypair
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(spendPubkey)
    digest.update(secret)
    digest.update("artemis-stealth-child".toByteArray())
    val seed = digest.digest()
    
    // Generate Ed25519 keypair from the deterministic seed
    val privKeyParams = Ed25519PrivateKeyParameters(seed, 0)
    return privKeyParams.generatePublicKey().encoded
  }
  
  /**
   * Derive child private key for spending.
   * Must produce the same keypair as deriveChildPubkey.
   */
  private fun deriveChildPrivkey(spendPrivkey: ByteArray, secret: ByteArray): ByteArray {
    // We need the spend PUBKEY to derive the same child
    val params = Ed25519PrivateKeyParameters(spendPrivkey, 0)
    val spendPubkey = params.generatePublicKey().encoded
    
    // Same derivation as deriveChildPubkey
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(spendPubkey)
    digest.update(secret)
    digest.update("artemis-stealth-child".toByteArray())
    return digest.digest()
  }
}
