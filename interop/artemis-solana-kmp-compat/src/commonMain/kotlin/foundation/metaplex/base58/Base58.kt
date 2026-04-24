/*
 * Drop-in source compatibility with foundation.metaplex.base58.Base58.
 *
 * Forwards to the Artemis core Base58 implementation so upstream callers that
 * imported from this package re-compile unchanged and inherit the Artemis
 * hardened encoder.
 */
package foundation.metaplex.base58

import com.selenus.artemis.runtime.Base58 as ArtemisBase58

/** solana-kmp compatible `Base58` facade. */
object Base58 {
    /** Encode [bytes] as base58. */
    @JvmStatic
    fun encode(bytes: ByteArray): String = ArtemisBase58.encode(bytes)

    /** Decode [string] as base58 to raw bytes. */
    @JvmStatic
    fun decode(string: String): ByteArray = ArtemisBase58.decode(string)
}

// ---------------------------------------------------------------------------
// Top-level extension functions. Upstream publishes these alongside the object
// helper so `bytes.encodeToBase58String()` and `"<base58>".decodeBase58()`
// compile against `import foundation.metaplex.base58.*`.
// ---------------------------------------------------------------------------

/** Encode [this] as a base58 string. */
fun ByteArray.encodeToBase58String(): String = ArtemisBase58.encode(this)

/**
 * Decode [this] from base58. Throws [NumberFormatException] on malformed input
 * to match upstream contract.
 */
fun String.decodeBase58(): ByteArray = try {
    ArtemisBase58.decode(this)
} catch (t: Throwable) {
    throw NumberFormatException("Invalid base58 string: ${t.message}")
}

/**
 * Base58Check encode: payload + sha256(sha256(payload))[..4] checksum appended,
 * then base58 encoded. Upstream marks the function `suspend` because its crypto
 * is suspend on native targets; the Artemis SHA-256 runs synchronously but the
 * signature is preserved so call-sites that `await` it keep compiling.
 */
suspend fun ByteArray.encodeToBase58WithChecksum(): String {
    val checksum = doubleSha256(this).copyOfRange(0, 4)
    val payload = this + checksum
    return ArtemisBase58.encode(payload)
}

/**
 * Inverse of [encodeToBase58WithChecksum]. Throws [IllegalArgumentException]
 * when the checksum does not match.
 */
suspend fun String.decodeBase58WithChecksum(): ByteArray {
    val full = this.decodeBase58()
    require(full.size >= 4) { "base58 checksum payload too short" }
    val payload = full.copyOfRange(0, full.size - 4)
    val expected = full.copyOfRange(full.size - 4, full.size)
    val actual = doubleSha256(payload).copyOfRange(0, 4)
    require(actual.contentEquals(expected)) { "base58 checksum mismatch" }
    return payload
}

/**
 * Double SHA-256 used by the Base58Check variant. Kept private to this file.
 */
private fun doubleSha256(bytes: ByteArray): ByteArray =
    com.selenus.artemis.runtime.Crypto.sha256(com.selenus.artemis.runtime.Crypto.sha256(bytes))
