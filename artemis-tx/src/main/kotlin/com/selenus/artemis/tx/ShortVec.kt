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
}
