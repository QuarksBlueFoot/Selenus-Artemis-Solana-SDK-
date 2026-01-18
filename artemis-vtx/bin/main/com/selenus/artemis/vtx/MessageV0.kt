package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.CompiledInstruction
import com.selenus.artemis.tx.MessageHeader
import com.selenus.artemis.tx.ShortVec
import java.io.ByteArrayOutputStream

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
    val out = ByteArrayOutputStream()

    // prefix
    out.write(byteArrayOf((0x80).toByte()))

    // header
    out.write(byteArrayOf(
      header.numRequiredSignatures.toByte(),
      header.numReadonlySigned.toByte(),
      header.numReadonlyUnsigned.toByte()
    ))

    // static account keys
    out.write(ShortVec.encodeLen(staticAccountKeys.size))
    staticAccountKeys.forEach { out.write(it.bytes) }

    // recent blockhash (base58 -> 32 bytes)
    val bh = Base58.decode(recentBlockhash)
    require(bh.size == 32) { "recentBlockhash must decode to 32 bytes" }
    out.write(bh)

    // instructions
    out.write(ShortVec.encodeLen(instructions.size))
    instructions.forEach { ix ->
      out.write(byteArrayOf(ix.programIdIndex.toByte()))
      out.write(ShortVec.encodeLen(ix.accountIndexes.size))
      out.write(ix.accountIndexes)
      out.write(ShortVec.encodeLen(ix.data.size))
      out.write(ix.data)
    }

    // address table lookups
    out.write(ShortVec.encodeLen(addressTableLookups.size))
    addressTableLookups.forEach { lut ->
      out.write(lut.accountKey.bytes)
      out.write(ShortVec.encodeLen(lut.writableIndexes.size))
      out.write(lut.writableIndexes)
      out.write(ShortVec.encodeLen(lut.readonlyIndexes.size))
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
    fun deserialize(bytes: ByteArray): MessageV0 {
      var offset = 0

      // Prefix
      val prefix = bytes[offset].toInt() and 0xFF
      val version = prefix and 0x7F
      require(prefix and 0x80 != 0) { "MessageV0 must have high bit set in first byte" }
      require(version == 0) { "Only version 0 is supported" }
      offset += 1

      // Header
      val numRequiredSignatures = bytes[offset].toInt() and 0xFF
      val numReadonlySigned = bytes[offset + 1].toInt() and 0xFF
      val numReadonlyUnsigned = bytes[offset + 2].toInt() and 0xFF
      offset += 3
      val header = MessageHeader(numRequiredSignatures, numReadonlySigned, numReadonlyUnsigned)

      // Static Account Keys
      val (numKeys, keysLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += keysLenBytes
      val staticAccountKeys = ArrayList<Pubkey>()
      for (i in 0 until numKeys) {
        staticAccountKeys.add(Pubkey(bytes.copyOfRange(offset, offset + 32)))
        offset += 32
      }

      // Recent Blockhash
      val recentBlockhash = Base58.encode(bytes.copyOfRange(offset, offset + 32))
      offset += 32

      // Instructions
      val (numIxs, ixsLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += ixsLenBytes
      val instructions = ArrayList<CompiledInstruction>()
      for (i in 0 until numIxs) {
        val programIdIndex = bytes[offset].toInt() and 0xFF
        offset += 1

        val (numAccIdx, accIdxLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += accIdxLenBytes
        val accountIndexes = bytes.copyOfRange(offset, offset + numAccIdx)
        offset += numAccIdx

        val (dataLen, dataLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += dataLenBytes
        val data = bytes.copyOfRange(offset, offset + dataLen)
        offset += dataLen

        instructions.add(CompiledInstruction(programIdIndex, accountIndexes, data))
      }

      // Address Table Lookups
      val (numLookups, lookupsLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
      offset += lookupsLenBytes
      val addressTableLookups = ArrayList<AddressTableLookup>()
      for (i in 0 until numLookups) {
        val accountKey = Pubkey(bytes.copyOfRange(offset, offset + 32))
        offset += 32

        val (numWritable, writableLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += writableLenBytes
        val writableIndexes = bytes.copyOfRange(offset, offset + numWritable)
        offset += numWritable

        val (numReadonly, readonlyLenBytes) = ShortVec.decodeLen(bytes.copyOfRange(offset, bytes.size))
        offset += readonlyLenBytes
        val readonlyIndexes = bytes.copyOfRange(offset, offset + numReadonly)
        offset += numReadonly

        addressTableLookups.add(AddressTableLookup(accountKey, writableIndexes, readonlyIndexes))
      }

      return MessageV0(header, staticAccountKeys, recentBlockhash, instructions, addressTableLookups)
    }
  }
}
