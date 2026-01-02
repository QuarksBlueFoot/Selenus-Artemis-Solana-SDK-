package com.selenus.artemis.privacy

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class PrivacyModuleTest {
  
  private lateinit var privacyManager: PrivacyManager
  
  @BeforeEach
  fun setup() {
    privacyManager = PrivacyManager()
  }
  
  @AfterEach
  fun teardown() {
    runBlocking {
      privacyManager.endSession()
    }
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // SecureCrypto Tests
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Test
  fun `AES-256-GCM encrypt and decrypt roundtrip`() {
    val plaintext = "Hello, Solana Privacy!".toByteArray(Charsets.UTF_8)
    val key = SecureCrypto.generateKey()
    
    val ciphertext = SecureCrypto.encrypt(plaintext, key)
    val decrypted = SecureCrypto.decrypt(ciphertext, key)
    
    assertNotNull(decrypted)
    assertArrayEquals(plaintext, decrypted)
    
    // Ciphertext should be larger (nonce + tag + encrypted data)
    assertTrue(ciphertext.size > plaintext.size)
  }
  
  @Test
  fun `AES decryption fails with wrong key`() {
    val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
    val key1 = SecureCrypto.generateKey()
    val key2 = SecureCrypto.generateKey()
    
    val ciphertext = SecureCrypto.encrypt(plaintext, key1)
    
    // Should throw SecurityException with wrong key
    assertThrows(SecurityException::class.java) {
      SecureCrypto.decrypt(ciphertext, key2)
    }
  }
  
  @Test
  fun `scrypt key derivation is deterministic`() {
    val password = "test-password".toByteArray(Charsets.UTF_8)
    val salt = SecureCrypto.generateSalt()
    
    val key1 = SecureCrypto.deriveKey(password, salt)
    val key2 = SecureCrypto.deriveKey(password, salt)
    
    assertArrayEquals(key1, key2)
    assertEquals(32, key1.size)
  }
  
  @Test
  fun `scrypt produces different keys with different salts`() {
    val password = "test-password".toByteArray(Charsets.UTF_8)
    val salt1 = SecureCrypto.generateSalt()
    val salt2 = SecureCrypto.generateSalt()
    
    val key1 = SecureCrypto.deriveKey(password, salt1)
    val key2 = SecureCrypto.deriveKey(password, salt2)
    
    assertFalse(key1.contentEquals(key2))
  }
  
  @Test
  fun `wipe clears byte array`() {
    val sensitive = "secret".toByteArray(Charsets.UTF_8)
    val original = sensitive.copyOf()
    
    SecureCrypto.wipe(sensitive)
    
    // All bytes should be zero
    assertTrue(sensitive.all { it == 0.toByte() })
    assertFalse(sensitive.contentEquals(original))
  }
  
  @Test
  fun `constantTimeEquals works correctly`() {
    val a = byteArrayOf(1, 2, 3, 4, 5)
    val b = byteArrayOf(1, 2, 3, 4, 5)
    val c = byteArrayOf(1, 2, 3, 4, 6)
    val d = byteArrayOf(1, 2, 3)
    
    assertTrue(SecureCrypto.constantTimeEquals(a, b))
    assertFalse(SecureCrypto.constantTimeEquals(a, c))
    assertFalse(SecureCrypto.constantTimeEquals(a, d))
  }
  
  @Test
  fun `keccak256 produces correct hash`() {
    val input = "test".toByteArray(Charsets.UTF_8)
    val hash = SecureCrypto.keccak256(input)
    
    assertEquals(32, hash.size)
    
    // Same input should produce same hash
    val hash2 = SecureCrypto.keccak256(input)
    assertArrayEquals(hash, hash2)
    
    // Different input should produce different hash
    val hash3 = SecureCrypto.keccak256("test2".toByteArray(Charsets.UTF_8))
    assertFalse(hash.contentEquals(hash3))
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // Stealth Address Tests
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Test
  fun `stealth keys generation produces valid keys`() {
    val keys = StealthAddress.generateKeys()
    
    assertEquals(32, keys.scanPrivkey.size)
    assertEquals(32, keys.scanPubkey.size)
    assertEquals(32, keys.spendPrivkey.size)
    assertEquals(32, keys.spendPubkey.size)
    
    // Meta-address should serialize properly
    val metaAddress = keys.metaAddress.toCompact()
    assertTrue(metaAddress.startsWith("st:"))
  }
  
  @Test
  fun `stealth meta-address roundtrip serialization`() {
    val keys = StealthAddress.generateKeys()
    val compact = keys.metaAddress.toCompact()
    val restored = StealthAddress.StealthMetaAddress.fromCompact(compact)
    
    assertNotNull(restored)
    assertArrayEquals(keys.metaAddress.scanPubkey, restored.scanPubkey)
    assertArrayEquals(keys.metaAddress.spendPubkey, restored.spendPubkey)
  }
  
  @Test
  fun `stealth address derivation and verification`() {
    // Recipient generates keys
    val recipientKeys = StealthAddress.generateKeys()
    
    // Sender derives stealth address
    val result = StealthAddress.deriveStealthAddress(recipientKeys.metaAddress)
    
    // Result should have valid components
    assertEquals(32, result.ephemeralPubkey.size)
    assertNotNull(result.address)
    
    // Recipient should be able to detect the payment
    val isOurs = StealthAddress.checkStealthAddress(
      recipientKeys,
      result.ephemeralPubkey,
      result.address
    )
    assertTrue(isOurs)
  }
  
  @Test
  fun `stealth address check fails for wrong recipient`() {
    val recipientKeys = StealthAddress.generateKeys()
    val otherKeys = StealthAddress.generateKeys()
    
    // Sender derives address for recipient
    val result = StealthAddress.deriveStealthAddress(recipientKeys.metaAddress)
    
    // Other party should NOT be able to claim it
    val isTheirs = StealthAddress.checkStealthAddress(
      otherKeys,
      result.ephemeralPubkey,
      result.address
    )
    assertFalse(isTheirs)
  }
  
  @Test
  fun `stealth private key derivation`() {
    val recipientKeys = StealthAddress.generateKeys()
    val result = StealthAddress.deriveStealthAddress(recipientKeys.metaAddress)
    
    // Recipient derives spending key
    val spendingKeypair = StealthAddress.deriveStealthPrivateKey(
      recipientKeys,
      result.ephemeralPubkey
    )
    
    assertNotNull(spendingKeypair)
    
    // The derived keypair's public key should match the stealth address
    assertArrayEquals(
      result.address.bytes,
      spendingKeypair!!.publicKey.bytes
    )
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // Encrypted Memo Tests
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Test
  fun `encrypted memo roundtrip`() {
    val message = "Hello, this is a private message!"
    // Use X25519 keys for memo encryption
    val keys = X25519Exchange.generateKeypair()
    
    val encrypted = EncryptedMemo.encrypt(message, keys.publicKey)
    val decrypted = EncryptedMemo.decrypt(encrypted, keys.privateKey)
    
    assertNotNull(decrypted)
    assertEquals(message, decrypted!!.plaintext)
    assertNull(decrypted.memoType)
  }
  
  @Test
  fun `encrypted memo with type and expiration`() {
    val message = "Payment for service X"
    val keys = X25519Exchange.generateKeypair()
    
    val encrypted = EncryptedMemo.encrypt(
      message, 
      keys.publicKey,
      memoType = "payment",
      expirationSeconds = 3600
    )
    
    val decrypted = EncryptedMemo.decrypt(encrypted, keys.privateKey)
    
    assertNotNull(decrypted)
    assertEquals(message, decrypted!!.plaintext)
    assertEquals("payment", decrypted.memoType)
    assertTrue(decrypted.expiration!! > System.currentTimeMillis() / 1000)
  }
  
  @Test
  fun `encrypted memo fails with wrong key`() {
    val message = "Secret!"
    val keys1 = X25519Exchange.generateKeypair()
    val keys2 = X25519Exchange.generateKeypair()
    
    val encrypted = EncryptedMemo.encrypt(message, keys1.publicKey)
    val decrypted = EncryptedMemo.decrypt(encrypted, keys2.privateKey)
    
    assertNull(decrypted)
  }
  
  @Test
  fun `isEncryptedMemo detection`() {
    val keys = X25519Exchange.generateKeypair()
    val encrypted = EncryptedMemo.encrypt("test", keys.publicKey)
    
    assertTrue(EncryptedMemo.isEncryptedMemo(encrypted))
    assertFalse(EncryptedMemo.isEncryptedMemo("Hello world"))
    // Note: isEncryptedMemo just checks for the prefix, actual validity is checked during decrypt
    assertTrue(EncryptedMemo.isEncryptedMemo("art-enc-v1:invalid"))
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // X25519 Exchange Tests
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Test
  fun `X25519 keypair generation`() {
    val keypair = X25519Exchange.generateKeypair()
    
    assertEquals(32, keypair.publicKey.size)
    assertEquals(32, keypair.privateKey.size)
    
    // Public and private should be different
    assertFalse(keypair.publicKey.contentEquals(keypair.privateKey))
  }
  
  @Test
  fun `X25519 shared secret agreement`() {
    val alice = X25519Exchange.generateKeypair()
    val bob = X25519Exchange.generateKeypair()
    
    val aliceSecret = X25519Exchange.computeSharedSecret(alice.privateKey, bob.publicKey)
    val bobSecret = X25519Exchange.computeSharedSecret(bob.privateKey, alice.publicKey)
    
    // Both parties should derive the same shared secret
    assertArrayEquals(aliceSecret, bobSecret)
    assertEquals(32, aliceSecret.size)
  }
  
  @Test
  fun `HKDF key derivation`() {
    val sharedSecret = SecureCrypto.generateKey()
    val salt = SecureCrypto.generateSalt()
    
    val key1 = X25519Exchange.deriveKey(sharedSecret, salt)
    val key2 = X25519Exchange.deriveKey(sharedSecret, salt)
    
    // Same inputs should produce same output
    assertArrayEquals(key1, key2)
    
    // Different salt should produce different output
    val key3 = X25519Exchange.deriveKey(sharedSecret, SecureCrypto.generateSalt())
    assertFalse(key1.contentEquals(key3))
  }
  
  @Test
  fun `full key agreement flow`() {
    val alice = X25519Exchange.generateKeypair()
    val bob = X25519Exchange.generateKeypair()
    
    // Alice sends ephemeral pubkey along with message
    val aliceKey = X25519Exchange.agreeAndDerive(
      alice.privateKey, 
      bob.publicKey,
      alice.publicKey // ephemeral pubkey as salt
    )
    
    // Bob receives Alice's ephemeral pubkey and derives same key
    val bobKey = X25519Exchange.agreeAndDerive(
      bob.privateKey,
      alice.publicKey,
      alice.publicKey
    )
    
    // Both should derive the same encryption key
    assertArrayEquals(aliceKey, bobKey)
  }
  
  // ═══════════════════════════════════════════════════════════════════════════
  // Privacy Manager Integration Tests
  // ═══════════════════════════════════════════════════════════════════════════
  
  @Test
  fun `privacy manager session lifecycle`() = runBlocking {
    assertFalse(privacyManager.isSessionActive())
    
    privacyManager.startSession()
    assertTrue(privacyManager.isSessionActive())
    
    privacyManager.endSession()
    assertFalse(privacyManager.isSessionActive())
  }
  
  @Test
  fun `privacy manager stealth keys workflow`() = runBlocking {
    privacyManager.startSession()
    
    // Generate keys for a wallet
    val metaAddress = privacyManager.generateStealthKeys("wallet-1")
    assertNotNull(metaAddress)
    
    // Should be able to retrieve
    val retrieved = privacyManager.getStealthMetaAddress("wallet-1")
    assertNotNull(retrieved)
    assertArrayEquals(metaAddress.scanPubkey, retrieved!!.scanPubkey)
    
    // Other wallet should not exist
    val nonexistent = privacyManager.getStealthMetaAddress("wallet-2")
    assertNull(nonexistent)
  }
  
  @Test
  fun `privacy manager full stealth payment flow`() = runBlocking {
    privacyManager.startSession()
    
    // Recipient sets up stealth keys
    val recipientMeta = privacyManager.generateStealthKeys("recipient")
    
    // Sender derives stealth address
    val stealthResult = privacyManager.deriveStealthAddressForSending(recipientMeta)
    
    // Recipient checks if payment is for them
    val isOurs = privacyManager.checkIncomingStealthPayment(
      "recipient",
      stealthResult.ephemeralPubkey,
      stealthResult.address
    )
    assertTrue(isOurs)
    
    // Recipient derives spending key
    val spendingKey = privacyManager.deriveStealthSpendingKey(
      "recipient",
      stealthResult.ephemeralPubkey
    )
    assertNotNull(spendingKey)
  }
  
  @Test
  fun `password-based encryption`() {
    val data = "My secret wallet backup".toByteArray(Charsets.UTF_8)
    val password = "super-secure-password-123"
    
    val encrypted = privacyManager.encryptWithPassword(data, password)
    val decrypted = privacyManager.decryptWithPassword(encrypted, password)
    
    assertNotNull(decrypted)
    assertArrayEquals(data, decrypted)
  }
  
  @Test
  fun `password-based decryption fails with wrong password`() {
    val data = "Secret data".toByteArray(Charsets.UTF_8)
    val password = "correct-password"
    
    val encrypted = privacyManager.encryptWithPassword(data, password)
    val decrypted = privacyManager.decryptWithPassword(encrypted, "wrong-password")
    
    assertNull(decrypted)
  }
}
