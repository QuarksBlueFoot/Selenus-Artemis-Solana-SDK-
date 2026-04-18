/*
 * Drop-in source compatibility with org.sol4k.Binary.
 *
 * Upstream ships these as top-level utility functions. Apps pull them in
 * via `import org.sol4k.encodeLength` / `uint32` etc. to hand-roll tx or
 * instruction data without the kotlinx.serialization dependency.
 */
package org.sol4k

/** Little-endian u32 encoding. */
fun uint32(value: Long): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte()
)

/** Little-endian i64 encoding. */
fun int64(value: Long): ByteArray = ByteArray(8).also { buf ->
    for (i in 0 until 8) {
        buf[i] = ((value ushr (i * 8)) and 0xFF).toByte()
    }
}

/** Little-endian u16 encoding. */
fun uint16(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte()
)

/**
 * Short-vec (compact-u16) encoding used for Solana tx message array lengths.
 * Up to 10 output bytes for 32-bit values, continuation bit in the MSB.
 */
fun encodeLength(len: Int): ByteArray {
    require(len >= 0) { "length must be non-negative" }
    val out = ArrayList<Byte>(4)
    var value = len
    while (true) {
        var b = value and 0x7F
        value = value ushr 7
        if (value == 0) {
            out.add(b.toByte())
            break
        }
        b = b or 0x80
        out.add(b.toByte())
    }
    return out.toByteArray()
}

/** Matches upstream `DecodedLength(length, bytes)` return record. */
data class DecodedLength(val length: Int, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedLength) return false
        return length == other.length && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * length + bytes.contentHashCode()
}

/**
 * Inverse of [encodeLength]. Returns the decoded value plus the unconsumed
 * tail of the byte array (matching upstream's decision to split rather than
 * return an offset).
 */
fun decodeLength(bytes: ByteArray): DecodedLength {
    var value = 0
    var shift = 0
    var consumed = 0
    while (consumed < bytes.size) {
        val b = bytes[consumed].toInt() and 0xFF
        consumed++
        value = value or ((b and 0x7F) shl shift)
        if ((b and 0x80) == 0) {
            return DecodedLength(value, bytes.copyOfRange(consumed, bytes.size))
        }
        shift += 7
        require(shift <= 28) { "short-vec length overflow" }
    }
    error("short-vec length truncated")
}
