package com.selenus.artemis.candymachine.internal

import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey

internal object AssociatedTokenAddresses {
  fun ata(owner: Pubkey, mint: Pubkey, tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM): Pubkey {
    val seeds = listOf(owner.bytes, tokenProgram.bytes, mint.bytes)
    return Pda.findProgramAddress(seeds, ProgramIds.ASSOCIATED_TOKEN_PROGRAM).address
  }
}
