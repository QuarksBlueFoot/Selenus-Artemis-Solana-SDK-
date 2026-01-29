/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.seedvault

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class SeedVaultCryptoTest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Key Derivation Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `deriveKey produces 32-byte key`() {
        val signatureBytes = ByteArray(64) { (it * 7).toByte() }
        val context = "encryption-key"
        
        val key = SeedVaultCrypto.deriveKey(signatureBytes, context)
        
        assertEquals(32, key.size)
    }
    
    @Test
    fun `deriveKey is deterministic`() {
        val signatureBytes = ByteArray(64) { it.toByte() }
        val context = "test-context"
        
        val key1 = SeedVaultCrypto.deriveKey(signatureBytes, context)
        val key2 = SeedVaultCrypto.deriveKey(signatureBytes, context)
        
        assertArrayEquals(key1, key2)
    }
    
    @Test
    fun `deriveKey produces different keys for different contexts`() {
        val signatureBytes = ByteArray(64) { it.toByte() }
        
        val key1 = SeedVaultCrypto.deriveKey(signatureBytes, "context-a")
        val key2 = SeedVaultCrypto.deriveKey(signatureBytes, "context-b")
        
        assertFalse(key1.contentEquals(key2))
    }
    
    @Test
    fun `deriveKey produces different keys for different signatures`() {
        val sig1 = ByteArray(64) { 0x01 }
        val sig2 = ByteArray(64) { 0x02 }
        val context = "same-context"
        
        val key1 = SeedVaultCrypto.deriveKey(sig1, context)
        val key2 = SeedVaultCrypto.deriveKey(sig2, context)
        
        assertFalse(key1.contentEquals(key2))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Encryption/Decryption Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `encrypt and decrypt roundtrip`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Hello, Seed Vault!".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        val decrypted = SeedVaultCrypto.decrypt(encrypted, key)
        
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }
    
    @Test
    fun `encrypt produces EncryptedData with all fields`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "test data".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        
        assertEquals(12, encrypted.nonce.size) // GCM standard nonce size
        assertTrue(encrypted.ciphertext.isNotEmpty())
        assertEquals(16, encrypted.tag.size) // GCM tag size
    }
    
    @Test
    fun `decrypt with wrong key fails`() {
        val key1 = ByteArray(32) { it.toByte() }
        val key2 = ByteArray(32) { (it + 1).toByte() }
        val plaintext = "secret data".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key1)
        val decrypted = SeedVaultCrypto.decrypt(encrypted, key2)
        
        assertNull(decrypted)
    }
    
    @Test
    fun `decrypt with tampered ciphertext fails`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "important message".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        
        // Tamper with ciphertext
        val tamperedCiphertext = encrypted.ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        
        val tampered = EncryptedData(encrypted.nonce, tamperedCiphertext, encrypted.tag)
        val decrypted = SeedVaultCrypto.decrypt(tampered, key)
        
        assertNull(decrypted)
    }
    
    @Test
    fun `decrypt with tampered tag fails`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "authenticated data".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        
        // Tamper with authentication tag
        val tamperedTag = encrypted.tag.clone()
        tamperedTag[0] = (tamperedTag[0].toInt() xor 0xFF).toByte()
        
        val tampered = EncryptedData(encrypted.nonce, encrypted.ciphertext, tamperedTag)
        val decrypted = SeedVaultCrypto.decrypt(tampered, key)
        
        assertNull(decrypted)
    }
    
    @Test
    fun `encrypt produces unique nonces`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "same message".toByteArray()
        
        val encrypted1 = SeedVaultCrypto.encrypt(plaintext, key)
        val encrypted2 = SeedVaultCrypto.encrypt(plaintext, key)
        
        assertFalse(encrypted1.nonce.contentEquals(encrypted2.nonce))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EncryptedData Serialization Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `EncryptedData serialization roundtrip`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "serialize me".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        val serialized = encrypted.toBytes()
        val deserialized = EncryptedData.fromBytes(serialized)
        
        assertNotNull(deserialized)
        assertArrayEquals(encrypted.nonce, deserialized!!.nonce)
        assertArrayEquals(encrypted.ciphertext, deserialized.ciphertext)
        assertArrayEquals(encrypted.tag, deserialized.tag)
    }
    
    @Test
    fun `decrypt from deserialized EncryptedData`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "roundtrip test".toByteArray()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        val serialized = encrypted.toBytes()
        val deserialized = EncryptedData.fromBytes(serialized)!!
        
        val decrypted = SeedVaultCrypto.decrypt(deserialized, key)
        
        assertArrayEquals(plaintext, decrypted)
    }
    
    @Test
    fun `invalid EncryptedData bytes returns null`() {
        val tooShort = byteArrayOf(0x01, 0x02, 0x03)
        val result = EncryptedData.fromBytes(tooShort)
        
        assertNull(result)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HMAC Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `hmac produces 32-byte output`() {
        val key = ByteArray(32) { it.toByte() }
        val data = "message to authenticate".toByteArray()
        
        val hmac = SeedVaultCrypto.hmac(data, key)
        
        assertEquals(32, hmac.size)
    }
    
    @Test
    fun `hmac is deterministic`() {
        val key = ByteArray(32) { it.toByte() }
        val data = "consistent message".toByteArray()
        
        val hmac1 = SeedVaultCrypto.hmac(data, key)
        val hmac2 = SeedVaultCrypto.hmac(data, key)
        
        assertArrayEquals(hmac1, hmac2)
    }
    
    @Test
    fun `hmac differs with different keys`() {
        val key1 = ByteArray(32) { 0x01 }
        val key2 = ByteArray(32) { 0x02 }
        val data = "same message".toByteArray()
        
        val hmac1 = SeedVaultCrypto.hmac(data, key1)
        val hmac2 = SeedVaultCrypto.hmac(data, key2)
        
        assertFalse(hmac1.contentEquals(hmac2))
    }
    
    @Test
    fun `hmac differs with different data`() {
        val key = ByteArray(32) { it.toByte() }
        val data1 = "message one".toByteArray()
        val data2 = "message two".toByteArray()
        
        val hmac1 = SeedVaultCrypto.hmac(data1, key)
        val hmac2 = SeedVaultCrypto.hmac(data2, key)
        
        assertFalse(hmac1.contentEquals(hmac2))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared Secret Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `deriveSharedSecret produces 32-byte output`() {
        val mySignature = ByteArray(64) { it.toByte() }
        val theirPublicKey = ByteArray(32) { (it * 3).toByte() }
        
        val shared = SeedVaultCrypto.deriveSharedSecret(mySignature, theirPublicKey)
        
        assertEquals(32, shared.size)
    }
    
    @Test
    fun `deriveSharedSecret is deterministic`() {
        val mySignature = ByteArray(64) { it.toByte() }
        val theirPublicKey = ByteArray(32) { (it * 5).toByte() }
        
        val shared1 = SeedVaultCrypto.deriveSharedSecret(mySignature, theirPublicKey)
        val shared2 = SeedVaultCrypto.deriveSharedSecret(mySignature, theirPublicKey)
        
        assertArrayEquals(shared1, shared2)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `encrypt empty plaintext`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = byteArrayOf()
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        val decrypted = SeedVaultCrypto.decrypt(encrypted, key)
        
        assertNotNull(decrypted)
        assertEquals(0, decrypted!!.size)
    }
    
    @Test
    fun `encrypt large plaintext`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = ByteArray(1024 * 100) { (it % 256).toByte() } // 100KB
        
        val encrypted = SeedVaultCrypto.encrypt(plaintext, key)
        val decrypted = SeedVaultCrypto.decrypt(encrypted, key)
        
        assertArrayEquals(plaintext, decrypted)
    }
}
