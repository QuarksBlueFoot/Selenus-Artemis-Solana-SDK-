package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

object AssociatedToken {

  fun address(owner: Pubkey, mint: Pubkey, tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM): Pubkey {
    val seeds = listOf(owner.bytes, tokenProgram.bytes, mint.bytes)
    return Pda.findProgramAddress(seeds, ProgramIds.ASSOCIATED_TOKEN_PROGRAM).address
  }

  fun createAssociatedTokenAccount(
    payer: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    ata: Pubkey = address(owner, mint),
    tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
  ): Instruction {
    val accounts = listOf(
      AccountMeta(payer, isSigner = true, isWritable = true),
      AccountMeta(ata, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = false, isWritable = false),
      AccountMeta(mint, isSigner = false, isWritable = false),
      AccountMeta(ProgramIds.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
      AccountMeta(tokenProgram, isSigner = false, isWritable = false)
    )
    // discriminator 0 = Create
    return Instruction(ProgramIds.ASSOCIATED_TOKEN_PROGRAM, accounts, byteArrayOf(0))
  }

  /**
   * Idempotent ATA creation. If the account already exists with the
   * expected mint and owner, the instruction is a no-op instead of
   * failing. Use this when batching ATA creation alongside transfers
   * so the transaction lands on the second send if the first creation
   * already happened.
   *
   * Same account list as `createAssociatedTokenAccount`; only the
   * discriminator differs (1 = CreateIdempotent).
   */
  fun createAssociatedTokenAccountIdempotent(
    payer: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    ata: Pubkey = address(owner, mint),
    tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
  ): Instruction {
    val accounts = listOf(
      AccountMeta(payer, isSigner = true, isWritable = true),
      AccountMeta(ata, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = false, isWritable = false),
      AccountMeta(mint, isSigner = false, isWritable = false),
      AccountMeta(ProgramIds.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
      AccountMeta(tokenProgram, isSigner = false, isWritable = false)
    )
    return Instruction(ProgramIds.ASSOCIATED_TOKEN_PROGRAM, accounts, byteArrayOf(1))
  }
}
