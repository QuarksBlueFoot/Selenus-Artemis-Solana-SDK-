package com.selenus.artemis.runtime

/**
 * Platform cryptography abstraction.
 *
 * Each target (JVM, Android, iOS, etc.) provides actual implementations
 * of these crypto primitives via expect/actual.
 */
internal expect object PlatformCrypto {
    /** SHA-256 digest of the concatenated input parts. */
    fun sha256(vararg parts: ByteArray): ByteArray

    /** HMAC-SHA512 keyed hash. */
    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray

    /** PBKDF2 with HMAC-SHA512 key derivation. */
    fun pbkdf2Sha512(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray

    /** Cryptographically secure random bytes. */
    fun secureRandomBytes(size: Int): ByteArray
}

/**
 * Platform Ed25519 abstraction.
 */
internal expect object PlatformEd25519 {
    /** Derive the 32-byte public key from a 32-byte seed. */
    fun publicKeyFromSeed(seed: ByteArray): ByteArray

    /** Sign a message using a 32-byte seed. Returns 64-byte signature. */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray

    /** Verify an Ed25519 signature. */
    fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean

    /** Check if a 32-byte key is on the Ed25519 curve. */
    fun isOnCurve(publicKey: ByteArray): Boolean
}

/**
 * Platform Base64 abstraction.
 */
expect object PlatformBase64 {
    fun encode(data: ByteArray): String
    fun decode(data: String): ByteArray
    fun urlEncode(data: ByteArray): String
    fun urlDecode(data: String): ByteArray
}

/**
 * Platform time abstraction.
 */
expect fun currentTimeMillis(): Long

/**
 * Platform nanosecond time source (monotonic).
 */
internal expect fun currentNanoTime(): Long
