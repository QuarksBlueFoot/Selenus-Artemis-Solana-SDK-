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
}
