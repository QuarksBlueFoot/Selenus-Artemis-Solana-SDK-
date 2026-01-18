package com.selenus.artemis.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for Base58 encoding/decoding with Solana SDK parity.
 */
class Base58Test {

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC ENCODE/DECODE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `encode empty array returns empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test
    fun `decode empty string returns empty array`() {
        assertArrayEquals(ByteArray(0), Base58.decode(""))
    }

    @Test
    fun `encode and decode are reversible`() {
        val testCases = listOf(
            byteArrayOf(0),
            byteArrayOf(1),
            byteArrayOf(0, 0, 0, 1),
            "Hello, Solana!".toByteArray(),
            ByteArray(32) { it.toByte() }, // Simulated pubkey
            ByteArray(64) { (it * 2).toByte() } // Simulated signature
        )
        
        for (input in testCases) {
            val encoded = Base58.encode(input)
            val decoded = Base58.decode(encoded)
            assertArrayEquals(input, decoded, "Round-trip failed for ${input.contentToString()}")
        }
    }

    @Test
    fun `leading zeros preserved as leading ones`() {
        val input = byteArrayOf(0, 0, 0, 1, 2, 3)
        val encoded = Base58.encode(input)
        assertTrue(encoded.startsWith("111"), "Expected leading '1's for leading zeros")
        assertArrayEquals(input, Base58.decode(encoded))
    }

    @Test
    fun `known test vectors match expected values`() {
        // Standard Base58 test vectors
        assertEquals("2g", Base58.encode(byteArrayOf(97))) // 'a'
        assertEquals("11111111111111111111111111111111", Base58.encode(ByteArray(32))) // System program
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SOLANA-SPECIFIC TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `system program pubkey round-trip`() {
        val systemProgram = "11111111111111111111111111111111"
        val decoded = Base58.decode(systemProgram)
        assertEquals(32, decoded.size)
        assertTrue(decoded.all { it == 0.toByte() })
        assertEquals(systemProgram, Base58.encode(decoded))
    }

    @Test
    fun `typical solana pubkey validation`() {
        val validPubkeys = listOf(
            "11111111111111111111111111111111", // System program
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", // Token program
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL" // ATA program
        )
        
        for (pubkey in validPubkeys) {
            assertTrue(Base58.isValidSolanaPubkey(pubkey), "Should be valid: $pubkey")
        }
        
        assertFalse(Base58.isValidSolanaPubkey(""))
        assertFalse(Base58.isValidSolanaPubkey("invalid!"))
        assertFalse(Base58.isValidSolanaPubkey("1")) // Too short
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `decode throws on invalid characters`() {
        assertThrows<IllegalArgumentException> { Base58.decode("0OIl") } // Contains invalid chars
        assertThrows<IllegalArgumentException> { Base58.decode("abc!def") } // Contains special char
    }

    @Test
    fun `decodeOrNull returns null on invalid input`() {
        assertNull(Base58.decodeOrNull("invalid!"))
        assertNull(Base58.decodeOrNull("0OIl"))
        assertNotNull(Base58.decodeOrNull("abc123"))
    }

    @Test
    fun `decodeResult contains failure on invalid input`() {
        val result = Base58.decodeResult("invalid!")
        assertTrue(result.isFailure)
        
        val goodResult = Base58.decodeResult("abc123")
        assertTrue(goodResult.isSuccess)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BASE58CHECK TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `encodeCheck and decodeCheck are reversible`() {
        val testData = "Hello, Base58Check!".toByteArray()
        val encoded = Base58.encodeCheck(testData)
        val decoded = Base58.decodeCheck(encoded)
        assertArrayEquals(testData, decoded)
    }

    @Test
    fun `decodeCheck detects tampered data`() {
        val testData = "Sensitive data".toByteArray()
        val encoded = Base58.encodeCheck(testData)
        
        // Tamper with the encoded string
        val tampered = encoded.dropLast(1) + "X"
        
        assertThrows<IllegalArgumentException> { Base58.decodeCheck(tampered) }
        assertNull(Base58.decodeCheckOrNull(tampered))
    }

    @Test
    fun `decodeCheck rejects too-short data`() {
        assertThrows<IllegalArgumentException> { Base58.decodeCheck("11") }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isValid correctly identifies valid strings`() {
        assertTrue(Base58.isValid(""))
        assertTrue(Base58.isValid("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"))
        assertFalse(Base58.isValid("0")) // zero not in alphabet
        assertFalse(Base58.isValid("O")) // uppercase O not in alphabet
        assertFalse(Base58.isValid("I")) // uppercase I not in alphabet
        assertFalse(Base58.isValid("l")) // lowercase L not in alphabet
    }

    @Test
    fun `isValidChar identifies individual characters`() {
        assertTrue(Base58.isValidChar('1'))
        assertTrue(Base58.isValidChar('A'))
        assertTrue(Base58.isValidChar('z'))
        assertFalse(Base58.isValidChar('0'))
        assertFalse(Base58.isValidChar('O'))
        assertFalse(Base58.isValidChar('!'))
    }

    @Test
    fun `isSolanaSignature validates 64-byte signatures`() {
        val sig = ByteArray(64) { it.toByte() }
        val sigBase58 = Base58.encode(sig)
        assertTrue(Base58.isValidSolanaSignature(sigBase58))
        
        val notSig = ByteArray(32) { it.toByte() }
        val notSigBase58 = Base58.encode(notSig)
        assertFalse(Base58.isValidSolanaSignature(notSigBase58))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BASE64 CONVERSION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `base58 to base64 round trip`() {
        val original = ByteArray(32) { (it * 3).toByte() }
        val base58 = Base58.encode(original)
        val base64 = Base58.toBase64(base58)
        val backToBase58 = Base58.fromBase64(base64)
        assertEquals(base58, backToBase58)
    }

    @Test
    fun `base64url conversion works`() {
        val data = ByteArray(32) { 0xFF.toByte() } // Data that produces + and / in standard base64
        val base58 = Base58.encode(data)
        val base64Url = Base58.toBase64Url(base58)
        
        // URL-safe base64 should not contain + / =
        assertFalse(base64Url.contains('+'))
        assertFalse(base64Url.contains('/'))
        assertFalse(base64Url.contains('='))
        
        val backToBase58 = Base58.fromBase64Url(base64Url)
        assertEquals(base58, backToBase58)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXTENSION FUNCTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ByteArray toBase58 extension works`() {
        val data = "test".toByteArray()
        assertEquals(Base58.encode(data), data.toBase58())
    }

    @Test
    fun `String decodeBase58 extension works`() {
        val encoded = Base58.encode("test".toByteArray())
        assertArrayEquals(Base58.decode(encoded), encoded.decodeBase58())
    }

    @Test
    fun `String decodeBase58OrNull extension works`() {
        assertNotNull("abc123".decodeBase58OrNull())
        assertNull("invalid!".decodeBase58OrNull())
    }

    @Test
    fun `String isBase58 extension works`() {
        assertTrue("abc123".isBase58())
        assertFalse("abc!".isBase58())
    }

    @Test
    fun `String base58ToBase64 extension works`() {
        val data = "test".toByteArray()
        val base58 = Base58.encode(data)
        assertEquals(Base58.toBase64(base58), base58.base58ToBase64())
    }

    @Test
    fun `String base64ToBase58 extension works`() {
        val data = "test".toByteArray()
        val base64 = java.util.Base64.getEncoder().encodeToString(data)
        assertEquals(Base58.fromBase64(base64), base64.base64ToBase58())
    }

    @Test
    fun `ByteArray toBase58Check extension works`() {
        val data = "test".toByteArray()
        assertEquals(Base58.encodeCheck(data), data.toBase58Check())
    }

    @Test
    fun `String decodeBase58Check extension works`() {
        val data = "test".toByteArray()
        val encoded = Base58.encodeCheck(data)
        assertArrayEquals(data, encoded.decodeBase58Check())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PERFORMANCE / EDGE CASE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `large data encoding works`() {
        val largeData = ByteArray(1024) { it.toByte() }
        val encoded = Base58.encode(largeData)
        val decoded = Base58.decode(encoded)
        assertArrayEquals(largeData, decoded)
    }

    @Test
    fun `all zero bytes encode correctly`() {
        for (len in 1..10) {
            val zeros = ByteArray(len)
            val encoded = Base58.encode(zeros)
            assertEquals("1".repeat(len), encoded)
            assertArrayEquals(zeros, Base58.decode(encoded))
        }
    }

    @Test
    fun `maximum value bytes encode correctly`() {
        val maxBytes = ByteArray(10) { 0xFF.toByte() }
        val encoded = Base58.encode(maxBytes)
        val decoded = Base58.decode(encoded)
        assertArrayEquals(maxBytes, decoded)
    }
}
