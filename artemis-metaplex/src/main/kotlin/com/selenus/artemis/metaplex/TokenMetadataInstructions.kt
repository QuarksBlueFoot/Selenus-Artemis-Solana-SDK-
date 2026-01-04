package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object TokenMetadataInstructions {
  val PROGRAM_ID = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")

  data class DataV2(
    val name: String,
    val symbol: String,
    val uri: String,
    val sellerFeeBasisPoints: Int,
    val creators: List<Creator>? = null,
    val collection: Collection? = null,
    val uses: Uses? = null
  )

  data class Creator(val address: Pubkey, val verified: Boolean, val share: Int)
  data class Collection(val verified: Boolean, val key: Pubkey)
  data class Uses(val useMethod: Int, val remaining: Long, val total: Long)

  private fun serializeDataV2(data: DataV2): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    
    fun writeString(s: String) {
      val b = s.toByteArray(StandardCharsets.UTF_8)
      out.write(intToBytesLE(b.size))
      out.write(b)
    }

    writeString(data.name)
    writeString(data.symbol)
    writeString(data.uri)
    out.write(shortToBytesLE(data.sellerFeeBasisPoints))

    if (data.creators != null) {
      out.write(1)
      out.write(intToBytesLE(data.creators.size))
      for (c in data.creators) {
        out.write(c.address.bytes)
        out.write(if (c.verified) 1 else 0)
        out.write(c.share)
      }
    } else {
      out.write(0)
    }

    if (data.collection != null) {
      out.write(1)
      out.write(if (data.collection.verified) 1 else 0)
      out.write(data.collection.key.bytes)
    } else {
      out.write(0)
    }

    if (data.uses != null) {
      out.write(1)
      out.write(data.uses.useMethod)
      out.write(longToBytesLE(data.uses.remaining))
      out.write(longToBytesLE(data.uses.total))
    } else {
      out.write(0)
    }

    return out.toByteArray()
  }

  /**
   * Create Metadata Account V3
   */
  fun createMetadataAccountV3(
    metadata: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    payer: Pubkey,
    updateAuthority: Pubkey,
    data: DataV2,
    isMutable: Boolean = true,
    collectionDetails: Boolean = false // simplified for now
  ): Instruction {
    val body = java.io.ByteArrayOutputStream()
    body.write(33) // Instruction: CreateMetadataAccountV3
    body.write(serializeDataV2(data))
    body.write(if (isMutable) 1 else 0)
    body.write(if (collectionDetails) 1 else 0) // Collection Details (simplified)

    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(metadata, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(mintAuthority, isSigner = true, isWritable = false),
        AccountMeta(payer, isSigner = true, isWritable = true),
        AccountMeta(updateAuthority, isSigner = false, isWritable = false),
        AccountMeta(Pubkey.fromBase58("11111111111111111111111111111111"), isSigner = false, isWritable = false), // System Program
        AccountMeta(Pubkey.fromBase58("SysvarRent111111111111111111111111111111111"), isSigner = false, isWritable = false) // Rent
      ),
      data = body.toByteArray()
    )
  }

  /**
   * Update Metadata Account V2
   */
  fun updateMetadataAccountV2(
    metadata: Pubkey,
    updateAuthority: Pubkey,
    data: DataV2? = null,
    newUpdateAuthority: Pubkey? = null,
    primarySaleHappened: Boolean? = null,
    isMutable: Boolean? = null
  ): Instruction {
    val body = java.io.ByteArrayOutputStream()
    body.write(15) // Instruction: UpdateMetadataAccountV2
    
    // Data
    if (data != null) {
      body.write(1)
      body.write(serializeDataV2(data))
    } else {
      body.write(0)
    }

    // New Update Authority
    if (newUpdateAuthority != null) {
      body.write(1)
      body.write(newUpdateAuthority.bytes)
    } else {
      body.write(0)
    }

    // Primary Sale Happened
    if (primarySaleHappened != null) {
      body.write(1)
      body.write(if (primarySaleHappened) 1 else 0)
    } else {
      body.write(0)
    }

    // Is Mutable
    if (isMutable != null) {
      body.write(1)
      body.write(if (isMutable) 1 else 0)
    } else {
      body.write(0)
    }

    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(metadata, isSigner = false, isWritable = true),
        AccountMeta(updateAuthority, isSigner = true, isWritable = false)
      ),
      data = body.toByteArray()
    )
  }

  /**
   * Verify Collection
   */
  fun verifyCollection(
    metadata: Pubkey,
    collectionAuthority: Pubkey,
    payer: Pubkey,
    collectionMint: Pubkey,
    collection: Pubkey,
    collectionMasterEditionAccount: Pubkey
  ): Instruction {
    val body = byteArrayOf(52) // Instruction: VerifyCollection

    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(metadata, isSigner = false, isWritable = true),
        AccountMeta(collectionAuthority, isSigner = true, isWritable = false),
        AccountMeta(payer, isSigner = true, isWritable = true),
        AccountMeta(collectionMint, isSigner = false, isWritable = false),
        AccountMeta(collection, isSigner = false, isWritable = false),
        AccountMeta(collectionMasterEditionAccount, isSigner = false, isWritable = false)
      ),
      data = body
    )
  }

  private fun intToBytesLE(i: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()
  private fun shortToBytesLE(i: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(i.toShort()).array()
  private fun longToBytesLE(i: Long): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(i).array()
}
