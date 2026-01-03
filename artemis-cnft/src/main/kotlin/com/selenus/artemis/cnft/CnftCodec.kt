package com.selenus.artemis.cnft

/**
 * Small binary codec helpers for Bubblegum/cNFT payloads.
 *
 * Bubblegum args are generally serialized using Borsh in common clients.
 * This codec implements just what we need:
 * - little-endian integers
 * - fixed byte arrays
 * - borsh strings (u32 len + bytes)
 * - vec<T> (u32 len + elements)
 */
internal object CnftCodec {

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

  fun bytes(b: ByteArray) = b

  fun borshString(s: String): ByteArray {
    val bytes = s.encodeToByteArray()
    return u32le(bytes.size.toLong()) + bytes
  }

  fun vec(items: List<ByteArray>): ByteArray {
    val len = u32le(items.size.toLong())
    val total = items.sumOf { it.size }
    val out = ByteArray(len.size + total)
    System.arraycopy(len, 0, out, 0, len.size)
    var off = len.size
    for (it in items) {
      System.arraycopy(it, 0, out, off, it.size)
      off += it.size
    }
    return out
  }

  fun concat(parts: List<ByteArray>): ByteArray {
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var off = 0
    for (p in parts) {
      System.arraycopy(p, 0, out, off, p.size)
      off += p.size
    }
    return out
  }
}
