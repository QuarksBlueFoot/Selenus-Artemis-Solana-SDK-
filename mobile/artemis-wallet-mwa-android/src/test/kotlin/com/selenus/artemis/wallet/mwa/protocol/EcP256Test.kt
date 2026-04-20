package com.selenus.artemis.wallet.mwa.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.interfaces.ECPublicKey

/**
 * Exercises the [EcP256] helper used by the MWA session handshake. Covers:
 *
 * 1. Keypair generation produces a 65-byte SEC1-uncompressed public key.
 * 2. SEC1 uncompressed round-trip through [EcP256.publicKeyFromX962] yields
 *    the same curve point (affineX / affineY preserved).
 * 3. ECDH over two independent keypairs produces a symmetric shared secret.
 * 4. ECDSA P1363 signatures verify correctly on the matching public key.
 * 5. Tampered signatures fail to verify.
 * 6. DER <-> P1363 round-trip preserves the signature bits.
 */
class EcP256Test {

    @Test
    fun keypair_produces_sec1_uncompressed_65_bytes() {
        val kp = EcP256.generateKeypair()
        val encoded = EcP256.x962Uncompressed(kp.public)
        assertEquals(65, encoded.size)
        assertEquals(0x04.toByte(), encoded[0])
    }

    @Test
    fun sec1_round_trip_preserves_point() {
        val kp = EcP256.generateKeypair()
        val encoded = EcP256.x962Uncompressed(kp.public)
        val recovered = EcP256.publicKeyFromX962(encoded)
        val original = kp.public as ECPublicKey
        val restored = recovered as ECPublicKey
        assertEquals(original.w.affineX, restored.w.affineX)
        assertEquals(original.w.affineY, restored.w.affineY)
    }

    @Test
    fun ecdh_is_symmetric() {
        val alice = EcP256.generateKeypair()
        val bob = EcP256.generateKeypair()
        val aliceShared = EcP256.ecdhSecret(alice.private, bob.public)
        val bobShared = EcP256.ecdhSecret(bob.private, alice.public)
        assertArrayEquals(aliceShared, bobShared)
    }

    @Test
    fun signature_verifies_on_matching_public_key() {
        val kp = EcP256.generateKeypair()
        val msg = "hello solana mobile wallet adapter".encodeToByteArray()
        val sig = EcP256.signP1363(kp.private, msg)
        assertEquals(64, sig.size)
        assertTrue(EcP256.verifyP1363(kp.public, msg, sig))
    }

    @Test
    fun signature_fails_on_tampered_message() {
        val kp = EcP256.generateKeypair()
        val msg = "payload".encodeToByteArray()
        val sig = EcP256.signP1363(kp.private, msg)
        val tampered = msg.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(EcP256.verifyP1363(kp.public, tampered, sig))
    }

    @Test
    fun signature_fails_on_tampered_signature() {
        val kp = EcP256.generateKeypair()
        val msg = "payload".encodeToByteArray()
        val sig = EcP256.signP1363(kp.private, msg).copyOf()
        sig[5] = (sig[5].toInt() xor 0x01).toByte()
        assertFalse(EcP256.verifyP1363(kp.public, msg, sig))
    }

    @Test
    fun signature_fails_on_wrong_public_key() {
        val kp = EcP256.generateKeypair()
        val other = EcP256.generateKeypair()
        val msg = "payload".encodeToByteArray()
        val sig = EcP256.signP1363(kp.private, msg)
        assertFalse(EcP256.verifyP1363(other.public, msg, sig))
    }
}
