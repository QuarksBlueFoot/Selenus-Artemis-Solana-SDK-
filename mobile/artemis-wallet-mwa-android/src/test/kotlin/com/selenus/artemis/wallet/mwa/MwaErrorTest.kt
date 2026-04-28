package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.wallet.SessionExpiredException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour conformance for the MWA error taxonomy ([MwaError]) and the
 * recovery-hint enum ([Recovery]).
 *
 * Tests the spec-first surface promised by the parity matrix: `from(throwable)`
 * mapping is deterministic, every sealed subclass carries a stable code/reason
 * shape, and `SignedButNotBroadcast` round-trips its signed bytes intact.
 */
class MwaErrorTest {

    @Test
    fun `WalletNotFound has stable code reason and recovery`() {
        val err = MwaError.WalletNotFound()
        assertEquals(-4001, err.code)
        assertEquals("WALLET_NOT_FOUND", err.reason)
        assertEquals(Recovery.InstallOrOpenWallet, err.recovery)
        assertTrue(
            "hint mentions wallet",
            err.hint.contains("wallet", ignoreCase = true)
        )
        assertTrue(
            "message contains code",
            err.message?.contains("-4001") == true
        )
    }

    @Test
    fun `from connect timeout maps to ConnectionTimeout preserving cause`() {
        val cause = IllegalStateException("connect timeout: WS never opened")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to ConnectionTimeout", mapped is MwaError.ConnectionTimeout)
        assertEquals(-4002, mapped.code)
        assertEquals(Recovery.Retry, mapped.recovery)
        assertSame("cause is preserved", cause, mapped.cause)
    }

    @Test
    fun `from SessionExpiredException maps to SessionExpired`() {
        val cause = SessionExpiredException("token reauth needed")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to SessionExpired", mapped is MwaError.SessionExpired)
        assertEquals(-4011, mapped.code)
        assertEquals(Recovery.Reauthorize, mapped.recovery)
        assertSame(cause, mapped.cause)
    }

    @Test
    fun `from user canceled maps to UserDeclined`() {
        val cause = IllegalStateException("user canceled the bottom sheet")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to UserDeclined", mapped is MwaError.UserDeclined)
        assertEquals(-4012, mapped.code)
        assertEquals(Recovery.UserCancel, mapped.recovery)
    }

    @Test
    fun `from cancelled spelling also maps to UserDeclined`() {
        // The mapper handles both "cancelled" (UK) and "canceled" (US).
        val cause = RuntimeException("Operation cancelled by peer")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to UserDeclined", mapped is MwaError.UserDeclined)
    }

    @Test
    fun `SignedButNotBroadcast preserves signed raw bytes`() {
        val signed = ByteArray(96) { (it * 7).toByte() }
        val err = MwaError.SignedButNotBroadcast(signedRaw = signed)
        assertEquals(-4023, err.code)
        assertEquals(Recovery.BroadcastSignedFallback, err.recovery)
        assertArrayEquals("bytes round-trip without copy or truncation", signed, err.signedRaw)
        assertEquals("SIGNED_BUT_NOT_BROADCAST", err.reason)
    }

    @Test
    fun `from unknown throwable maps to InternalError preserving cause and message`() {
        val cause = RuntimeException("totally unexpected gremlin")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to InternalError", mapped is MwaError.InternalError)
        assertEquals(-4999, mapped.code)
        assertEquals(Recovery.Fatal, mapped.recovery)
        assertSame("cause preserved", cause, mapped.cause)
        assertNotNull(mapped.message)
        assertTrue(
            "internal hint mentions the original message",
            mapped.hint.contains("totally unexpected gremlin")
        )
    }

    @Test
    fun `from MwaError returns the same instance`() {
        // The spec contract: when caller already has a typed MwaError,
        // re-mapping must be a no-op so retry loops do not double-wrap.
        val original = MwaError.AssociationMismatch()
        val passed = MwaError.from(original)
        assertSame(original, passed)
    }

    @Test
    fun `from cipher related throwable maps to SessionCipherFailed`() {
        val cause = RuntimeException("AES-GCM decrypt failed: bad tag")
        val mapped = MwaError.from(cause)
        assertTrue("mapped to SessionCipherFailed", mapped is MwaError.SessionCipherFailed)
        assertEquals(-4005, mapped.code)
        assertEquals(Recovery.Retry, mapped.recovery)
    }

    @Test
    fun `MaxPayloadsExceeded carries requested and maxAllowed in hint`() {
        val err = MwaError.MaxPayloadsExceeded(requested = 25, maxAllowed = 10)
        assertEquals(-4021, err.code)
        assertEquals(Recovery.Fatal, err.recovery)
        assertTrue("hint contains requested count", err.hint.contains("25"))
        assertTrue("hint contains max allowed", err.hint.contains("10"))
    }

    @Test
    fun `ChainMismatch hint surfaces both chain ids`() {
        val err = MwaError.ChainMismatch(appChain = "solana:devnet", walletChain = "solana:mainnet")
        assertEquals(-4030, err.code)
        assertEquals(Recovery.VerifyChain, err.recovery)
        assertTrue(err.hint.contains("solana:devnet"))
        assertTrue(err.hint.contains("solana:mainnet"))
    }
}
