package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Pubkey

data class MessageHeader(val numRequiredSignatures: Int, val numReadonlySigned: Int, val numReadonlyUnsigned: Int)

data class Message(
  val header: MessageHeader,
  val accountKeys: List<Pubkey>,
  val recentBlockhash: String,
  val instructions: List<CompiledInstruction>
) {
  fun serialize(): ByteArray {
    val out = ByteArrayBuilder(initialCapacity = estimateSize())
    out.write(header.numRequiredSignatures)
    out.write(header.numReadonlySigned)
    out.write(header.numReadonlyUnsigned)
    out.writeShortVec(accountKeys.size)
    for (k in accountKeys) out.write(k.bytes)
    out.write(com.selenus.artemis.runtime.Base58.decode(recentBlockhash))
    out.writeShortVec(instructions.size)
    for (ix in instructions) ix.serializeInto(out)
    return out.toByteArray()
  }

  /**
   * Estimate a reasonable initial capacity so the builder rarely needs to
   * regrow on the hot serialization path. The constants are tuned for typical
   * mobile transactions (a few accounts, one or two instructions).
   */
  private fun estimateSize(): Int {
    var bytes = 3 // header
    bytes += ShortVec.MAX_BYTES + accountKeys.size * 32
    bytes += 32 // recent blockhash
    bytes += ShortVec.MAX_BYTES
    for (ix in instructions) {
      bytes += 1 // programIdIndex
      bytes += ShortVec.MAX_BYTES + ix.accountIndexes.size
      bytes += ShortVec.MAX_BYTES + ix.data.size
    }
    return bytes
  }
}

data class CompiledInstruction(val programIdIndex: Int, val accountIndexes: ByteArray, val data: ByteArray) {
  fun serialize(): ByteArray {
    val out = ByteArrayBuilder(
      initialCapacity = 1 + ShortVec.MAX_BYTES + accountIndexes.size + ShortVec.MAX_BYTES + data.size
    )
    serializeInto(out)
    return out.toByteArray()
  }

  /**
   * Serialize directly into the parent builder. Avoids the intermediate
   * `ByteArray` that the message-level serializer would otherwise allocate
   * and copy back through [ByteArrayBuilder.write].
   */
  internal fun serializeInto(out: ByteArrayBuilder) {
    out.write(programIdIndex)
    out.writeShortVec(accountIndexes.size)
    out.write(accountIndexes)
    out.writeShortVec(data.size)
    out.write(data)
  }
}
