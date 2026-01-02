/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.seedvault

import com.selenus.artemis.seedvault.SolanaDerivation
import com.selenus.artemis.seedvault.DerivationScheme
import com.selenus.artemis.seedvault.PathComponent
import com.selenus.artemis.seedvault.WalletPresets
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class SolanaDerivationTest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Path Parsing Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `parse standard Solana BIP44 path`() {
        val path = "m/44'/501'/0'/0'"
        
        val result = SolanaDerivation.parsePath(path)
        
        assertNotNull(result)
        assertEquals(4, result!!.components.size)
        assertEquals(listOf(44, 501, 0, 0), result.components.map { it.index })
        assertTrue(result.components.all { it.hardened })
    }
    
    @Test
    fun `parse path with mixed hardened and non-hardened`() {
        val path = "m/44'/501'/0/0"
        
        val result = SolanaDerivation.parsePath(path)
        
        assertNotNull(result)
        assertEquals(4, result!!.components.size)
        assertTrue(result.components[0].hardened)
        assertTrue(result.components[1].hardened)
        assertFalse(result.components[2].hardened)
        assertFalse(result.components[3].hardened)
    }
    
    @Test
    fun `parse Ledger Live path`() {
        val path = "m/44'/501'/5'"
        
        val result = SolanaDerivation.parsePath(path)
        
        assertNotNull(result)
        assertEquals(3, result!!.components.size)
        assertEquals(5, result.components[2].index)
    }
    
    @Test
    fun `parse path without m prefix`() {
        val path = "44'/501'/0'/0'"
        
        val result = SolanaDerivation.parsePath(path)
        
        assertNotNull(result)
        assertEquals(4, result!!.components.size)
    }
    
    @Test
    fun `invalid path returns null`() {
        assertNull(SolanaDerivation.parsePath("invalid"))
        assertNull(SolanaDerivation.parsePath("m/abc'/501'/0'"))
        assertNull(SolanaDerivation.parsePath(""))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `validate standard Solana paths`() {
        val validPaths = listOf(
            "m/44'/501'/0'/0'",
            "m/44'/501'/0'",
            "m/44'/501'/99'/0'",
            "m/44'/501'/0'/0'/0'"
        )
        
        for (path in validPaths) {
            assertTrue(SolanaDerivation.isValidSolanaPath(path), "Path should be valid: $path")
        }
    }
    
    @Test
    fun `reject non-Solana coin types`() {
        val invalidPaths = listOf(
            "m/44'/60'/0'/0",   // Ethereum
            "m/44'/0'/0'/0",    // Bitcoin
            "m/44'/1'/0'/0"     // Bitcoin testnet
        )
        
        for (path in invalidPaths) {
            assertFalse(SolanaDerivation.isValidSolanaPath(path), "Path should be invalid: $path")
        }
    }
    
    @Test
    fun `reject paths with invalid purpose`() {
        assertFalse(SolanaDerivation.isValidSolanaPath("m/45'/501'/0'"))
        assertFalse(SolanaDerivation.isValidSolanaPath("m/86'/501'/0'"))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Path Building Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `build standard Solana path`() {
        val path = SolanaDerivation.buildPath(
            purpose = 44,
            coinType = 501,
            account = 0,
            change = 0
        )
        
        assertEquals("m/44'/501'/0'/0'", path)
    }
    
    @Test
    fun `build path with different account`() {
        val path = SolanaDerivation.buildPath(
            purpose = 44,
            coinType = 501,
            account = 5,
            change = 0
        )
        
        assertEquals("m/44'/501'/5'/0'", path)
    }
    
    @Test
    fun `build Ledger Live style path`() {
        val path = SolanaDerivation.buildLedgerLivePath(account = 3)
        
        assertEquals("m/44'/501'/3'", path)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Scheme Detection Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect standard scheme`() {
        val scheme = SolanaDerivation.detectScheme("m/44'/501'/0'/0'")
        
        assertEquals(DerivationScheme.BIP44_STANDARD, scheme)
    }
    
    @Test
    fun `detect Ledger Live scheme`() {
        val scheme = SolanaDerivation.detectScheme("m/44'/501'/0'")
        
        assertEquals(DerivationScheme.LEDGER_LIVE, scheme)
    }
    
    @Test
    fun `detect extended scheme`() {
        val scheme = SolanaDerivation.detectScheme("m/44'/501'/0'/0'/0'")
        
        assertEquals(DerivationScheme.ED25519_BIP32, scheme)
    }
    
    @Test
    fun `unknown path returns unknown scheme`() {
        val scheme = SolanaDerivation.detectScheme("m/44'/60'/0'/0")
        
        assertEquals(DerivationScheme.UNKNOWN, scheme)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Wallet Preset Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `Phantom wallet paths`() {
        val paths = WalletPresets.phantomPaths(accountCount = 3)
        
        assertEquals(3, paths.size)
        assertEquals("m/44'/501'/0'/0'", paths[0])
        assertEquals("m/44'/501'/1'/0'", paths[1])
        assertEquals("m/44'/501'/2'/0'", paths[2])
    }
    
    @Test
    fun `Solflare wallet paths`() {
        val paths = WalletPresets.solflarePaths(accountCount = 2)
        
        assertEquals(2, paths.size)
        assertEquals("m/44'/501'/0'/0'", paths[0])
        assertEquals("m/44'/501'/1'/0'", paths[1])
    }
    
    @Test
    fun `Ledger Live paths`() {
        val paths = WalletPresets.ledgerLivePaths(accountCount = 3)
        
        assertEquals(3, paths.size)
        assertEquals("m/44'/501'/0'", paths[0])
        assertEquals("m/44'/501'/1'", paths[1])
        assertEquals("m/44'/501'/2'", paths[2])
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Path Component Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `path component to string with hardened`() {
        val component = PathComponent(44, true)
        
        assertEquals("44'", component.toString())
    }
    
    @Test
    fun `path component to string without hardened`() {
        val component = PathComponent(0, false)
        
        assertEquals("0", component.toString())
    }
    
    @Test
    fun `path component to BIP32 value`() {
        val hardened = PathComponent(44, true)
        val normal = PathComponent(0, false)
        
        assertEquals(0x8000002C.toInt(), hardened.toBip32Value())
        assertEquals(0, normal.toBip32Value())
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Next Account Path Tests
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `get next account path`() {
        val current = "m/44'/501'/0'/0'"
        val next = SolanaDerivation.nextAccountPath(current)
        
        assertEquals("m/44'/501'/1'/0'", next)
    }
    
    @Test
    fun `get next Ledger account path`() {
        val current = "m/44'/501'/5'"
        val next = SolanaDerivation.nextAccountPath(current)
        
        assertEquals("m/44'/501'/6'", next)
    }
    
    @Test
    fun `invalid path returns same path`() {
        val invalid = "invalid"
        val result = SolanaDerivation.nextAccountPath(invalid)
        
        assertEquals(invalid, result)
    }
}
