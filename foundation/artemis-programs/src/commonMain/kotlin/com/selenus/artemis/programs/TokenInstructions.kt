package com.selenus.artemis.programs

import com.selenus.artemis.tx.ByteArrayBuilder

internal object TokenInstructions {

  fun u8(i: Int): ByteArray = byteArrayOf((i and 0xff).toByte())

  fun u64LE(value: Long): ByteArray {
    return ByteArrayBuilder(8).putLongLE(value).toByteArray()
  }

  fun boolOptionPubkey(option: Boolean, pubkey32: ByteArray): ByteArray {
    val out = ByteArray(1 + 32)
    out[0] = if (option) 1 else 0
    if (option) {
      require(pubkey32.size == 32) { "pubkey must be 32 bytes" }
      pubkey32.copyInto(out, 1)
    }
    return out
  }

  fun pubkeyOption(pubkey32: ByteArray?): ByteArray {
    if (pubkey32 == null) return byteArrayOf(0)
    require(pubkey32.size == 32) { "pubkey must be 32 bytes" }
    val out = ByteArray(1 + 32)
    out[0] = 1
    pubkey32.copyInto(out, 1)
    return out
  }
}
