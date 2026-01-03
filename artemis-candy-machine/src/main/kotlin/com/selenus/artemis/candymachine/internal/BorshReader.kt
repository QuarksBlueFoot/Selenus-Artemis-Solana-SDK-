package com.selenus.artemis.candymachine.internal

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Borsh reader used by the Candy Machine intelligence layer.
 */
internal class BorshReader(data: ByteArray, offset: Int = 0) {
  private val buf: ByteBuffer = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.LITTLE_ENDIAN)

  fun remaining(): Int = buf.remaining()

  fun u8(): Int = buf.get().toInt() and 0xFF
  fun bool(): Boolean = u8() != 0
  fun u16(): Int = buf.short.toInt() and 0xFFFF
  fun u32(): Long = buf.int.toLong() and 0xFFFFFFFFL
  fun u64(): Long = buf.long

  fun bytes(len: Int): ByteArray {
    val out = ByteArray(len)
    buf.get(out)
    return out
  }

  fun pubkey32(): ByteArray = bytes(32)

  fun string(): String {
    val len = u32().toInt()
    val b = bytes(len)
    return b.toString(Charsets.UTF_8)
  }

  fun <T> option(read: () -> T): T? {
    val tag = u8()
    return if (tag == 0) null else read()
  }

  fun optionPubkey32(): ByteArray? = option { pubkey32() }

  fun <T> vec(readItem: () -> T): List<T> {
    val n = u32().toInt()
    val out = ArrayList<T>(n)
    for (i in 0 until n) out.add(readItem())
    return out
  }
}
