package com.selenus.artemis.tx

/**
 * KMP-safe replacement for `java.io.ByteArrayOutputStream` and `java.nio.ByteBuffer`.
 *
 * Growable byte buffer with little-endian integer support for transaction and
 * instruction data serialization. Designed for the hot serialization path so
 * the common case (transaction message build) requires zero auxiliary
 * allocations beyond the backing array itself.
 *
 * Innovations over a naive `ByteArrayOutputStream` wrapper:
 *
 * - [writeShortVec] encodes a Solana compact-u16 length directly into the
 *   buffer with no intermediate `ByteArray`. This is invoked once per account,
 *   instruction, and signature in every transaction serialization and was the
 *   single biggest allocation hotspot in the legacy implementation.
 * - [view] returns a read-only logical slice of the backing buffer that can
 *   be passed to a hash function (SHA-256, Ed25519 message digest) without
 *   copying. Use [toByteArray] when the caller needs an owned, defensive copy.
 * - [reset] keeps the backing array allocated and rewinds the cursor so the
 *   builder can be re-used across multiple sign-and-send operations from a
 *   single thread, avoiding GC churn in tight signing loops.
 */
class ByteArrayBuilder(initialCapacity: Int = 256) {
    private var buffer: ByteArray = ByteArray(initialCapacity)
    private var size: Int = 0

    /** Number of bytes written so far. */
    val length: Int get() = size

    fun write(bytes: ByteArray): ByteArrayBuilder {
        ensureCapacity(size + bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
        return this
    }

    /**
     * Write [length] bytes from [bytes] starting at [offset] into the builder.
     * Avoids allocating an intermediate sub-array when the caller already
     * holds a larger source buffer.
     */
    fun write(bytes: ByteArray, offset: Int, length: Int): ByteArrayBuilder {
        ensureCapacity(size + length)
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
        return this
    }

    fun write(byte: Int): ByteArrayBuilder {
        ensureCapacity(size + 1)
        buffer[size++] = byte.toByte()
        return this
    }

    /** Write a little-endian unsigned 16-bit value. */
    fun putShortLE(value: Int): ByteArrayBuilder {
        ensureCapacity(size + 2)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value shr 8) and 0xFF).toByte()
        return this
    }

    fun putIntLE(value: Int): ByteArrayBuilder {
        ensureCapacity(size + 4)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value shr 8) and 0xFF).toByte()
        buffer[size++] = ((value shr 16) and 0xFF).toByte()
        buffer[size++] = ((value shr 24) and 0xFF).toByte()
        return this
    }

    fun putLongLE(value: Long): ByteArrayBuilder {
        ensureCapacity(size + 8)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value shr 8) and 0xFF).toByte()
        buffer[size++] = ((value shr 16) and 0xFF).toByte()
        buffer[size++] = ((value shr 24) and 0xFF).toByte()
        buffer[size++] = ((value shr 32) and 0xFF).toByte()
        buffer[size++] = ((value shr 40) and 0xFF).toByte()
        buffer[size++] = ((value shr 48) and 0xFF).toByte()
        buffer[size++] = ((value shr 56) and 0xFF).toByte()
        return this
    }

    /**
     * Encode [value] as a Solana compact-u16 (short-vec) length and append it.
     * Performs no auxiliary allocations: the bytes are written straight into
     * the backing buffer.
     */
    fun writeShortVec(value: Int): ByteArrayBuilder {
        ensureCapacity(size + ShortVec.MAX_BYTES)
        size += ShortVec.encodeLenInto(value, buffer, size)
        return this
    }

    /**
     * Return a defensive copy of everything written so far. This is the safe
     * path for callers that need to retain the bytes after the builder is
     * mutated again or returned to a pool.
     */
    fun toByteArray(): ByteArray = buffer.copyOfRange(0, size)

    /**
     * Return a logical view over the bytes written so far without copying.
     *
     * The returned [BytesView] holds a reference to the backing array along
     * with an inclusive offset and exclusive end. It is safe to pass to any
     * read-only consumer (hashing, length checks, base58 encoding) provided
     * the caller does not continue mutating the builder before consuming the
     * view. Mutating the builder after calling [view] invalidates the view.
     */
    fun view(): BytesView = BytesView(buffer, 0, size)

    /**
     * Rewind the cursor and reuse the backing buffer for another build.
     * Capacity is preserved so the next set of writes does not allocate.
     */
    fun reset() {
        size = 0
    }

    private fun ensureCapacity(needed: Int) {
        if (needed > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, needed))
        }
    }
}

/**
 * A read-only window over a slice of a larger byte array.
 *
 * Used by [ByteArrayBuilder.view] to expose serialized bytes for hashing and
 * digesting without copying. Treat it as immutable: the producer may overwrite
 * the underlying buffer at any time after the view is consumed.
 */
class BytesView internal constructor(
    private val backing: ByteArray,
    val offset: Int,
    val length: Int
) {
    /** Byte at [index] within the view's logical range. */
    operator fun get(index: Int): Byte {
        require(index in 0 until length) { "index $index out of range [0, $length)" }
        return backing[offset + index]
    }

    /** Materialize the view into an owned, defensive copy. */
    fun toByteArray(): ByteArray = backing.copyOfRange(offset, offset + length)

    /**
     * Hand the view to [block] as `(backing, offset, length)`. Lets digest and
     * cipher APIs that already accept offset+length triples avoid the copy.
     */
    inline fun <R> use(block: (backing: ByteArray, offset: Int, length: Int) -> R): R =
        block(asBacking(), offset, length)

    @PublishedApi
    internal fun asBacking(): ByteArray = backing
}
