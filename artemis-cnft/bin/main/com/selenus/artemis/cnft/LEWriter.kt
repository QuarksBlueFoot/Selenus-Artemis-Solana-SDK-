package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tiny little-endian writer for Bubblegum/Core instruction data.
 */
internal class LEWriter {
  private val out = ByteArrayOutputStream()

  fun writeU8(v: Int): LEWriter {
    out.write(v and 0xFF)
    return this
  }

  fun writeI32LE(v: Int): LEWriter {
    val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    out.write(bb)
    return this
  }

  fun writeU32LE(v: Long): LEWriter {
    return writeI32LE((v and 0xFFFF_FFFFL).toInt())
  }

  fun writeU64LE(v: Long): LEWriter {
    val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    out.write(bb)
    return this
  }

  fun writeBytes(b: ByteArray): LEWriter {
    out.write(b)
    return this
  }

  fun writePubkey(pk: Pubkey): LEWriter {
    out.write(pk.bytes)
    return this
  }

  /** Borsh-ish string: u32 LE byte length + UTF8 bytes. */
  fun writeString(s: String): LEWriter {
    val b = s.toByteArray(Charsets.UTF_8)
    writeU32LE(b.size.toLong())
    writeBytes(b)
    return this
  }

  fun toByteArray(): ByteArray = out.toByteArray()
}
