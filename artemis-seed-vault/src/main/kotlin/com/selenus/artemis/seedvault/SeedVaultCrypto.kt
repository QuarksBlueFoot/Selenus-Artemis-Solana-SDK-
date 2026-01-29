/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * SeedVaultCrypto - Encryption and key derivation extensions for Seed Vault.
 * 
 * Addresses Seed Vault Issue #144: Add data encryption/decryption API
 * 
 * Provides:
 * - Symmetric encryption using derived keys
 * - ECDH shared secret derivation
 * - Message authentication
 * 
 * Note: This works alongside the Seed Vault, not as a replacement.
 * The actual key material stays in the Seed Vault; we derive application
 * keys for encryption operations.
 */
package com.selenus.artemis.seedvault

import com.selenus.artemis.runtime.Pubkey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted data container.
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val tag: ByteArray
) {
    /**
     * Serialize for storage or transmission.
     */
    fun serialize(): ByteArray {
        val result = ByteArray(4 + nonce.size + 4 + ciphertext.size + 4 + tag.size)
        var offset = 0
        
        // Nonce length + nonce
        writeInt(result, offset, nonce.size)
        offset += 4
        System.arraycopy(nonce, 0, result, offset, nonce.size)
        offset += nonce.size
        
        // Ciphertext length + ciphertext
        writeInt(result, offset, ciphertext.size)
        offset += 4
        System.arraycopy(ciphertext, 0, result, offset, ciphertext.size)
        offset += ciphertext.size
        
        // Tag length + tag
        writeInt(result, offset, tag.size)
        offset += 4
        System.arraycopy(tag, 0, result, offset, tag.size)
        
        return result
    }
    
    companion object {
        fun deserialize(data: ByteArray): EncryptedData {
            var offset = 0
            
            val nonceLen = readInt(data, offset)
            offset += 4
            val nonce = data.copyOfRange(offset, offset + nonceLen)
            offset += nonceLen
            
            val ctLen = readInt(data, offset)
            offset += 4
            val ciphertext = data.copyOfRange(offset, offset + ctLen)
            offset += ctLen
            
            val tagLen = readInt(data, offset)
            offset += 4
            val tag = data.copyOfRange(offset, offset + tagLen)
            
            return EncryptedData(ciphertext, nonce, tag)
        }
        
        private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        
        private fun readInt(buf: ByteArray, offset: Int): Int {
            return (buf[offset].toInt() and 0xFF) or
                   ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                   ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                   ((buf[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return ciphertext.contentEquals(other.ciphertext) &&
               nonce.contentEquals(other.nonce) &&
               tag.contentEquals(other.tag)
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

/**
 * Cryptographic operations using Seed Vault-derived keys.
 * 
 * This provides encryption capabilities that work with the Seed Vault
 * without exposing the underlying seed material.
 * 
 * Usage:
 * ```kotlin
 * val crypto = SeedVaultCrypto()
 * 
 * // Derive an encryption key from a signature
 * // (User signs a fixed message to derive deterministic key)
 * val encKey = crypto.deriveEncryptionKey(signature, purpose = "file-encryption")
 * 
 * // Encrypt data
 * val encrypted = crypto.encrypt(data, encKey)
 * 
 * // Decrypt data
 * val decrypted = crypto.decrypt(encrypted, encKey)
 * ```
 */
class SeedVaultCrypto {
    
    private val random = SecureRandom()
    
    companion object {
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val KEY_SIZE = 32
        private const val DOMAIN_SEPARATOR = "artemis:seedvault:crypto:v1"
    }
    
    /**
     * Derive an encryption key from a signature.
     * 
     * This allows using the Seed Vault for deterministic key derivation:
     * 1. App creates a fixed "key derivation message"
     * 2. User signs it with Seed Vault
     * 3. Signature is hashed to produce encryption key
     * 
     * @param signature The signature from Seed Vault
     * @param purpose Application-specific purpose string
     * @return 32-byte encryption key
     */
    fun deriveEncryptionKey(signature: ByteArray, purpose: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_SEPARATOR.toByteArray())
        digest.update(purpose.toByteArray())
        digest.update(signature)
        return digest.digest()
    }
    
    /**
     * Derive a shared secret using ECDH-style derivation.
     * 
     * For X25519 key exchange:
     * 1. Each party has a keypair
     * 2. Combine private key with other's public key
     * 3. Result is the shared secret
     * 
     * This method takes a signature (from signing the peer's pubkey)
     * and derives a shared secret deterministically.
     * 
     * @param mySignature Signature produced by signing peer's pubkey
     * @param peerPubkey The peer's public key
     * @return 32-byte shared secret
     */
    fun deriveSharedSecret(mySignature: ByteArray, peerPubkey: Pubkey): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_SEPARATOR.toByteArray())
        digest.update("shared-secret".toByteArray())
        digest.update(mySignature)
        digest.update(peerPubkey.bytes)
        return digest.digest()
    }
    
    /**
     * Encrypt data using AES-256-GCM.
     * 
     * @param plaintext The data to encrypt
     * @param key 32-byte encryption key
     * @param associatedData Optional authenticated but unencrypted data
     * @return Encrypted data container
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): EncryptedData {
        require(key.size == KEY_SIZE) { "Key must be 32 bytes" }
        
        val nonce = ByteArray(NONCE_SIZE)
        random.nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_SIZE * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }
        
        val ciphertextWithTag = cipher.doFinal(plaintext)
        
        // GCM appends tag to ciphertext; split them
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - TAG_SIZE)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - TAG_SIZE, ciphertextWithTag.size)
        
        return EncryptedData(ciphertext, nonce, tag)
    }
    
    /**
     * Decrypt data using AES-256-GCM.
     * 
     * @param encrypted The encrypted data container
     * @param key 32-byte encryption key
     * @param associatedData Optional authenticated data (must match encryption)
     * @return Decrypted plaintext
     * @throws javax.crypto.AEADBadTagException if authentication fails
     */
    fun decrypt(
        encrypted: EncryptedData,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be 32 bytes" }
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_SIZE * 8, encrypted.nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }
        
        // Recombine ciphertext and tag
        val ciphertextWithTag = encrypted.ciphertext + encrypted.tag
        
        return cipher.doFinal(ciphertextWithTag)
    }
    
    /**
     * Create an HMAC for data authentication.
     * 
     * @param data The data to authenticate
     * @param key The authentication key
     * @return 32-byte HMAC
     */
    fun hmac(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    /**
     * Verify an HMAC.
     * 
     * @param data The data that was authenticated
     * @param expectedMac The expected HMAC value
     * @param key The authentication key
     * @return True if HMAC matches
     */
    fun verifyHmac(data: ByteArray, expectedMac: ByteArray, key: ByteArray): Boolean {
        val computed = hmac(data, key)
        return MessageDigest.isEqual(computed, expectedMac)
    }
    
    /**
     * Create the message to sign for key derivation.
     * 
     * This message should be signed by the Seed Vault to derive
     * a deterministic encryption key.
     * 
     * @param purpose The purpose of the derived key
     * @param accountId Optional account identifier
     * @return Message bytes to sign
     */
    fun createKeyDerivationMessage(purpose: String, accountId: String? = null): ByteArray {
        val msg = StringBuilder()
            .append("Artemis Key Derivation\n")
            .append("Purpose: $purpose\n")
        
        if (accountId != null) {
            msg.append("Account: $accountId\n")
        }
        
        msg.append("Domain: $DOMAIN_SEPARATOR")
        
        return msg.toString().toByteArray()
    }
}

/**
 * Extension function to use crypto with SeedVaultManager.
 */
suspend fun SeedVaultManager.deriveEncryptionKey(
    authToken: String,
    purpose: String,
    crypto: SeedVaultCrypto = SeedVaultCrypto()
): ByteArray {
    val message = crypto.createKeyDerivationMessage(purpose)
    val signature = sign(authToken, listOf(message)).first()
    return crypto.deriveEncryptionKey(signature, purpose)
}
