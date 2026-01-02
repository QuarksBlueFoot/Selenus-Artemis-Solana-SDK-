package com.selenus.artemis.nft

internal class BorshReader(private val data: ByteArray) {
  private var pos: Int = 0

  fun u8(): Int {
    return (data[pos++].toInt() and 0xff)
  }

  fun u16(): Int {
    val v = (data[pos].toInt() and 0xff) or
            ((data[pos + 1].toInt() and 0xff) shl 8)
    pos += 2
    return v
  }

  fun u32(): Long {
    val v = (data[pos].toLong() and 0xff) or
            ((data[pos + 1].toLong() and 0xff) shl 8) or
            ((data[pos + 2].toLong() and 0xff) shl 16) or
            ((data[pos + 3].toLong() and 0xff) shl 24)
    pos += 4
    return v
  }

  fun u64(): Long {
    var v = 0L
    for (i in 0 until 8) {
      v = v or ((data[pos + i].toLong() and 0xff) shl (i * 8))
    }
    pos += 8
    return v
  }

  fun bytes(len: Int): ByteArray {
    val out = data.copyOfRange(pos, pos + len)
    pos += len
    return out
  }

  fun string(): String {
    val len = u32().toInt()
    val b = bytes(len)
    return b.decodeToString()
  }

  fun optionPubkey32(): ByteArray? {
    val tag = u8()
    return if (tag == 0) null else bytes(32)
  }

  fun bool(): Boolean = u8() != 0

  fun remaining(): Int = data.size - pos
}
