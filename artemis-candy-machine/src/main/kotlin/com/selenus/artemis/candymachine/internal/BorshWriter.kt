package com.selenus.artemis.candymachine.internal

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class BorshWriter {
  private val out = ByteArrayOutputStream()

  fun u8(value: Int): BorshWriter {
    out.write(value and 0xFF)
    return this
  }

  fun u32(value: Long): BorshWriter {
    val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    b.putInt(value.toInt())
    out.write(b.array())
    return this
  }

  fun u64(value: Long): BorshWriter {
    val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
    b.putLong(value)
    out.write(b.array())
    return this
  }

  fun bool(value: Boolean): BorshWriter {
    u8(if (value) 1 else 0)
    return this
  }

  fun fixedBytes(bytes: ByteArray): BorshWriter {
    out.write(bytes)
    return this
  }

  fun string(value: String): BorshWriter {
    val bytes = value.encodeToByteArray()
    u32(bytes.size.toLong())
    out.write(bytes)
    return this
  }

  fun <T> option(value: T?, writeValue: (T) -> Unit): BorshWriter {
    if (value == null) {
      u8(0)
    } else {
      u8(1)
      writeValue(value)
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
    out.write(bytes)
    return this
  }

  fun optionNone(): BorshWriter {
    u8(0)
    return this
  }

  fun bytes(): ByteArray = out.toByteArray()
}
