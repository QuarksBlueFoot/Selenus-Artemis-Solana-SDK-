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

  // Compatibility alias
  fun createInstruction(
    payer: Pubkey,
    associatedToken: Pubkey,
    owner: Pubkey,
    mint: Pubkey,
    programId: Pubkey = ProgramIds.TOKEN_PROGRAM,
    associatedTokenProgramId: Pubkey = ProgramIds.ASSOCIATED_TOKEN_PROGRAM
  ): Instruction {
    // Note: solana-kt might have different parameter order or names, but this covers the main use case.
    // We ignore associatedTokenProgramId if it's the standard one, as our impl hardcodes it or we can check it.
    // Our impl uses ProgramIds.ASSOCIATED_TOKEN_PROGRAM.
    return createAssociatedTokenAccount(payer, associatedToken, owner, mint, programId)
  }
}
