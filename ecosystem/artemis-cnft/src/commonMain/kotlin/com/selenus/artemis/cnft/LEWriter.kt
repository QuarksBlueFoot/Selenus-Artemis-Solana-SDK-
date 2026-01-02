package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey

/**
 * Tiny little-endian writer for Bubblegum/Core instruction data.
 */
internal class LEWriter {
  private var buf = ByteArray(256)
  private var pos = 0

  private fun ensureCapacity(needed: Int) {
    if (pos + needed > buf.size) {
      val newSize = maxOf(buf.size * 2, pos + needed)
      val newBuf = ByteArray(newSize)
      buf.copyInto(newBuf, 0, 0, pos)
      buf = newBuf
    }
  }

  fun writeU8(v: Int): LEWriter {
    ensureCapacity(1)
    buf[pos++] = (v and 0xFF).toByte()
    return this
  }

  fun writeI32LE(v: Int): LEWriter {
    ensureCapacity(4)
    buf[pos++] = (v and 0xFF).toByte()
    buf[pos++] = ((v ushr 8) and 0xFF).toByte()
    buf[pos++] = ((v ushr 16) and 0xFF).toByte()
    buf[pos++] = ((v ushr 24) and 0xFF).toByte()
    return this
  }

  fun writeU32LE(v: Long): LEWriter {
    return writeI32LE((v and 0xFFFF_FFFFL).toInt())
  }

  fun writeU64LE(v: Long): LEWriter {
    ensureCapacity(8)
    for (i in 0 until 8) {
      buf[pos++] = ((v ushr (i * 8)) and 0xFF).toByte()
    }
    return this
  }

  fun writeBytes(b: ByteArray): LEWriter {
    ensureCapacity(b.size)
    b.copyInto(buf, pos)
    pos += b.size
    return this
  }

  fun writePubkey(pk: Pubkey): LEWriter {
    return writeBytes(pk.bytes)
  }

  /** Borsh-ish string: u32 LE byte length + UTF8 bytes. */
  fun writeString(s: String): LEWriter {
    val b = s.encodeToByteArray()
    writeU32LE(b.size.toLong())
    writeBytes(b)
    return this
  }

  fun toByteArray(): ByteArray = buf.copyOfRange(0, pos)
}
