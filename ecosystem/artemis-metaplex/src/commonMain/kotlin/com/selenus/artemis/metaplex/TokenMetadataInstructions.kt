package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

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

  /**
   * Metaplex Token Metadata `CollectionDetails` enum as serialized by the program.
   *
   * The on-chain Rust definition is:
   *
   * ```rust
   * pub enum CollectionDetails {
   *     V1 { size: u64 },
   *     V2 { padding: [u8; 8] },
   * }
   * ```
   *
   * Borsh encodes this as a 1-byte variant index followed by the payload. `V1` is
   * the standard sized-collection marker used by Candy Machine v3 collection NFTs;
   * `V2` is reserved padding and is almost never set by callers. The shape is
   * explicit here so that passing `null` omits the field entirely (the optional
   * wrapper is `None`) while passing a variant serializes the full layout.
   */
  sealed class CollectionDetails {
    data class V1(val size: Long) : CollectionDetails()
    data object V2 : CollectionDetails()
  }

  private fun serializeDataV2(data: DataV2): ByteArray {
    val out = DynBuf()
    
    fun writeString(s: String) {
      val b = s.encodeToByteArray()
      out.write(intToBytesLE(b.size))
      out.write(b)
    }

    writeString(data.name)
    writeString(data.symbol)
    writeString(data.uri)
    out.write(shortToBytesLE(data.sellerFeeBasisPoints))

    if (data.creators != null) {
      out.writeByte(1)
      out.write(intToBytesLE(data.creators.size))
      for (c in data.creators) {
        out.write(c.address.bytes)
        out.writeByte(if (c.verified) 1 else 0)
        out.writeByte(c.share)
      }
    } else {
      out.writeByte(0)
    }

    if (data.collection != null) {
      out.writeByte(1)
      out.writeByte(if (data.collection.verified) 1 else 0)
      out.write(data.collection.key.bytes)
    } else {
      out.writeByte(0)
    }

    if (data.uses != null) {
      out.writeByte(1)
      out.writeByte(data.uses.useMethod)
      out.write(longToBytesLE(data.uses.remaining))
      out.write(longToBytesLE(data.uses.total))
    } else {
      out.writeByte(0)
    }

    return out.toByteArray()
  }

  /**
   * Create Metadata Account V3.
   *
   * Serializes the `CreateMetadataAccountV3` args struct matching the on-chain
   * layout: `DataV2 || Option<isMutable> || Option<CollectionDetails>`. Passing
   * `null` for [collectionDetails] writes a `None` byte. Passing a [CollectionDetails]
   * variant writes `Some` plus the Borsh-encoded enum body.
   */
  fun createMetadataAccountV3(
    metadata: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    payer: Pubkey,
    updateAuthority: Pubkey,
    data: DataV2,
    isMutable: Boolean = true,
    collectionDetails: CollectionDetails? = null
  ): Instruction {
    val body = DynBuf()
    body.writeByte(33) // Instruction: CreateMetadataAccountV3
    body.write(serializeDataV2(data))
    body.writeByte(if (isMutable) 1 else 0)
    if (collectionDetails == null) {
      body.writeByte(0) // Option::None
    } else {
      body.writeByte(1) // Option::Some
      when (collectionDetails) {
        is CollectionDetails.V1 -> {
          body.writeByte(0) // variant index 0 = V1
          body.write(longToBytesLE(collectionDetails.size))
        }
        is CollectionDetails.V2 -> {
          body.writeByte(1) // variant index 1 = V2
          repeat(8) { body.writeByte(0) } // reserved padding[8]
        }
      }
    }

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
    val body = DynBuf()
    body.writeByte(15) // Instruction: UpdateMetadataAccountV2
    
    // Data
    if (data != null) {
      body.writeByte(1)
      body.write(serializeDataV2(data))
    } else {
      body.writeByte(0)
    }

    // New Update Authority
    if (newUpdateAuthority != null) {
      body.writeByte(1)
      body.write(newUpdateAuthority.bytes)
    } else {
      body.writeByte(0)
    }

    // Primary Sale Happened
    if (primarySaleHappened != null) {
      body.writeByte(1)
      body.writeByte(if (primarySaleHappened) 1 else 0)
    } else {
      body.writeByte(0)
    }

    // Is Mutable
    if (isMutable != null) {
      body.writeByte(1)
      body.writeByte(if (isMutable) 1 else 0)
    } else {
      body.writeByte(0)
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

  /**
   * Burn NFT (Token Metadata Program instruction)
   * 
   * Burns an NFT, closing the token account and removing the metadata.
   * This is the Metaplex-native burn that handles metadata cleanup.
   */
  fun burnNft(
    metadata: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    tokenAccount: Pubkey,
    masterEdition: Pubkey,
    splTokenProgram: Pubkey = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
    collectionMetadata: Pubkey? = null
  ): Instruction {
    val body = byteArrayOf(41) // Instruction: BurnNft

    val accounts = mutableListOf(
      AccountMeta(metadata, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = true, isWritable = true),
      AccountMeta(mint, isSigner = false, isWritable = true),
      AccountMeta(tokenAccount, isSigner = false, isWritable = true),
      AccountMeta(masterEdition, isSigner = false, isWritable = true),
      AccountMeta(splTokenProgram, isSigner = false, isWritable = false)
    )

    if (collectionMetadata != null) {
      accounts.add(AccountMeta(collectionMetadata, isSigner = false, isWritable = true))
    }

    return Instruction(
      programId = PROGRAM_ID,
      accounts = accounts,
      data = body
    )
  }

  /**
   * Transfer V1 (Programmable NFT / pNFT transfer)
   * 
   * Transfers an NFT using the Token Metadata program's transfer instruction.
   * Required for pNFTs; also works for regular NFTs.
   */
  fun transferV1(
    token: Pubkey,
    tokenOwner: Pubkey,
    destination: Pubkey,
    destinationOwner: Pubkey,
    mint: Pubkey,
    metadata: Pubkey,
    edition: Pubkey? = null,
    ownerTokenRecord: Pubkey? = null,
    destinationTokenRecord: Pubkey? = null,
    authority: Pubkey,
    payer: Pubkey,
    systemProgram: Pubkey = Pubkey.fromBase58("11111111111111111111111111111111"),
    sysvarInstructions: Pubkey = Pubkey.fromBase58("Sysvar1nstructions1111111111111111111111111"),
    splTokenProgram: Pubkey = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
    splAtaProgram: Pubkey = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"),
    authorizationRulesProgram: Pubkey? = null,
    authorizationRules: Pubkey? = null,
    amount: Long = 1
  ): Instruction {
    val body = DynBuf()
    body.writeByte(49) // Instruction: Transfer (discriminator for TransferV1)
    
    // TransferArgs::V1 { amount }
    body.writeByte(0) // V1 variant
    body.write(longToBytesLE(amount))

    val accounts = mutableListOf(
      AccountMeta(token, isSigner = false, isWritable = true),
      AccountMeta(tokenOwner, isSigner = false, isWritable = false),
      AccountMeta(destination, isSigner = false, isWritable = true),
      AccountMeta(destinationOwner, isSigner = false, isWritable = false),
      AccountMeta(mint, isSigner = false, isWritable = false),
      AccountMeta(metadata, isSigner = false, isWritable = true)
    )

    // Optional edition
    if (edition != null) {
      accounts.add(AccountMeta(edition, isSigner = false, isWritable = false))
    }

    // Optional token records (for pNFTs)
    if (ownerTokenRecord != null) {
      accounts.add(AccountMeta(ownerTokenRecord, isSigner = false, isWritable = true))
    }
    if (destinationTokenRecord != null) {
      accounts.add(AccountMeta(destinationTokenRecord, isSigner = false, isWritable = true))
    }

    accounts.add(AccountMeta(authority, isSigner = true, isWritable = false))
    accounts.add(AccountMeta(payer, isSigner = true, isWritable = true))
    accounts.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    accounts.add(AccountMeta(sysvarInstructions, isSigner = false, isWritable = false))
    accounts.add(AccountMeta(splTokenProgram, isSigner = false, isWritable = false))
    accounts.add(AccountMeta(splAtaProgram, isSigner = false, isWritable = false))

    // Optional authorization rules
    if (authorizationRulesProgram != null) {
      accounts.add(AccountMeta(authorizationRulesProgram, isSigner = false, isWritable = false))
    }
    if (authorizationRules != null) {
      accounts.add(AccountMeta(authorizationRules, isSigner = false, isWritable = false))
    }

    return Instruction(
      programId = PROGRAM_ID,
      accounts = accounts,
      data = body.toByteArray()
    )
  }

  private fun intToBytesLE(i: Int): ByteArray = byteArrayOf(
    (i and 0xFF).toByte(), ((i shr 8) and 0xFF).toByte(),
    ((i shr 16) and 0xFF).toByte(), ((i shr 24) and 0xFF).toByte()
  )

  private fun shortToBytesLE(i: Int): ByteArray = byteArrayOf(
    (i and 0xFF).toByte(), ((i shr 8) and 0xFF).toByte()
  )

  private fun longToBytesLE(i: Long): ByteArray = ByteArray(8) { idx ->
    ((i shr (idx * 8)) and 0xFF).toByte()
  }

  private class DynBuf {
    private var buf = ByteArray(256)
    private var pos = 0

    private fun ensureCapacity(needed: Int) {
      if (pos + needed > buf.size) {
        var newSize = buf.size * 2
        while (newSize < pos + needed) newSize *= 2
        buf = buf.copyOf(newSize)
      }
    }

    fun writeByte(b: Int) {
      ensureCapacity(1)
      buf[pos++] = (b and 0xFF).toByte()
    }

    fun write(bytes: ByteArray) {
      ensureCapacity(bytes.size)
      bytes.copyInto(buf, pos)
      pos += bytes.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(pos)
  }
}
