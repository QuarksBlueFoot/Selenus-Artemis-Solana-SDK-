package com.selenus.artemis.wallet.mwa.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * RFC 5869 HKDF-SHA256 test vectors.
 *
 * Every vector is read straight from RFC 5869 Appendix A. The test locks the
 * Artemis HKDF implementation to the canonical derivation; regressions show
 * up here instead of as silent mismatches in the live MWA handshake.
 */
class HkdfVectorsTest {

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun rfc5869_test_case_1_basic() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865"
        )
        val out = HkdfSha256.derive(ikm = ikm, salt = salt, info = info, length = 42)
        assertArrayEquals(expected, out)
    }

    @Test
    fun rfc5869_test_case_2_longer_inputs() {
        val ikm = hex(
            "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f" +
                "303132333435363738393a3b3c3d3e3f" +
                "404142434445464748494a4b4c4d4e4f"
        )
        val salt = hex(
            "606162636465666768696a6b6c6d6e6f" +
                "707172737475767778797a7b7c7d7e7f" +
                "808182838485868788898a8b8c8d8e8f" +
                "909192939495969798999a9b9c9d9e9f" +
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        )
        val info = hex(
            "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
        )
        val expected = hex(
            "b11e398dc80327a1c8e7f78c596a4934" +
                "4f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09" +
                "da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f" +
                "1d87"
        )
        val out = HkdfSha256.derive(ikm = ikm, salt = salt, info = info, length = 82)
        assertArrayEquals(expected, out)
    }

    @Test
    fun rfc5869_test_case_3_zero_length_salt_and_info() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val expected = hex(
            "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8"
        )
        val out = HkdfSha256.derive(ikm = ikm, salt = ByteArray(0), info = ByteArray(0), length = 42)
        assertArrayEquals(expected, out)
    }
}
