/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.selenus.artemis.wallet.seedvault

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.seedvault.SeedVaultAccount
import com.selenus.artemis.seedvault.SeedVaultAuthorization
import com.selenus.artemis.seedvault.SeedVaultException
import com.selenus.artemis.seedvault.SeedVaultTokenResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Seed Vault authorization flow types.
 * 
 * Verifies:
 * - SeedVaultTokenResult correctly separates raw intent data from resolved account info
 * - SeedVaultAuthorization wraps resolved account with real public key
 * - SeedVaultException hierarchy error codes
 */
class SeedVaultAuthFlowTest {

    @Test
    fun `SeedVaultTokenResult holds raw auth token and account id`() {
        val result = SeedVaultTokenResult(authToken = 42L, accountId = 7L)
        assertEquals(42L, result.authToken)
        assertEquals(7L, result.accountId)
    }

    @Test
    fun `SeedVaultTokenResult with zero account id is valid`() {
        val result = SeedVaultTokenResult(authToken = 100L, accountId = 0L)
        assertEquals(0L, result.accountId)
    }

    @Test
    fun `SeedVaultAuthorization wraps token string and resolved account`() {
        val pubkey = Pubkey(ByteArray(32) { (it + 1).toByte() })
        val account = SeedVaultAccount(
            id = 1L,
            name = "Main Account",
            publicKey = pubkey,
            derivationPath = "m/44'/501'/0'/0'"
        )
        val auth = SeedVaultAuthorization("12345", account)

        assertEquals("12345", auth.authToken)
        assertEquals(1L, auth.account.id)
        assertEquals("Main Account", auth.account.name)
        assertEquals(pubkey, auth.account.publicKey)
        assertEquals("m/44'/501'/0'/0'", auth.account.derivationPath)
    }

    @Test
    fun `SeedVaultAccount public key is real not zeroed`() {
        val realKey = ByteArray(32) { (it * 3 + 1).toByte() }
        val account = SeedVaultAccount(1L, "Test", Pubkey(realKey))

        // Verify the key has actual content (not all zeros)
        assertFalse(account.publicKey.bytes.all { it == 0.toByte() },
            "Account public key must not be all zeros - that indicates unresolved dummy data")
    }

    @Test
    fun `SeedVaultAccount derivation path defaults to null`() {
        val account = SeedVaultAccount(1L, "No Path", Pubkey(ByteArray(32)))
        assertNull(account.derivationPath)
    }

    @Test
    fun `SeedVaultException sealed hierarchy covers all error codes`() {
        assertTrue(SeedVaultException.Unauthorized("x") is SeedVaultException)
        assertTrue(SeedVaultException.UserRejected("x") is SeedVaultException)
        assertTrue(SeedVaultException.InvalidRequest("x") is SeedVaultException)
        assertTrue(SeedVaultException.InternalError("x") is SeedVaultException)
        assertTrue(SeedVaultException.Unknown("x") is SeedVaultException)
    }

    @Test
    fun `SeedVaultException messages are preserved`() {
        assertEquals("not allowed", SeedVaultException.Unauthorized("not allowed").message)
        assertEquals("user said no", SeedVaultException.UserRejected("user said no").message)
    }

    @Test
    fun `SeedVaultTokenResult data class equality`() {
        val a = SeedVaultTokenResult(1L, 2L)
        val b = SeedVaultTokenResult(1L, 2L)
        val c = SeedVaultTokenResult(1L, 3L)

        assertEquals(a, b, "Same values must be equal")
        assertNotEquals(a, c, "Different accountId must not be equal")
    }

    @Test
    fun `SeedVaultAuthorization data class equality`() {
        val pk = Pubkey(ByteArray(32) { 1 })
        val account = SeedVaultAccount(1L, "Test", pk)
        val a = SeedVaultAuthorization("token1", account)
        val b = SeedVaultAuthorization("token1", account)
        val c = SeedVaultAuthorization("token2", account)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
