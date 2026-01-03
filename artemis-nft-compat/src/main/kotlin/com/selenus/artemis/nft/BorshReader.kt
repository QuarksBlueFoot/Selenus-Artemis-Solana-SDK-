package com.selenus.artemis.nft

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class BorshReader(private val data: ByteArray) {
  private val bb: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

  fun u8(): Int = (bb.get().toInt() and 0xff)
  fun u16(): Int = (bb.short.toInt() and 0xffff)
  fun u32(): Long = (bb.int.toLong() and 0xffffffffL)
  fun u64(): Long = bb.long

  fun bytes(len: Int): ByteArray {
    val out = ByteArray(len)
    bb.get(out)
    return out
  }

  fun string(): String {
    val len = u32().toInt()
    val b = bytes(len)
    return b.toString(Charsets.UTF_8)
  }

  fun optionPubkey32(): ByteArray? {
    val tag = u8()
    return if (tag == 0) null else bytes(32)
  }

  fun bool(): Boolean = u8() != 0

  fun remaining(): Int = bb.remaining()
}
