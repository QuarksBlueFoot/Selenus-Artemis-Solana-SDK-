package com.solanamobile.seedvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedVaultCompatTest {

    @Test
    fun `Seed Vault type aliases preserve request and response classes`() {
        val request: SigningRequest = SigningRequest(byteArrayOf(1, 2, 3), arrayListOf<android.net.Uri>())
        val response: SigningResponse = SigningResponse(
            arrayListOf(byteArrayOf(4, 5, 6)),
            arrayListOf<android.net.Uri>()
        )

        assertTrue(request.payload.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(0, request.requestedSignatures.size)
        assertTrue(response.signatures.single().contentEquals(byteArrayOf(4, 5, 6)))
        assertEquals(0, response.resolvedDerivationPaths.size)
    }

    @Test
    fun `SeedVault access types preserve granted semantics`() {
        assertFalse(SeedVault.AccessType.NONE.isGranted())
        assertTrue(SeedVault.AccessType.STANDARD.isGranted())
        assertTrue(SeedVault.AccessType.PRIVILEGED.isGranted())
        assertTrue(SeedVault.AccessType.SIMULATED.isGranted())
        assertEquals(SeedVault.MIN_API_FOR_SEED_VAULT_PRIVILEGED, SeedVault.SEEDVAULT_MIN_API_INT)
    }

    @Test
    fun `compat exceptions preserve upstream class names and messages`() {
        assertEquals("not modified", Wallet.NotModifiedException("not modified").message)
        assertEquals("failed", Wallet.ActionFailedException("failed").message)
        assertEquals("invalid", AuthTokenInvalidException("invalid").message)
    }
}
