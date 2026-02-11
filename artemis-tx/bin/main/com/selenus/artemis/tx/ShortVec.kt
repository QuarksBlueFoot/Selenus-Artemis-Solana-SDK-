package com.selenus.artemis.tx

object ShortVec {
  fun encodeLen(len: Int): ByteArray {
    var rem = len
    val out = ArrayList<Byte>()
    while (true) {
      var elem = rem and 0x7f
      rem = rem ushr 7
      if (rem == 0) {
        out.add(elem.toByte())
        break
      } else {
        elem = elem or 0x80
        out.add(elem.toByte())
      }
    }
    return out.toByteArray()
  }

  fun decodeLen(bytes: ByteArray): Pair<Int, Int> = decodeLen(bytes, 0)

  /**
   * Decode a ShortVec length starting at the given offset.
   * 
   * @param bytes The byte array
   * @param offset The offset to start decoding from
   * @return Pair of (decoded length, number of bytes consumed)
   */
  fun decodeLen(bytes: ByteArray, offset: Int): Pair<Int, Int> {
    var len = 0
    var size = 0
    var pos = offset
    while (pos < bytes.size) {
      size++
      val elem = bytes[pos].toInt() and 0xFF
      pos++
      len = len or ((elem and 0x7F) shl ((size - 1) * 7))
      if ((elem and 0x80) == 0) {
        break
      }
    }
    return Pair(len, size)
  }
}
