package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.tx.ShortVec

/**
 * SPL Token-2022 (Token Extensions) minimal builders.
 *
 * This module focuses on instruction construction. It does not depend on any specific serializer
 * beyond simple little-endian encoding and raw byte payloads.
 */
object Token2022Program {

  // Official Token-2022 program id
  val PROGRAM_ID: Pubkey = Pubkey.fromBase58("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")

  // Associated Token Account program id (same as Token-Program ATA)
  val ASSOCIATED_TOKEN_PROGRAM_ID: Pubkey = Pubkey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")

  // System + Rent
  val SYSTEM_PROGRAM_ID: Pubkey = Pubkey.fromBase58("11111111111111111111111111111111")
  val RENT_SYSVAR_ID: Pubkey = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")

  /**
   * Create an associated token account (ATA) for Token-2022 mint.
   *
   * Uses the standard ATA instruction format (Create=0) with Token-2022 program id provided
   * as the final account.
   */
  fun createAssociatedTokenAccount(
    payer: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    ata: Pubkey
  ): Instruction {
    val keys = listOf(
      AccountMeta(payer, isSigner = true, isWritable = true),
      AccountMeta(ata, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = false, isWritable = false),
      AccountMeta(mint, isSigner = false, isWritable = false),
      AccountMeta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false),
      AccountMeta(PROGRAM_ID, isSigner = false, isWritable = false),
      AccountMeta(RENT_SYSVAR_ID, isSigner = false, isWritable = false),
    )
    // ATA: instruction 0 (Create)
    val data = byteArrayOf(0)
    return Instruction(
      programId = ASSOCIATED_TOKEN_PROGRAM_ID,
      accounts = keys,
      data = data
    )
  }

  /**
   * Token-2022 transfer (same discriminator as SPL token transfer: 3).
   * data: [3] + amount(u64 LE)
   */
  fun transfer(
    sourceAta: Pubkey,
    destinationAta: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val data = ByteArray(1 + 8)
    data[0] = 3
    putU64LE(data, 1, amount)
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(sourceAta, isSigner = false, isWritable = true),
        AccountMeta(destinationAta, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  /**
   * InitializeMint2 (same discriminator as SPL token: 20)
   * data: [20] + decimals(u8) + mintAuthority(pubkey) + freezeAuthorityOption(u8) + freezeAuthority(pubkey?)
   */
  fun initializeMint2(
    mint: Pubkey,
    decimals: Int,
    mintAuthority: Pubkey,
    freezeAuthority: Pubkey?
  ): Instruction {
    val buf = ArrayList<Byte>()
    buf.add(20.toByte())
    buf.add(decimals.toByte())
    buf.addAll(mintAuthority.bytes.toList())
    if (freezeAuthority == null) {
      buf.add(0)
    } else {
      buf.add(1)
      buf.addAll(freezeAuthority.bytes.toList())
    }
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(RENT_SYSVAR_ID, isSigner = false, isWritable = false),
      ),
      data = buf.toByteArray()
    )
  }

  /**
   * Mints new tokens to an account.
   * data: [7] + amount(u64 LE)
   */
  fun mintTo(
    mint: Pubkey,
    destination: Pubkey,
    authority: Pubkey,
    amount: Long
  ): Instruction {
    val data = ByteArray(1 + 8)
    data[0] = 7
    putU64LE(data, 1, amount)
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(authority, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  /**
   * TransferChecked - the recommended transfer instruction for Token-2022.
   *
   * Unlike [transfer], this validates the mint and expected decimals,
   * preventing incorrect amount interpretation and enforcing transfer-fee
   * extension logic. Always prefer this over plain [transfer] for Token-2022.
   *
   * data: [12] + amount(u64 LE) + decimals(u8)
   */
  fun transferChecked(
    source: Pubkey,
    mint: Pubkey,
    destination: Pubkey,
    owner: Pubkey,
    amount: Long,
    decimals: Int
  ): Instruction {
    val data = ByteArray(1 + 8 + 1)
    data[0] = 12 // TransferChecked discriminator
    putU64LE(data, 1, amount)
    data[9] = decimals.toByte()
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  /**
   * Burn tokens from an account.
   * data: [8] + amount(u64 LE)
   */
  fun burn(
    account: Pubkey,
    mint: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val data = ByteArray(1 + 8)
    data[0] = 8
    putU64LE(data, 1, amount)
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  /**
   * Close an account and reclaim its rent-exempt balance.
   * data: [9]
   */
  fun closeAccount(
    account: Pubkey,
    destination: Pubkey,
    owner: Pubkey
  ): Instruction {
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false),
      ),
      data = byteArrayOf(9)
    )
  }

  /**
   * Withdraw withheld tokens from mint (transfer fees collected at the mint level).
   * data: [26] (WithdrawWithheldTokensFromMint)
   */
  fun withdrawWithheldTokensFromMint(
    mint: Pubkey,
    destination: Pubkey,
    withdrawWithheldAuthority: Pubkey
  ): Instruction {
    return Instruction(
      programId = PROGRAM_ID,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(withdrawWithheldAuthority, isSigner = true, isWritable = false),
      ),
      data = byteArrayOf(26)
    )
  }

  /**
   * Withdraw withheld tokens from accounts (collect transfer fees from token accounts).
   * data: [27] (WithdrawWithheldTokensFromAccounts) + num_accounts(u8)
   */
  fun withdrawWithheldTokensFromAccounts(
    mint: Pubkey,
    destination: Pubkey,
    withdrawWithheldAuthority: Pubkey,
    sourceAccounts: List<Pubkey>
  ): Instruction {
    val data = byteArrayOf(27, sourceAccounts.size.toByte())
    val accounts = mutableListOf(
      AccountMeta(mint, isSigner = false, isWritable = true),
      AccountMeta(destination, isSigner = false, isWritable = true),
      AccountMeta(withdrawWithheldAuthority, isSigner = true, isWritable = false),
    )
    for (source in sourceAccounts) {
      accounts.add(AccountMeta(source, isSigner = false, isWritable = true))
    }
    return Instruction(
      programId = PROGRAM_ID,
      accounts = accounts,
      data = data
    )
  }

  private fun putU64LE(dst: ByteArray, off: Int, v: Long) {
    var x = v
    for (i in 0 until 8) {
      dst[off + i] = (x and 0xff).toByte()
      x = x ushr 8
    }
  }
}
