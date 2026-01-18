package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey
import java.io.ByteArrayOutputStream

data class MessageHeader(val numRequiredSignatures: Int, val numReadonlySigned: Int, val numReadonlyUnsigned: Int)

data class Message(
  val header: MessageHeader,
  val accountKeys: List<Pubkey>,
  val recentBlockhash: String,
  val instructions: List<CompiledInstruction>
) {
  fun serialize(): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(
      header.numRequiredSignatures.toByte(),
      header.numReadonlySigned.toByte(),
      header.numReadonlyUnsigned.toByte()
    ))
    out.write(ShortVec.encodeLen(accountKeys.size))
    for (k in accountKeys) out.write(k.bytes)
    out.write(com.selenus.artemis.runtime.Base58.decode(recentBlockhash))
    out.write(ShortVec.encodeLen(instructions.size))
    for (ix in instructions) out.write(ix.serialize())
    // out.write(ShortVec.encodeLen(0)) // address table lookups = 0 for legacy
    return out.toByteArray()
  }
}

data class CompiledInstruction(val programIdIndex: Int, val accountIndexes: ByteArray, val data: ByteArray) {
  fun serialize(): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(programIdIndex)
    out.write(ShortVec.encodeLen(accountIndexes.size))
    out.write(accountIndexes)
    out.write(ShortVec.encodeLen(data.size))
    out.write(data)
    return out.toByteArray()
  }
}
