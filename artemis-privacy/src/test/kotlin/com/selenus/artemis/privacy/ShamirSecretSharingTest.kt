/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.privacy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class ShamirSecretSharingTest {
    
    private lateinit var shamir: ShamirSecretSharing
    
    @BeforeEach
    fun setup() {
        shamir = ShamirSecretSharing()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Split and Recover Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `split and recover 2-of-3 scheme`() {
        val secret = "my super secret seed phrase data".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        
        assertEquals(3, shares.size)
        assertTrue(shares.all { it.data.isNotEmpty() })
        assertTrue(shares.map { it.shareIndex }.distinct().size == 3)
        
        // Recover with first 2 shares
        val result1 = shamir.recover(listOf(shares[0], shares[1]))
        assertTrue(result1 is RecoveryResult.Success)
        assertArrayEquals(secret, (result1 as RecoveryResult.Success).secret)
        
        // Recover with last 2 shares
        val result2 = shamir.recover(listOf(shares[1], shares[2]))
        assertTrue(result2 is RecoveryResult.Success)
        assertArrayEquals(secret, (result2 as RecoveryResult.Success).secret)
        
        // Recover with first and last
        val result3 = shamir.recover(listOf(shares[0], shares[2]))
        assertTrue(result3 is RecoveryResult.Success)
        assertArrayEquals(secret, (result3 as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `split and recover 3-of-5 scheme`() {
        val secret = ByteArray(64) { it.toByte() } // Simulate a seed
        
        val shares = shamir.split(secret, threshold = 3, totalShares = 5)
        
        assertEquals(5, shares.size)
        
        // Any 3 shares should work
        val result = shamir.recover(listOf(shares[0], shares[2], shares[4]))
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `split and recover with all shares`() {
        val secret = "test secret".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        
        // Using all shares should also work
        val result = shamir.recover(shares)
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `single byte secret works`() {
        val secret = byteArrayOf(0x42)
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        val result = shamir.recover(listOf(shares[0], shares[1]))
        
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `large secret works`() {
        val secret = ByteArray(1024) { Random.nextInt().toByte() }
        
        val shares = shamir.split(secret, threshold = 3, totalShares = 5)
        val result = shamir.recover(listOf(shares[1], shares[2], shares[3]))
        
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `secret with zero bytes works`() {
        val secret = byteArrayOf(0, 0, 0, 0, 0x01, 0, 0)
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        val result = shamir.recover(listOf(shares[0], shares[2]))
        
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `secret with all 0xFF bytes works`() {
        val secret = byteArrayOf(-1, -1, -1, -1) // 0xFF bytes
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 2)
        val result = shamir.recover(shares)
        
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `threshold must be at least 2`() {
        val secret = "test".toByteArray()
        
        assertThrows<IllegalArgumentException> {
            shamir.split(secret, threshold = 1, totalShares = 3)
        }
    }
    
    @Test
    fun `total shares must be at least threshold`() {
        val secret = "test".toByteArray()
        
        assertThrows<IllegalArgumentException> {
            shamir.split(secret, threshold = 4, totalShares = 3)
        }
    }
    
    @Test
    fun `total shares cannot exceed 255`() {
        val secret = "test".toByteArray()
        
        assertThrows<IllegalArgumentException> {
            shamir.split(secret, threshold = 2, totalShares = 256)
        }
    }
    
    @Test
    fun `empty secret is rejected`() {
        val secret = byteArrayOf()
        
        assertThrows<IllegalArgumentException> {
            shamir.split(secret, threshold = 2, totalShares = 3)
        }
    }
    
    @Test
    fun `recovery with insufficient shares fails`() {
        val secret = "test secret".toByteArray()
        
        val shares = shamir.split(secret, threshold = 3, totalShares = 5)
        
        // Only 2 shares when 3 required
        val result = shamir.recover(listOf(shares[0], shares[1]))
        
        assertTrue(result is RecoveryResult.InsufficientShares)
        assertEquals(3, (result as RecoveryResult.InsufficientShares).required)
        assertEquals(2, result.provided)
    }
    
    @Test
    fun `recovery with empty share list fails`() {
        val result = shamir.recover(emptyList())
        
        assertTrue(result is RecoveryResult.InsufficientShares)
    }
    
    @Test
    fun `recovery with duplicate shares fails`() {
        val secret = "test".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        
        // Duplicate share indices
        val duplicates = listOf(shares[0], shares[0])
        val result = shamir.recover(duplicates)
        
        assertTrue(result is RecoveryResult.InvalidShares)
    }
    
    @Test
    fun `recovery with mismatched schemes fails`() {
        val secret1 = "secret one".toByteArray()
        val secret2 = "secret two".toByteArray()
        
        val shares1 = shamir.split(secret1, threshold = 2, totalShares = 3)
        val shares2 = shamir.split(secret2, threshold = 2, totalShares = 3)
        
        // Mix shares from different secrets
        val mixed = listOf(shares1[0], shares2[1])
        val result = shamir.recover(mixed)
        
        // Should either fail validation or produce wrong result
        // Our implementation uses schemeId to detect mismatches
        assertTrue(result is RecoveryResult.InvalidShares)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Serialization Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `share serialization roundtrip`() {
        val secret = "serialize me".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        
        for (share in shares) {
            val serialized = share.toBytes()
            val deserialized = ShamirShare.fromBytes(serialized)
            
            assertNotNull(deserialized)
            assertEquals(share.shareIndex, deserialized!!.shareIndex)
            assertEquals(share.threshold, deserialized.threshold)
            assertEquals(share.schemeId, deserialized.schemeId)
            assertArrayEquals(share.data, deserialized.data)
        }
    }
    
    @Test
    fun `recovery from deserialized shares works`() {
        val secret = "recoverable secret".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 3)
        
        // Serialize and deserialize
        val serialized = shares.map { it.toBytes() }
        val deserialized = serialized.mapNotNull { ShamirShare.fromBytes(it) }
        
        // Recover from deserialized
        val result = shamir.recover(deserialized.take(2))
        
        assertTrue(result is RecoveryResult.Success)
        assertArrayEquals(secret, (result as RecoveryResult.Success).secret)
    }
    
    @Test
    fun `invalid serialized data returns null`() {
        val invalid = byteArrayOf(0x01, 0x02) // Too short
        
        val result = ShamirShare.fromBytes(invalid)
        
        assertNull(result)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GF(256) Property Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `shares are unique for same secret`() {
        val secret = "unique shares test".toByteArray()
        
        val shares = shamir.split(secret, threshold = 2, totalShares = 5)
        
        // All share data should be different
        val uniqueData = shares.map { it.data.toList() }.distinct()
        assertEquals(5, uniqueData.size)
    }
    
    @Test
    fun `multiple splits produce different shares`() {
        val secret = "randomness test".toByteArray()
        
        val shares1 = shamir.split(secret, threshold = 2, totalShares = 3)
        val shares2 = shamir.split(secret, threshold = 2, totalShares = 3)
        
        // Different random coefficients should produce different shares
        // (extremely unlikely to be equal)
        assertFalse(shares1[0].data.contentEquals(shares2[0].data))
    }
    
    @Test
    fun `BIP39 seed phrase recovery simulation`() {
        // Simulate a 24-word BIP39 seed (256 bits = 32 bytes entropy)
        val seedEntropy = ByteArray(32) { Random.nextInt().toByte() }
        
        // Create 5 shares, require 3 to recover
        val shares = shamir.split(seedEntropy, threshold = 3, totalShares = 5)
        
        // Simulate giving shares to:
        // Share 1: Bank safety deposit box
        // Share 2: Family member 1  
        // Share 3: Family member 2
        // Share 4: Personal safe
        // Share 5: Lawyer
        
        // Any 3 can recover
        val scenarios = listOf(
            listOf(shares[0], shares[1], shares[2]), // Bank + Family1 + Family2
            listOf(shares[0], shares[3], shares[4]), // Bank + Safe + Lawyer
            listOf(shares[1], shares[2], shares[4])  // Family1 + Family2 + Lawyer
        )
        
        for (scenario in scenarios) {
            val result = shamir.recover(scenario)
            assertTrue(result is RecoveryResult.Success)
            assertArrayEquals(seedEntropy, (result as RecoveryResult.Success).secret)
        }
    }
}
