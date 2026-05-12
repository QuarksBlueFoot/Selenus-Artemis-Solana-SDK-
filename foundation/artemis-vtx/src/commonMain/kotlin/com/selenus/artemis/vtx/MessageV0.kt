package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.CompiledInstruction
import com.selenus.artemis.tx.MessageHeader

data class AddressTableLookup(
  val accountKey: Pubkey,
  val writableIndexes: ByteArray,
  val readonlyIndexes: ByteArray
)

/**
 * Solana v0 message (versioned transaction message).
 *
 * Serialization follows the Versioned Transaction specification:
 * - prefix byte: 0x80 | version (0)
 * - then legacy-like message body (header, static keys, blockhash, instructions)
 * - then address table lookups
 */
data class MessageV0(
  val header: MessageHeader,
  val staticAccountKeys: List<Pubkey>,
  val recentBlockhash: String,
  val instructions: List<CompiledInstruction>,
  val addressTableLookups: List<AddressTableLookup>
) {

  fun serialize(): ByteArray {
    val out = ByteArrayBuilder()

    // prefix
    out.write(0x80)

    // header
    out.write(header.numRequiredSignatures)
    out.write(header.numReadonlySigned)
    out.write(header.numReadonlyUnsigned)

    // static account keys
    out.writeShortVec(staticAccountKeys.size)
    staticAccountKeys.forEach { out.write(it.bytes) }

    // recent blockhash (base58 -> 32 bytes)
    val bh = Base58.decode(recentBlockhash)
    require(bh.size == 32) { "recentBlockhash must decode to 32 bytes" }
    out.write(bh)

    // instructions
    out.writeShortVec(instructions.size)
    instructions.forEach { ix ->
      out.write(ix.programIdIndex)
      out.writeShortVec(ix.accountIndexes.size)
      out.write(ix.accountIndexes)
      out.writeShortVec(ix.data.size)
      out.write(ix.data)
    }

    // address table lookups
    out.writeShortVec(addressTableLookups.size)
    addressTableLookups.forEach { lut ->
      out.write(lut.accountKey.bytes)
      out.writeShortVec(lut.writableIndexes.size)
      out.write(lut.writableIndexes)
      out.writeShortVec(lut.readonlyIndexes.size)
      out.write(lut.readonlyIndexes)
    }

    return out.toByteArray()
  }

  /**
   * Resolve the full ordered account key list used for instruction account indexing:
   * - static keys first
   * - then, for each lookup table, writable keys then readonly keys (in the order referenced)
   */
  fun resolveAccountKeys(lookupTables: Map<Pubkey, AddressLookupTableAccount>): List<Pubkey> {
    val full = staticAccountKeys.toMutableList()
    for (lookup in addressTableLookups) {
      val table = lookupTables[lookup.accountKey]
        ?: error("Missing lookup table account for ${lookup.accountKey.toString()}")
      lookup.writableIndexes.forEach { idx ->
        full.add(table.addresses[idx.toInt() and 0xFF])
      }
      lookup.readonlyIndexes.forEach { idx ->
        full.add(table.addresses[idx.toInt() and 0xFF])
      }
    }
    return full
  }

  companion object {
    fun deserialize(bytes: ByteArray): MessageV0 = deserialize(bytes, 0, bytes.size)

    fun deserialize(bytes: ByteArray, startOffset: Int, length: Int): MessageV0 {
      require(startOffset >= 0 && length >= 0 && startOffset + length <= bytes.size) {
        "MessageV0 range out of bounds"
      }
      var offset = startOffset
      val endOffset = startOffset + length

      // Prefix
      require(offset < endOffset) { "MessageV0 truncated before prefix" }
      val prefix = bytes[offset].toInt() and 0xFF
      val version = prefix and 0x7F
      require(prefix and 0x80 != 0) { "MessageV0 must have high bit set in first byte" }
      require(version == 0) { "Only version 0 is supported" }
      offset += 1

      // Header
      require(offset + 3 <= endOffset) { "MessageV0 truncated in header" }
      val numRequiredSignatures = bytes[offset].toInt() and 0xFF
      val numReadonlySigned = bytes[offset + 1].toInt() and 0xFF
      val numReadonlyUnsigned = bytes[offset + 2].toInt() and 0xFF
      offset += 3
      val header = MessageHeader(numRequiredSignatures, numReadonlySigned, numReadonlyUnsigned)

      // Static Account Keys
      val (numKeys, keysLenBytes) = decodeShortVec(bytes, offset, endOffset)
      offset += keysLenBytes
      val staticAccountKeys = ArrayList<Pubkey>()
      for (i in 0 until numKeys) {
        require(offset + 32 <= endOffset) { "MessageV0 truncated in static account keys" }
        staticAccountKeys.add(Pubkey(bytes.copyOfRange(offset, offset + 32)))
        offset += 32
      }

      // Recent Blockhash
      require(offset + 32 <= endOffset) { "MessageV0 truncated in recent blockhash" }
      val recentBlockhash = Base58.encode(bytes.copyOfRange(offset, offset + 32))
      offset += 32

      // Instructions
      val (numIxs, ixsLenBytes) = decodeShortVec(bytes, offset, endOffset)
      offset += ixsLenBytes
      val instructions = ArrayList<CompiledInstruction>()
      for (i in 0 until numIxs) {
        require(offset < endOffset) { "MessageV0 truncated in instruction header" }
        val programIdIndex = bytes[offset].toInt() and 0xFF
        offset += 1

        val (numAccIdx, accIdxLenBytes) = decodeShortVec(bytes, offset, endOffset)
        offset += accIdxLenBytes
        require(offset + numAccIdx <= endOffset) { "MessageV0 truncated in instruction accounts" }
        val accountIndexes = bytes.copyOfRange(offset, offset + numAccIdx)
        offset += numAccIdx

        val (dataLen, dataLenBytes) = decodeShortVec(bytes, offset, endOffset)
        offset += dataLenBytes
        require(offset + dataLen <= endOffset) { "MessageV0 truncated in instruction data" }
        val data = bytes.copyOfRange(offset, offset + dataLen)
        offset += dataLen

        instructions.add(CompiledInstruction(programIdIndex, accountIndexes, data))
      }

      // Address Table Lookups
      val (numLookups, lookupsLenBytes) = decodeShortVec(bytes, offset, endOffset)
      offset += lookupsLenBytes
      val addressTableLookups = ArrayList<AddressTableLookup>()
      for (i in 0 until numLookups) {
        require(offset + 32 <= endOffset) { "MessageV0 truncated in lookup account key" }
        val accountKey = Pubkey(bytes.copyOfRange(offset, offset + 32))
        offset += 32

        val (numWritable, writableLenBytes) = decodeShortVec(bytes, offset, endOffset)
        offset += writableLenBytes
        require(offset + numWritable <= endOffset) { "MessageV0 truncated in lookup writable indexes" }
        val writableIndexes = bytes.copyOfRange(offset, offset + numWritable)
        offset += numWritable

        val (numReadonly, readonlyLenBytes) = decodeShortVec(bytes, offset, endOffset)
        offset += readonlyLenBytes
        require(offset + numReadonly <= endOffset) { "MessageV0 truncated in lookup readonly indexes" }
        val readonlyIndexes = bytes.copyOfRange(offset, offset + numReadonly)
        offset += numReadonly

        addressTableLookups.add(AddressTableLookup(accountKey, writableIndexes, readonlyIndexes))
      }

      require(offset == endOffset) { "MessageV0 had trailing bytes: ${endOffset - offset}" }

      return MessageV0(header, staticAccountKeys, recentBlockhash, instructions, addressTableLookups)
    }

    private fun decodeShortVec(bytes: ByteArray, offset: Int, endOffset: Int): Pair<Int, Int> {
      var value = 0
      var shift = 0
      var read = 0
      while (true) {
        require(offset + read < endOffset) { "short-vec length truncated at offset ${offset + read}" }
        val byte = bytes[offset + read].toInt() and 0xFF
        read++
        value = value or ((byte and 0x7F) shl shift)
        if ((byte and 0x80) == 0) return value to read
        shift += 7
        require(shift <= 21) { "short-vec length overflow" }
      }
    }
  }
}
