package com.selenus.artemis.wallet.mwa.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * X25519 ECDH conformance for the MWA session transport layer.
 *
 * Previously lived under the Seed Vault module, which muddled the
 * custody/transport boundary. These tests belong here because the code under
 * test [MwaSessionCrypto] is a transport primitive, not a custody one.
 */
class MwaSessionCryptoTest {

    @Test
    fun `X25519 ECDH is symmetric`() {
        // Real Diffie-Hellman: alice computes dh(aPriv, bPub), bob computes
        // dh(bPriv, aPub); both must land on the same session key.
        val (aPriv, aPub) = MwaSessionCrypto.generateX25519Keypair()
        val (bPriv, bPub) = MwaSessionCrypto.generateX25519Keypair()

        val aliceKey = MwaSessionCrypto.deriveX25519SharedSecret(aPriv, bPub, context = "test")
        val bobKey = MwaSessionCrypto.deriveX25519SharedSecret(bPriv, aPub, context = "test")

        assertArrayEquals(aliceKey, bobKey)
        assertEquals(32, aliceKey.size)
    }

    @Test
    fun `X25519 ECDH context separation`() {
        val (aPriv, aPub) = MwaSessionCrypto.generateX25519Keypair()
        val (_, bPub) = MwaSessionCrypto.generateX25519Keypair()

        val keyA = MwaSessionCrypto.deriveX25519SharedSecret(aPriv, bPub, context = "ctx-a")
        val keyB = MwaSessionCrypto.deriveX25519SharedSecret(aPriv, bPub, context = "ctx-b")

        assertFalse(keyA.contentEquals(keyB))
    }

    @Test
    fun `generateX25519Keypair produces 32-byte keys`() {
        val (priv, pub) = MwaSessionCrypto.generateX25519Keypair()
        assertEquals(32, priv.size)
        assertEquals(32, pub.size)
    }

    @Test
    fun `X25519 rejects malformed inputs`() {
        val (priv, pub) = MwaSessionCrypto.generateX25519Keypair()
        // Wrong-size private scalar
        try {
            MwaSessionCrypto.deriveX25519SharedSecret(ByteArray(16), pub)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
        // Wrong-size peer public
        try {
            MwaSessionCrypto.deriveX25519SharedSecret(priv, ByteArray(16))
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }
}
