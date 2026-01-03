package com.selenus.artemis.metaplex

import java.nio.ByteBuffer
import java.nio.ByteOrder

class BorshReader(private val buf: ByteBuffer) {
  init { buf.order(ByteOrder.LITTLE_ENDIAN) }

  fun u8(): Int = buf.get().toInt() and 0xFF
  fun u32(): Long = buf.int.toLong() and 0xFFFFFFFFL
  fun bool(): Boolean = u8() != 0

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

  fun optionPubkey(): ByteArray? {
    val tag = u8()
    return if (tag == 0) null else pubkey32()
  }
}
