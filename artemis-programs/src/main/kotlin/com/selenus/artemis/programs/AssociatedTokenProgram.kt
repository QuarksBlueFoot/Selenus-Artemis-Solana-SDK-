package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

object AssociatedTokenProgram {

  /**
   * createAssociatedTokenAccount
   *
   * Creates an ATA for (owner, mint). Token program can be SPL Token or Token-2022.
   */
  fun createAssociatedTokenAccount(
    payer: Pubkey,
    ata: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
  ): Instruction {
    return Instruction(
      programId = ProgramIds.ASSOCIATED_TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(payer, isSigner = true, isWritable = true),
        AccountMeta(ata, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = false, isWritable = false),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(ProgramIds.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
        AccountMeta(tokenProgram, isSigner = false, isWritable = false),
        AccountMeta(ProgramIds.RENT_SYSVAR, isSigner = false, isWritable = false)
      ),
      data = byteArrayOf()
    )
  }
}
