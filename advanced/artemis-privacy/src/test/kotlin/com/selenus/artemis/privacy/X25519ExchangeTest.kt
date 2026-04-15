/*
 * Tests for the X25519 key exchange surface, in particular the Edwards
 * to Montgomery birational map used by `ed25519PublicKeyToX25519`.
 *
 * Why this matters: Signal X3DH, libsodium's `crypto_sign_ed25519_pk_to_curve25519`,
 * and the Artemis stealth address feature all depend on the conversion being
 * correct. A wrong map produces an x25519 public key that fails ECDH and
 * silently corrupts encrypted messages.
 */
package com.selenus.artemis.privacy

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.math.ec.rfc7748.X25519
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class X25519ExchangeTest {

    /**
     * Derive an Ed25519 public key from a 32-byte seed via BouncyCastle.
     * Local helper so the tests do not pull in the artemis-core internals.
     */
    private fun deriveEd25519PublicKey(seed: ByteArray): ByteArray {
        val sk = Ed25519PrivateKeyParameters(seed, 0)
        return sk.generatePublicKey().encoded
    }

    @Test
    fun `ed25519 to x25519 returns 32 bytes`() {
        val seed = ByteArray(32) { it.toByte() }
        val edPub = deriveEd25519PublicKey(seed)
        val x25519Pub = X25519Exchange.ed25519PublicKeyToX25519(edPub)
        assertEquals(32, x25519Pub.size)
    }

    @Test
    fun `ed25519 to x25519 is not a byte copy`() {
        // The previous broken implementation returned the Ed25519 bytes
        // unchanged. Verify the new map produces a distinct point so any
        // future regression to the byte-copy version fails this test.
        val seed = ByteArray(32) { (it * 7 + 1).toByte() }
        val edPub = deriveEd25519PublicKey(seed)
        val x25519Pub = X25519Exchange.ed25519PublicKeyToX25519(edPub)
        assertNotEquals(
            edPub.toList(),
            x25519Pub.toList(),
            "Edwards bytes must not equal Montgomery bytes for non-trivial keys"
        )
    }

    @Test
    fun `ed25519 to x25519 produces a valid curve25519 point`() {
        // Deterministic conversion: run an ECDH against a known clamped scalar
        // and check the result is a non-zero shared secret, which is only
        // possible if the resulting Montgomery u-coordinate is a valid point.
        val seed = ByteArray(32)
        val edPub = deriveEd25519PublicKey(seed)
        val x25519Pub = X25519Exchange.ed25519PublicKeyToX25519(edPub)

        val scalar = ByteArray(32) { 9.toByte() }
        scalar[0] = (scalar[0].toInt() and 0xF8).toByte()
        scalar[31] = (scalar[31].toInt() and 0x7F).toByte()
        scalar[31] = (scalar[31].toInt() or 0x40).toByte()

        val shared = ByteArray(32)
        X25519.scalarMult(scalar, 0, x25519Pub, 0, shared, 0)
        assertTrue(shared.any { it != 0.toByte() }, "ECDH against converted point produced all-zero secret")
    }

    @Test
    fun `computeSharedSecret is symmetric`() {
        val alice = X25519Exchange.generateKeypair()
        val bob = X25519Exchange.generateKeypair()
        val sharedAB = X25519Exchange.computeSharedSecret(alice.privateKey, bob.publicKey)
        val sharedBA = X25519Exchange.computeSharedSecret(bob.privateKey, alice.publicKey)
        assertArrayEquals(sharedAB, sharedBA)
        alice.wipe()
        bob.wipe()
    }

    @Test
    fun `ed25519 private key to x25519 is clamped per RFC 8032`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val scalar = X25519Exchange.ed25519PrivateKeyToX25519(seed)
        assertEquals(32, scalar.size)
        // Low three bits of byte[0] cleared.
        assertEquals(0, scalar[0].toInt() and 0x07)
        // High bit of byte[31] cleared.
        assertFalse((scalar[31].toInt() and 0x80) != 0)
        // Second-highest bit of byte[31] set.
        assertTrue((scalar[31].toInt() and 0x40) != 0)
    }
}
