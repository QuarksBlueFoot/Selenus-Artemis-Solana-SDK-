package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

object SplToken {
  // instruction enum: Transfer = 3
  fun transfer(
    source: Pubkey,
    destination: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val data = ByteArrayBuilder(1 + 8)
      .write(3)
      .putLongLE(amount)
      .toByteArray()

    val accounts = listOf(
      AccountMeta(source, isSigner = false, isWritable = true),
      AccountMeta(destination, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = true, isWritable = false)
    )
    return Instruction(ProgramIds.TOKEN_PROGRAM, accounts, data)
  }
}
