package com.selenus.artemis.programs

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object TokenInstructions {

  fun u8(i: Int): ByteArray = byteArrayOf((i and 0xff).toByte())

  fun u64LE(value: Long): ByteArray {
    val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
    bb.putLong(value)
    return bb.array()
  }

  fun boolOptionPubkey(option: Boolean, pubkey32: ByteArray): ByteArray {
    val out = ByteArray(1 + 32)
    out[0] = if (option) 1 else 0
    if (option) {
      require(pubkey32.size == 32) { "pubkey must be 32 bytes" }
      System.arraycopy(pubkey32, 0, out, 1, 32)
    }
    return out
  }
}
