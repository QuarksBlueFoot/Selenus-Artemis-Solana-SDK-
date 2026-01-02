package com.selenus.artemis.tx

/**
 * KMP-safe replacement for java.io.ByteArrayOutputStream and java.nio.ByteBuffer.
 * Growable byte buffer with little-endian integer support for transaction and
 * instruction data serialization.
 */
class ByteArrayBuilder(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    fun write(bytes: ByteArray): ByteArrayBuilder {
        ensureCapacity(size + bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
        return this
    }

    fun write(byte: Int): ByteArrayBuilder {
        ensureCapacity(size + 1)
        buffer[size++] = byte.toByte()
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

    private fun ensureCapacity(needed: Int) {
        if (needed > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, needed))
        }
    }

    fun toByteArray() = buffer.copyOfRange(0, size)
}
