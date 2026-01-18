package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SplToken {
  // instruction enum: Transfer = 3
  fun transfer(
    source: Pubkey,
    destination: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val data = ByteBuffer.allocate(1 + 8).order(ByteOrder.LITTLE_ENDIAN)
      .put(3)
      .putLong(amount)
      .array()

    val accounts = listOf(
      AccountMeta(source, isSigner = false, isWritable = true),
      AccountMeta(destination, isSigner = false, isWritable = true),
      AccountMeta(owner, isSigner = true, isWritable = false)
    )
    return Instruction(ProgramIds.TOKEN_PROGRAM, accounts, data)
  }
}
