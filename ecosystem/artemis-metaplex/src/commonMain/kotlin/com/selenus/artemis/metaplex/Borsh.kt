package com.selenus.artemis.metaplex

class BorshReader(private val data: ByteArray) {
  private var pos = 0

  fun u8(): Int = data[pos++].toInt() and 0xFF

  fun u32(): Long {
    val v = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
    pos += 4
    return v.toLong() and 0xFFFFFFFFL
  }

  fun bool(): Boolean = u8() != 0

  fun bytes(len: Int): ByteArray {
    val out = data.copyOfRange(pos, pos + len)
    pos += len
    return out
  }

  fun pubkey32(): ByteArray = bytes(32)

  fun string(): String {
    val len = u32().toInt()
    val s = data.decodeToString(pos, pos + len)
    pos += len
    return s
  }

  fun optionPubkey(): ByteArray? {
    val tag = u8()
    return if (tag == 0) null else pubkey32()
  }
}
