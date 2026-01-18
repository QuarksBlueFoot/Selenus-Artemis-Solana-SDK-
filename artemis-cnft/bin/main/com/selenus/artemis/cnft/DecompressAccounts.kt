package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta

/**
 * Helper struct for the typical DecompressV1 account bundle.
 *
 * Different providers may require slightly different account ordering;
 * this helper gives you a sane default list you can pass as extraAccounts.
 */
data class DecompressAccounts(
  val voucher: Pubkey,
  val newMetadata: Pubkey,
  val newMasterEdition: Pubkey,
  val mint: Pubkey,
  val mintAuthority: Pubkey,
  val tokenAccount: Pubkey,
  val tokenAccountOwner: Pubkey,
  val tokenMetadataProgram: Pubkey = BubblegumPrograms.TOKEN_METADATA_PROGRAM_ID,
  val splTokenProgram: Pubkey = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
  val systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID
) {
  fun toMetas(): List<AccountMeta> {
    return listOf(
      AccountMeta(voucher, isSigner = false, isWritable = true),
      AccountMeta(newMetadata, isSigner = false, isWritable = true),
      AccountMeta(newMasterEdition, isSigner = false, isWritable = true),
      AccountMeta(mint, isSigner = false, isWritable = true),
      AccountMeta(mintAuthority, isSigner = true, isWritable = false),
      AccountMeta(tokenAccount, isSigner = false, isWritable = true),
      AccountMeta(tokenAccountOwner, isSigner = false, isWritable = false),
      AccountMeta(tokenMetadataProgram, isSigner = false, isWritable = false),
      AccountMeta(splTokenProgram, isSigner = false, isWritable = false),
      AccountMeta(systemProgram, isSigner = false, isWritable = false),
    )
  }
}
