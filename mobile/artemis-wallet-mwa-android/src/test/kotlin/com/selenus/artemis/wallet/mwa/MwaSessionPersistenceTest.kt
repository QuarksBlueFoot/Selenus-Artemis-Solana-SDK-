/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.mwa

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class MwaSessionPersistenceTest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PersistedSession Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `session is not expired when within timeout`() {
        val session = PersistedSession(
            authToken = "test-token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis() - 1000,
            expiresAt = System.currentTimeMillis() + 3600_000 // 1 hour from now
        )
        
        assertFalse(session.isExpired)
    }
    
    @Test
    fun `session is expired when past expiration`() {
        val session = PersistedSession(
            authToken = "test-token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis() - 7200_000, // 2 hours ago
            createdAt = System.currentTimeMillis() - 10800_000, // 3 hours ago
            expiresAt = System.currentTimeMillis() - 3600_000 // Expired 1 hour ago
        )
        
        assertTrue(session.isExpired)
    }
    
    @Test
    fun `session age is calculated correctly`() {
        val createdAt = System.currentTimeMillis() - 3600_000 // 1 hour ago
        val session = PersistedSession(
            authToken = "test-token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = createdAt,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        val ageMs = session.ageMs
        assertTrue(ageMs >= 3600_000)
        assertTrue(ageMs < 3700_000) // Allow 100ms tolerance
    }
    
    @Test
    fun `session idle time is calculated correctly`() {
        val lastActive = System.currentTimeMillis() - 300_000 // 5 minutes ago
        val session = PersistedSession(
            authToken = "test-token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = lastActive,
            createdAt = System.currentTimeMillis() - 3600_000,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        val idleMs = session.idleMs
        assertTrue(idleMs >= 300_000)
        assertTrue(idleMs < 400_000) // Allow 100ms tolerance
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SessionRecoveryResult Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `successful recovery result`() {
        val session = PersistedSession(
            authToken = "recovered-token",
            publicKey = "22222222222222222222222222222222",
            walletName = "Solflare",
            walletPackage = "com.solflare.mobile",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis() - 600_000,
            expiresAt = System.currentTimeMillis() + 3000_000
        )
        
        val result = SessionRecoveryResult.Recovered(session)
        
        assertTrue(result is SessionRecoveryResult.Recovered)
        assertEquals("recovered-token", (result as SessionRecoveryResult.Recovered).session.authToken)
    }
    
    @Test
    fun `expired recovery result`() {
        val session = PersistedSession(
            authToken = "old-token",
            publicKey = "33333333333333333333333333333333",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis() - 86400_000, // 1 day ago
            createdAt = System.currentTimeMillis() - 90000_000,
            expiresAt = System.currentTimeMillis() - 3600_000 // Expired
        )
        
        val result = SessionRecoveryResult.Expired(session)
        
        assertTrue(result is SessionRecoveryResult.Expired)
    }
    
    @Test
    fun `no session recovery result`() {
        val result = SessionRecoveryResult.NoSession
        
        assertTrue(result is SessionRecoveryResult.NoSession)
    }
    
    @Test
    fun `corrupted data recovery result`() {
        val result = SessionRecoveryResult.CorruptedData("Invalid JSON format")
        
        assertTrue(result is SessionRecoveryResult.CorruptedData)
        assertEquals("Invalid JSON format", (result as SessionRecoveryResult.CorruptedData).reason)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Session Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `session with empty auth token is invalid`() {
        val session = PersistedSession(
            authToken = "",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        assertFalse(session.isValid)
    }
    
    @Test
    fun `session with empty public key is invalid`() {
        val session = PersistedSession(
            authToken = "valid-token",
            publicKey = "",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        assertFalse(session.isValid)
    }
    
    @Test
    fun `session with all fields is valid`() {
        val session = PersistedSession(
            authToken = "valid-token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        assertTrue(session.isValid)
        assertFalse(session.isExpired)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Session Touch Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `touching session updates lastActiveAt`() {
        val originalLastActive = System.currentTimeMillis() - 60_000 // 1 minute ago
        val session = PersistedSession(
            authToken = "token",
            publicKey = "11111111111111111111111111111111",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = originalLastActive,
            createdAt = System.currentTimeMillis() - 3600_000,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        val touched = session.touch()
        
        assertTrue(touched.lastActiveAt > originalLastActive)
        assertTrue(touched.lastActiveAt >= System.currentTimeMillis() - 1000)
    }
    
    @Test
    fun `touching session preserves other fields`() {
        val session = PersistedSession(
            authToken = "original-token",
            publicKey = "original-pubkey",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis() - 60_000,
            createdAt = System.currentTimeMillis() - 3600_000,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        val touched = session.touch()
        
        assertEquals(session.authToken, touched.authToken)
        assertEquals(session.publicKey, touched.publicKey)
        assertEquals(session.walletName, touched.walletName)
        assertEquals(session.walletPackage, touched.walletPackage)
        assertEquals(session.createdAt, touched.createdAt)
        assertEquals(session.expiresAt, touched.expiresAt)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Serialization Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `session serialization roundtrip`() {
        val original = PersistedSession(
            authToken = "test-auth-token-12345",
            publicKey = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            walletName = "Phantom",
            walletPackage = "app.phantom",
            lastActiveAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis() - 1000,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        val json = original.toJson()
        val restored = PersistedSession.fromJson(json)
        
        assertNotNull(restored)
        assertEquals(original.authToken, restored!!.authToken)
        assertEquals(original.publicKey, restored.publicKey)
        assertEquals(original.walletName, restored.walletName)
        assertEquals(original.walletPackage, restored.walletPackage)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.expiresAt, restored.expiresAt)
    }
    
    @Test
    fun `invalid JSON returns null`() {
        val invalidJson = "{ invalid json }"
        
        val result = PersistedSession.fromJson(invalidJson)
        
        assertNull(result)
    }
    
    @Test
    fun `empty JSON returns null`() {
        val result = PersistedSession.fromJson("")
        
        assertNull(result)
    }
    
    @Test
    fun `JSON with missing fields returns null`() {
        val incompleteJson = """{"authToken": "test"}"""
        
        val result = PersistedSession.fromJson(incompleteJson)
        
        assertNull(result)
    }
}
