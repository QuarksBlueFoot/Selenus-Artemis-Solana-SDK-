package com.selenus.artemis.nft

import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TokenMetadataProgram {

  private fun u8(v: Int) = byteArrayOf((v and 0xff).toByte())
  private fun u16LE(v: Int): ByteArray =
    ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
  private fun u32LE(v: Long): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
  private fun u64LE(v: Long): ByteArray =
    ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
  private fun string(s: String): ByteArray {
    val b = s.toByteArray(Charsets.UTF_8)
    return u32LE(b.size.toLong()) + b
  }
  private fun optPubkey(v: Pubkey?): ByteArray = if (v == null) u8(0) else u8(1) + v.bytes
  private fun optU64(v: Long?): ByteArray = if (v == null) u8(0) else u8(1) + u64LE(v)

  data class DataV2(
    val name: String,
    val symbol: String,
    val uri: String,
    val sellerFeeBasisPoints: Int,
    val creators: List<Creator>? = null,
    val collectionMint: Pubkey? = null,
    val uses: Uses? = null
  )

  private fun encodeCreators(creators: List<Creator>?): ByteArray {
    if (creators == null) return u8(0)
    var out = u8(1) + u32LE(creators.size.toLong())
    for (c in creators) {
      out += c.address.bytes
      out += u8(if (c.verified) 1 else 0)
      out += u8(c.share)
    }
    return out
  }

  private fun encodeCollection(collectionMint: Pubkey?): ByteArray {
    return if (collectionMint == null) u8(0) else u8(1) + u8(0) + collectionMint.bytes
  }

  private fun encodeUses(uses: Uses?): ByteArray {
    return if (uses == null) u8(0) else u8(1) + u8(uses.useMethod) + u64LE(uses.remaining) + u64LE(uses.total)
  }

  private fun encodeDataV2(d: DataV2): ByteArray {
    return string(d.name) +
      string(d.symbol) +
      string(d.uri) +
      u16LE(d.sellerFeeBasisPoints) +
      encodeCreators(d.creators) +
      encodeCollection(d.collectionMint) +
      encodeUses(d.uses)
  }

  fun createMetadataAccountV3(
    metadata: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    payer: Pubkey,
    updateAuthority: Pubkey,
    data: DataV2,
    isMutable: Boolean = true
  ): Instruction {
    val ix = u8(33) + encodeDataV2(data) + u8(if (isMutable) 1 else 0) + u8(0)
    return Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(metadata, false, true),
        AccountMeta(mint, false, false),
        AccountMeta(mintAuthority, true, false),
        AccountMeta(payer, true, true),
        AccountMeta(updateAuthority, false, false),
        AccountMeta(ProgramIds.SYSTEM_PROGRAM, false, false),
        AccountMeta(ProgramIds.RENT_SYSVAR, false, false)
      ),
      data = ix
    )
  }

  fun createMasterEditionV3(
    edition: Pubkey,
    metadata: Pubkey,
    mint: Pubkey,
    mintAuthority: Pubkey,
    payer: Pubkey,
    updateAuthority: Pubkey,
    maxSupply: Long? = null
  ): Instruction {
    val ix = u8(17) + optU64(maxSupply)
    return Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(edition, false, true),
        AccountMeta(mint, false, true),
        AccountMeta(updateAuthority, false, false),
        AccountMeta(mintAuthority, true, false),
        AccountMeta(payer, true, true),
        AccountMeta(metadata, false, true),
        AccountMeta(ProgramIds.TOKEN_PROGRAM, false, false),
        AccountMeta(ProgramIds.SYSTEM_PROGRAM, false, false),
        AccountMeta(ProgramIds.RENT_SYSVAR, false, false)
      ),
      data = ix
    )
  }

  fun updateMetadataAccountV2(
    metadata: Pubkey,
    updateAuthority: Pubkey,
    newUpdateAuthority: Pubkey? = null,
    primarySaleHappened: Boolean? = null,
    isMutable: Boolean? = null
  ): Instruction {
    val ix =
      u8(15) +
        u8(0) +
        optPubkey(newUpdateAuthority) +
        (if (primarySaleHappened == null) u8(0) else u8(1) + u8(if (primarySaleHappened) 1 else 0)) +
        (if (isMutable == null) u8(0) else u8(1) + u8(if (isMutable) 1 else 0))
    return Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(metadata, false, true),
        AccountMeta(updateAuthority, true, false)
      ),
      data = ix
    )
  }

  fun signMetadata(metadata: Pubkey, creator: Pubkey): Instruction =
    Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(AccountMeta(metadata, false, true), AccountMeta(creator, true, false)),
      data = u8(7)
    )

  fun verifyCollection(
    metadata: Pubkey,
    collectionAuthority: Pubkey,
    payer: Pubkey,
    collectionMint: Pubkey,
    collectionMetadata: Pubkey,
    collectionMasterEdition: Pubkey
  ): Instruction =
    Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(metadata, false, true),
        AccountMeta(collectionAuthority, true, false),
        AccountMeta(payer, true, true),
        AccountMeta(collectionMint, false, false),
        AccountMeta(collectionMetadata, false, false),
        AccountMeta(collectionMasterEdition, false, false)
      ),
      data = u8(18)
    )

  fun unverifyCollection(
    metadata: Pubkey,
    collectionAuthority: Pubkey,
    payer: Pubkey,
    collectionMint: Pubkey,
    collectionMetadata: Pubkey,
    collectionMasterEdition: Pubkey
  ): Instruction =
    Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(metadata, false, true),
        AccountMeta(collectionAuthority, true, false),
        AccountMeta(payer, true, true),
        AccountMeta(collectionMint, false, false),
        AccountMeta(collectionMetadata, false, false),
        AccountMeta(collectionMasterEdition, false, false)
      ),
      data = u8(19)
    )

  fun setAndVerifyCollection(
    metadata: Pubkey,
    collectionAuthority: Pubkey,
    payer: Pubkey,
    updateAuthority: Pubkey,
    collectionMint: Pubkey,
    collectionMetadata: Pubkey,
    collectionMasterEdition: Pubkey,
    collectionAuthorityRecord: Pubkey? = null
  ): Instruction {
    val accounts = ArrayList<AccountMeta>()
    accounts.add(AccountMeta(metadata, false, true))
    accounts.add(AccountMeta(collectionAuthority, true, false))
    accounts.add(AccountMeta(payer, true, true))
    accounts.add(AccountMeta(updateAuthority, true, false))
    accounts.add(AccountMeta(collectionMint, false, false))
    accounts.add(AccountMeta(collectionMetadata, false, false))
    accounts.add(AccountMeta(collectionMasterEdition, false, false))
    if (collectionAuthorityRecord != null) accounts.add(AccountMeta(collectionAuthorityRecord, false, false))
    return Instruction(MetaplexIds.TOKEN_METADATA_PROGRAM, accounts, u8(34))
  }

  fun verifySizedCollectionItem(
    metadata: Pubkey,
    collectionAuthority: Pubkey,
    payer: Pubkey,
    collectionMint: Pubkey,
    collectionMetadata: Pubkey,
    collectionMasterEdition: Pubkey,
    collectionAuthorityRecord: Pubkey? = null
  ): Instruction {
    val accounts = ArrayList<AccountMeta>()
    accounts.add(AccountMeta(metadata, false, true))
    accounts.add(AccountMeta(collectionAuthority, true, false))
    accounts.add(AccountMeta(payer, true, true))
    accounts.add(AccountMeta(collectionMint, false, false))
    accounts.add(AccountMeta(collectionMetadata, false, true))
    accounts.add(AccountMeta(collectionMasterEdition, false, false))
    if (collectionAuthorityRecord != null) accounts.add(AccountMeta(collectionAuthorityRecord, false, false))
    return Instruction(MetaplexIds.TOKEN_METADATA_PROGRAM, accounts, u8(35))
  }

  fun approveCollectionAuthority(
    collectionAuthorityRecord: Pubkey,
    newCollectionAuthority: Pubkey,
    updateAuthority: Pubkey,
    payer: Pubkey,
    collectionMetadata: Pubkey,
    collectionMint: Pubkey
  ): Instruction =
    Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(collectionAuthorityRecord, false, true),
        AccountMeta(newCollectionAuthority, false, false),
        AccountMeta(updateAuthority, true, false),
        AccountMeta(payer, true, true),
        AccountMeta(collectionMetadata, false, false),
        AccountMeta(collectionMint, false, false),
        AccountMeta(ProgramIds.SYSTEM_PROGRAM, false, false),
        AccountMeta(ProgramIds.RENT_SYSVAR, false, false)
      ),
      data = u8(22)
    )

  fun revokeCollectionAuthority(
    collectionAuthorityRecord: Pubkey,
    revokeAuthority: Pubkey,
    collectionMetadata: Pubkey,
    collectionMint: Pubkey
  ): Instruction =
    Instruction(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM,
      accounts = listOf(
        AccountMeta(collectionAuthorityRecord, false, true),
        AccountMeta(revokeAuthority, true, false),
        AccountMeta(collectionMetadata, false, false),
        AccountMeta(collectionMint, false, false)
      ),
      data = u8(23)
    )
}
