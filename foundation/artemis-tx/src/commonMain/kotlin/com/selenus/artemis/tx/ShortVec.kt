package com.selenus.artemis.tx

/**
 * Solana compact-u16 (short-vec) length codec.
 *
 * Solana on-chain wire format prefixes every variable-length sequence with a
 * compact-u16 length: 1 to 3 bytes, with a continuation flag in the high bit.
 * This object exposes the canonical encode and decode helpers used by every
 * transaction serializer in Artemis.
 *
 * Performance note: the encoder allocates a single 3-byte stack buffer and
 * trims it on return rather than building an `ArrayList<Byte>` and copying.
 * A zero-allocation variant lives on [ByteArrayBuilder.writeShortVec] for hot
 * paths that already own a builder.
 */
object ShortVec {

    /** Maximum number of bytes a compact-u16 length can occupy. */
    const val MAX_BYTES: Int = 3

    fun encodeLen(len: Int): ByteArray {
        val temp = ByteArray(MAX_BYTES)
        val written = encodeLenInto(len, temp, 0)
        return if (written == temp.size) temp else temp.copyOf(written)
    }

    /**
     * Encode [len] as a compact-u16 directly into [destination] starting at
     * [offset]. Returns the number of bytes written. The caller is responsible
     * for ensuring [destination] has at least [MAX_BYTES] bytes available.
     */
    fun encodeLenInto(len: Int, destination: ByteArray, offset: Int): Int {
        var rem = len
        var pos = offset
        while (true) {
            val elem = rem and 0x7F
            rem = rem ushr 7
            if (rem == 0) {
                destination[pos++] = elem.toByte()
                break
            }
            destination[pos++] = (elem or 0x80).toByte()
        }
        return pos - offset
    }

    fun decodeLen(bytes: ByteArray): Pair<Int, Int> = decodeLen(bytes, 0)

    /**
     * Decode a ShortVec length starting at the given offset.
     *
     * @param bytes The byte array
     * @param offset The offset to start decoding from
     * @return Pair of (decoded length, number of bytes consumed)
     */
    fun decodeLen(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        var len = 0
        var size = 0
        var pos = offset
        while (pos < bytes.size) {
            size++
            val elem = bytes[pos].toInt() and 0xFF
            pos++
            len = len or ((elem and 0x7F) shl ((size - 1) * 7))
            if ((elem and 0x80) == 0) {
                break
            }
        }
        return Pair(len, size)
    }
}
