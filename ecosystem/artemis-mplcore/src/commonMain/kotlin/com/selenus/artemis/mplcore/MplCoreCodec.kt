package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Crypto

internal object MplCoreCodec {

  fun disc(method: String): ByteArray {
    val pre = "global:$method".encodeToByteArray()
    val hash = Crypto.sha256(pre)
    return hash.copyOfRange(0, 8)
  }

  fun u8(v: Int) = byteArrayOf((v and 0xff).toByte())

  
  fun u16le(v: Int): ByteArray {
    return byteArrayOf((v and 0xff).toByte(), ((v ushr 8) and 0xff).toByte())
  }

  fun u32le(v: Long): ByteArray {
    val x = v.toInt()
    return byteArrayOf(
      (x and 0xff).toByte(),
      ((x ushr 8) and 0xff).toByte(),
      ((x ushr 16) and 0xff).toByte(),
      ((x ushr 24) and 0xff).toByte()
    )
  }

  fun u64le(v: Long): ByteArray {
    var x = v
    val out = ByteArray(8)
    for (i in 0 until 8) {
      out[i] = (x and 0xff).toByte()
      x = x ushr 8
    }
    return out
  }

  fun borshString(s: String): ByteArray {
    val b = s.encodeToByteArray()
    return u32le(b.size.toLong()) + b
  }

  fun concat(parts: List<ByteArray>): ByteArray {
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var off = 0
    for (p in parts) {
      p.copyInto(out, destinationOffset = off)
      off += p.size
    }
    return out
  }
}
