package com.selenus.artemis.wallet.mwa.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-GCM wire-format guards for [Aes128Gcm].
 *
 * The MWA session cipher composes JCA's AES/GCM/NoPadding with the
 * spec-defined framing `<seq(4)><iv(12)><ciphertext><tag(16)>` and pins the
 * sequence number as AAD. The cases below verify:
 *
 * 1. Independent round-trip with a NIST-style known key.
 * 2. Wire layout (length, seq prefix, IV at offset 4) for a deterministic
 *    plaintext so regressions that flip byte-order are caught.
 * 3. Decrypt cross-check against JCA directly, with the same AAD, so the
 *    wrapper cannot silently diverge from a stock AES-128-GCM tool.
 * 4. Sequence-number tamper rejection.
 * 5. Ciphertext tamper rejection (tag mismatch).
 */
class Aes128GcmTest {

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** NIST test-vector style 128-bit key (all-zero IKM would be ambiguous). */
    private val key = hex("feffe9928665731c6d6a8f9467308308")

    @Test
    fun round_trip_recovers_plaintext() {
        val gcm = Aes128Gcm(key)
        val plaintext = "MWA sign_transactions payload".encodeToByteArray()
        val packet = gcm.encrypt(seq = 7, plaintext = plaintext)
        val decrypted = gcm.decrypt(expectedSeq = 7, packet = packet)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun wire_format_has_expected_layout() {
        val gcm = Aes128Gcm(key)
        val plaintext = ByteArray(32) { it.toByte() }
        val packet = gcm.encrypt(seq = 0x01020304, plaintext = plaintext)
        // 4 (seq) + 12 (iv) + |ct| + 16 (tag) = 4 + 12 + 32 + 16 = 64
        assertTrue(packet.size == 64)
        // Big-endian sequence prefix.
        val prefix = packet.copyOfRange(0, 4)
        assertArrayEquals(hex("01020304"), prefix)
    }

    @Test
    fun decrypt_matches_stock_jca_with_same_aad() {
        val gcm = Aes128Gcm(key)
        val plaintext = "cross-check".encodeToByteArray()
        val packet = gcm.encrypt(seq = 42, plaintext = plaintext)
        val iv = packet.copyOfRange(4, 16)
        val ctWithTag = packet.copyOfRange(16, packet.size)

        val jca = Cipher.getInstance("AES/GCM/NoPadding")
        jca.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        jca.updateAAD(byteArrayOf(0, 0, 0, 42))
        val stock = jca.doFinal(ctWithTag)
        assertArrayEquals(plaintext, stock)
    }

    @Test
    fun decrypt_rejects_wrong_sequence_number() {
        val gcm = Aes128Gcm(key)
        val packet = gcm.encrypt(seq = 1, plaintext = "hi".encodeToByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            gcm.decrypt(expectedSeq = 2, packet = packet)
        }
    }

    @Test
    fun decrypt_rejects_tampered_ciphertext() {
        val gcm = Aes128Gcm(key)
        val packet = gcm.encrypt(seq = 1, plaintext = "hi".encodeToByteArray()).copyOf()
        // Flip one ciphertext byte to force a GCM tag mismatch.
        packet[packet.size - 10] = (packet[packet.size - 10].toInt() xor 0x01).toByte()
        // JCA raises AEADBadTagException which extends GeneralSecurityException.
        var thrown: Throwable? = null
        try {
            gcm.decrypt(expectedSeq = 1, packet = packet)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected decryption to fail on tampered ciphertext", thrown != null)
    }
}
