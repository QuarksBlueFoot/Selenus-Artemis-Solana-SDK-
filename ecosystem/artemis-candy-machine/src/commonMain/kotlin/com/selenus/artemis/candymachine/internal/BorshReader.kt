package com.selenus.artemis.candymachine.internal

/**
 * Minimal Borsh reader used by the Candy Machine intelligence layer.
 */
internal class BorshReader(private val data: ByteArray, private var pos: Int = 0) {

  fun remaining(): Int = data.size - pos

  fun u8(): Int {
    return data[pos++].toInt() and 0xFF
  }

  fun bool(): Boolean = u8() != 0

  fun u16(): Int {
    val result = (data[pos].toInt() and 0xFF) or
                 ((data[pos + 1].toInt() and 0xFF) shl 8)
    pos += 2
    return result
  }

  fun u32(): Long {
    val result = (data[pos].toLong() and 0xFF) or
                 ((data[pos + 1].toLong() and 0xFF) shl 8) or
                 ((data[pos + 2].toLong() and 0xFF) shl 16) or
                 ((data[pos + 3].toLong() and 0xFF) shl 24)
    pos += 4
    return result
  }

  fun u64(): Long {
    var result = 0L
    for (i in 0 until 8) {
      result = result or ((data[pos + i].toLong() and 0xFF) shl (i * 8))
    }
    pos += 8
    return result
  }

  fun bytes(len: Int): ByteArray {
    val out = data.copyOfRange(pos, pos + len)
    pos += len
    return out
  }

  fun pubkey32(): ByteArray = bytes(32)

  fun string(): String {
    val len = u32().toInt()
    val result = data.decodeToString(pos, pos + len)
    pos += len
    return result
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
