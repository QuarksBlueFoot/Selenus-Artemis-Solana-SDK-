package com.selenus.artemis.candymachine.internal

internal class BorshWriter {
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

  fun u8(value: Int): BorshWriter {
    ensureCapacity(1)
    buf[pos++] = (value and 0xFF).toByte()
    return this
  }

  fun u32(value: Long): BorshWriter {
    ensureCapacity(4)
    val v = value.toInt()
    buf[pos++] = (v and 0xFF).toByte()
    buf[pos++] = ((v ushr 8) and 0xFF).toByte()
    buf[pos++] = ((v ushr 16) and 0xFF).toByte()
    buf[pos++] = ((v ushr 24) and 0xFF).toByte()
    return this
  }

  fun u64(value: Long): BorshWriter {
    ensureCapacity(8)
    for (i in 0 until 8) {
      buf[pos++] = ((value ushr (i * 8)) and 0xFF).toByte()
    }
    return this
  }

  fun bool(value: Boolean): BorshWriter {
    u8(if (value) 1 else 0)
    return this
  }

  fun fixedBytes(bytes: ByteArray): BorshWriter {
    ensureCapacity(bytes.size)
    bytes.copyInto(buf, pos)
    pos += bytes.size
    return this
  }

  fun string(value: String): BorshWriter {
    val bytes = value.encodeToByteArray()
    u32(bytes.size.toLong())
    fixedBytes(bytes)
    return this
  }

  fun <T> option(value: T?, writeValue: BorshWriter.(T) -> Unit): BorshWriter {
    if (value == null) {
      u8(0)
    } else {
      u8(1)
      this.writeValue(value)
    }
    return this
  }

  fun <T> vec(items: List<T>, writeItem: (T) -> Unit): BorshWriter {
    u32(items.size.toLong())
    for (it in items) writeItem(it)
    return this
  }

  fun optionString(value: String?): BorshWriter {
    if (value == null) {
      u8(0)
      return this
    }
    u8(1)
    val bytes = value.encodeToByteArray()
    u32(bytes.size.toLong())
    fixedBytes(bytes)
    return this
  }

  fun optionNone(): BorshWriter {
    u8(0)
    return this
  }

  fun bytes(): ByteArray = buf.copyOfRange(0, pos)
}
