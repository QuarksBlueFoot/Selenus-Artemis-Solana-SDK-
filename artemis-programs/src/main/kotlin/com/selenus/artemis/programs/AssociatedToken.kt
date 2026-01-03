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
    ata: Pubkey = address(owner, mint)
  ): Instruction {
    val accounts = listOf(
      AccountMeta(payer, isSigner = true, isWritable = true),
      AccountMeta(ata, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = false, isWritable = false),
      AccountMeta(mint, isSigner = false, isWritable = false),
      AccountMeta(ProgramIds.SYSTEM_PROGRAM, isSigner = false, isWritable = false),
      AccountMeta(ProgramIds.TOKEN_PROGRAM, isSigner = false, isWritable = false)
    )
    // ATA program expects an empty data payload for create
    return Instruction(ProgramIds.ASSOCIATED_TOKEN_PROGRAM, accounts, ByteArray(0))
  }
}
