package com.selenus.artemis.runtime

import java.security.MessageDigest
import java.util.Base64 as JBase64

/**
 * Artemis Base58 Encoding/Decoding - Solana-optimized implementation.
 * 
 * Features:
 * - Core encode/decode operations
 * - Base58Check encoding with double-SHA256 checksum (Bitcoin-style)
 * - Safe decoding with Result types (no exceptions)
 * - Validation utilities
 * - Base58 ↔ Base64 conversion
 * - Ergonomic extension functions
 * 
 * This is an original implementation designed for Solana SDK operations,
 * providing parity with Solana Mobile SDK and solana-kt while maintaining
 * Artemis's clean, idiomatic Kotlin design philosophy.
 */
object Base58 {
  /** The Base58 alphabet used by Bitcoin/Solana (excludes 0, O, I, l for clarity) */
  const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
  
  /** Lookup table for fast character-to-index mapping */
  private val CHAR_TO_INDEX = IntArray(128) { -1 }.also { arr ->
    for (i in ALPHABET.indices) arr[ALPHABET[i].code] = i
  }
  
  /** Checksum length for Base58Check encoding */
  private const val CHECKSUM_SIZE = 4

  // ═══════════════════════════════════════════════════════════════════════════
  // CORE ENCODING/DECODING
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Encodes a byte array to Base58 string.
   * 
   * Leading zero bytes in input become leading '1' characters in output
   * (preserving data length information).
   * 
   * @param input The bytes to encode
   * @return Base58-encoded string
   */
  fun encode(input: ByteArray): String {
    if (input.isEmpty()) return ""
    
    // Count leading zeros → they become leading '1's
    var leadingZeros = 0
    while (leadingZeros < input.size && input[leadingZeros].toInt() == 0) leadingZeros++

    // Allocate output buffer (worst case: ~137% of input size)
    val encoded = CharArray(input.size * 2)
    var outLen = 0
    
    // Work on a copy to preserve input
    val work = input.copyOf()
    var startAt = leadingZeros
    
    // Convert from base-256 to base-58
    while (startAt < work.size) {
      var carry = 0
      for (i in startAt until work.size) {
        val digit = (work[i].toInt() and 0xFF)
        val combined = carry * 256 + digit
        work[i] = (combined / 58).toByte()
        carry = combined % 58
      }
      encoded[outLen++] = ALPHABET[carry]
      // Skip new leading zeros in work array
      while (startAt < work.size && work[startAt].toInt() == 0) startAt++
    }
    
    // Append leading '1's for original leading zeros
    repeat(leadingZeros) { encoded[outLen++] = ALPHABET[0] }
    
    // Result is in reverse order, so reverse it
    return encoded.copyOfRange(0, outLen).reversed().joinToString("")
  }

  /**
   * Decodes a Base58 string to byte array.
   * 
   * @param input The Base58 string to decode
   * @return Decoded byte array
   * @throws IllegalArgumentException if input contains invalid characters
   */
  fun decode(input: String): ByteArray {
    if (input.isEmpty()) return ByteArray(0)
    
    // Convert characters to base-58 digit values
    val digits = ByteArray(input.length)
    for (i in input.indices) {
      val c = input[i].code
      require(c < 128) { "Non-ASCII character at position $i" }
      val digitValue = CHAR_TO_INDEX[c]
      require(digitValue >= 0) { "Invalid Base58 character '${input[i]}' at position $i" }
      digits[i] = digitValue.toByte()
    }
    
    // Count leading zeros (encoded as '1')
    var leadingOnes = 0
    while (leadingOnes < digits.size && digits[leadingOnes].toInt() == 0) leadingOnes++

    // Allocate output buffer
    val decoded = ByteArray(input.length)
    var outLen = 0
    var startAt = leadingOnes
    
    // Convert from base-58 to base-256
    while (startAt < digits.size) {
      var carry = 0
      for (i in startAt until digits.size) {
        val digit = digits[i].toInt() and 0xFF
        val combined = carry * 58 + digit
        digits[i] = (combined / 256).toByte()
        carry = combined % 256
      }
      decoded[outLen++] = carry.toByte()
      // Skip new leading zeros
      while (startAt < digits.size && digits[startAt].toInt() == 0) startAt++
    }
    
    // Construct result: leading zeros + decoded bytes (reversed)
    val result = ByteArray(leadingOnes + outLen)
    for (i in 0 until outLen) {
      result[leadingOnes + i] = decoded[outLen - 1 - i]
    }
    return result
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // SAFE DECODING (Result-based, no exceptions)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Safely decodes a Base58 string, returning null on invalid input.
   * 
   * Useful when handling untrusted user input without try-catch blocks.
   * 
   * @param input The Base58 string to decode
   * @return Decoded bytes, or null if input is invalid
   */
  fun decodeOrNull(input: String): ByteArray? = runCatching { decode(input) }.getOrNull()

  /**
   * Safely decodes a Base58 string, returning a Result.
   * 
   * Provides detailed error information on failure.
   * 
   * @param input The Base58 string to decode
   * @return Result containing decoded bytes or failure exception
   */
  fun decodeResult(input: String): Result<ByteArray> = runCatching { decode(input) }

  // ═══════════════════════════════════════════════════════════════════════════
  // BASE58CHECK (with double-SHA256 checksum)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Encodes data with a 4-byte checksum (Bitcoin Base58Check format).
   * 
   * The checksum is the first 4 bytes of double-SHA256(data).
   * 
   * @param input Data to encode
   * @return Base58Check-encoded string
   */
  fun encodeCheck(input: ByteArray): String {
    val checksum = doubleSha256(input).copyOfRange(0, CHECKSUM_SIZE)
    return encode(input + checksum)
  }

  /**
   * Decodes a Base58Check string, verifying the checksum.
   * 
   * @param input Base58Check string
   * @return Decoded data (without checksum)
   * @throws IllegalArgumentException if checksum verification fails
   */
  fun decodeCheck(input: String): ByteArray {
    val decoded = decode(input)
    require(decoded.size >= CHECKSUM_SIZE) { "Base58Check data too short" }
    
    val payload = decoded.copyOfRange(0, decoded.size - CHECKSUM_SIZE)
    val checksum = decoded.copyOfRange(decoded.size - CHECKSUM_SIZE, decoded.size)
    val expected = doubleSha256(payload).copyOfRange(0, CHECKSUM_SIZE)
    
    require(checksum.contentEquals(expected)) { "Base58Check checksum mismatch" }
    return payload
  }

  /**
   * Safely decodes a Base58Check string.
   * 
   * @param input Base58Check string
   * @return Decoded data, or null if invalid/checksum fails
   */
  fun decodeCheckOrNull(input: String): ByteArray? = runCatching { decodeCheck(input) }.getOrNull()

  // ═══════════════════════════════════════════════════════════════════════════
  // VALIDATION UTILITIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks if a character is a valid Base58 character.
   */
  fun isValidChar(c: Char): Boolean = c.code < 128 && CHAR_TO_INDEX[c.code] >= 0

  /**
   * Checks if a string is a valid Base58 string.
   * 
   * Note: An empty string is considered valid (encodes empty data).
   */
  fun isValid(input: String): Boolean = input.all { isValidChar(it) }

  /**
   * Validates a string and returns the expected decoded length, or -1 if invalid.
   * 
   * Useful for pre-validation before allocation.
   */
  fun validateAndEstimateLength(input: String): Int {
    if (!isValid(input)) return -1
    // Rough estimate: each base58 char ≈ 0.73 bytes
    return (input.length * 733 / 1000) + 1
  }

  /**
   * Checks if a string is a valid Solana public key (32 bytes when decoded).
   */
  fun isValidSolanaPubkey(input: String): Boolean {
    val decoded = decodeOrNull(input) ?: return false
    return decoded.size == 32
  }

  /**
   * Checks if a string is a valid Solana signature (64 bytes when decoded).
   */
  fun isValidSolanaSignature(input: String): Boolean {
    val decoded = decodeOrNull(input) ?: return false
    return decoded.size == 64
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // BASE58 ↔ BASE64 CONVERSION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Converts a Base64 string to Base58.
   * 
   * Useful when receiving data in Base64 format (common in web APIs)
   * and needing to display/store as Base58 (Solana standard).
   */
  fun fromBase64(base64: String): String = encode(JBase64.getDecoder().decode(base64))

  /**
   * Converts a Base58 string to Base64.
   * 
   * Useful when sending data to systems expecting Base64 format.
   */
  fun toBase64(base58: String): String = JBase64.getEncoder().encodeToString(decode(base58))

  /**
   * Converts a Base64 URL-safe string to Base58.
   */
  fun fromBase64Url(base64Url: String): String = encode(JBase64.getUrlDecoder().decode(base64Url))

  /**
   * Converts a Base58 string to Base64 URL-safe format.
   */
  fun toBase64Url(base58: String): String = JBase64.getUrlEncoder().withoutPadding().encodeToString(decode(base58))

  // ═══════════════════════════════════════════════════════════════════════════
  // INTERNAL UTILITIES
  // ═══════════════════════════════════════════════════════════════════════════

  private fun doubleSha256(data: ByteArray): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256")
    return sha.digest(sha.digest(data))
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS - Ergonomic API for Kotlin users
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Encodes this ByteArray to a Base58 string.
 * 
 * Example: `myBytes.toBase58()`
 */
fun ByteArray.toBase58(): String = Base58.encode(this)

/**
 * Encodes this ByteArray with Base58Check (includes checksum).
 * 
 * Example: `myBytes.toBase58Check()`
 */
fun ByteArray.toBase58Check(): String = Base58.encodeCheck(this)

/**
 * Decodes this Base58 string to a ByteArray.
 * 
 * @throws IllegalArgumentException if string is not valid Base58
 * Example: `"abc123".decodeBase58()`
 */
fun String.decodeBase58(): ByteArray = Base58.decode(this)

/**
 * Safely decodes this Base58 string to a ByteArray.
 * 
 * @return Decoded bytes, or null if invalid
 * Example: `"abc123".decodeBase58OrNull()`
 */
fun String.decodeBase58OrNull(): ByteArray? = Base58.decodeOrNull(this)

/**
 * Decodes this Base58Check string to a ByteArray.
 * 
 * @throws IllegalArgumentException if string is invalid or checksum fails
 * Example: `"abc123xyz".decodeBase58Check()`
 */
fun String.decodeBase58Check(): ByteArray = Base58.decodeCheck(this)

/**
 * Safely decodes this Base58Check string to a ByteArray.
 * 
 * @return Decoded bytes, or null if invalid/checksum fails
 */
fun String.decodeBase58CheckOrNull(): ByteArray? = Base58.decodeCheckOrNull(this)

/**
 * Checks if this string is a valid Base58 string.
 */
fun String.isBase58(): Boolean = Base58.isValid(this)

/**
 * Checks if this string is a valid Solana public key.
 */
fun String.isSolanaPubkey(): Boolean = Base58.isValidSolanaPubkey(this)

/**
 * Checks if this string is a valid Solana signature.
 */
fun String.isSolanaSignature(): Boolean = Base58.isValidSolanaSignature(this)

/**
 * Converts this Base58 string to Base64.
 */
fun String.base58ToBase64(): String = Base58.toBase64(this)

/**
 * Converts this Base64 string to Base58.
 */
fun String.base64ToBase58(): String = Base58.fromBase64(this)
