package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

/**
 * TokenProgram
 *
 * SPL Token instruction builders for the classic Tokenkeg program.
 */
object TokenProgram {
  val PROGRAM_ID = ProgramIds.TOKEN_PROGRAM
  val TOKEN_PROGRAM_ID = PROGRAM_ID

  private fun iData(op: Int, body: ByteArray = byteArrayOf()): ByteArray {
    val out = ByteArray(1 + body.size)
    out[0] = (op and 0xff).toByte()
    body.copyInto(out, 1)
    return out
  }

  fun initializeMint2(
    mint: Pubkey,
    decimals: Int,
    mintAuthority: Pubkey,
    freezeAuthority: Pubkey? = null
  ): Instruction {
    val body = ByteArray(1 + 32 + 1 + 32)
    body[0] = (decimals and 0xff).toByte()
    mintAuthority.bytes.copyInto(body, 1)
    body[33] = if (freezeAuthority != null) 1 else 0
    if (freezeAuthority != null) {
      freezeAuthority.bytes.copyInto(body, 34)
    }
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(AccountMeta(mint, isSigner = false, isWritable = true)),
      data = iData(20, body) // InitializeMint2
    )
  }

  /**
   * Alias for initializeMint2 to support legacy calls.
   */
  fun initializeMint(
    mint: Pubkey,
    decimals: Int,
    mintAuthority: Pubkey,
    freezeAuthority: Pubkey? = null
  ) = initializeMint2(mint, decimals, mintAuthority, freezeAuthority)

  fun transfer(
    source: Pubkey,
    destination: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val body = TokenInstructions.u64LE(amount)
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(3, body)
    )
  }

  fun mintTo(
    mint: Pubkey,
    destination: Pubkey,
    mintAuthority: Pubkey,
    amount: Long
  ): Instruction {
    val body = TokenInstructions.u64LE(amount)
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(mintAuthority, isSigner = true, isWritable = false)
      ),
      data = iData(7, body)
    )
  }

  fun burn(
    account: Pubkey,
    mint: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val body = TokenInstructions.u64LE(amount)
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(8, body)
    )
  }

  fun approve(
    source: Pubkey,
    delegate: Pubkey,
    owner: Pubkey,
    amount: Long
  ): Instruction {
    val body = TokenInstructions.u64LE(amount)
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(delegate, isSigner = false, isWritable = false),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(4, body)
    )
  }

  fun revoke(
    source: Pubkey,
    owner: Pubkey
  ): Instruction {
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(5)
    )
  }

  fun closeAccount(
    account: Pubkey,
    destination: Pubkey,
    owner: Pubkey
  ): Instruction {
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(9)
    )
  }

  fun transferChecked(
    source: Pubkey,
    mint: Pubkey,
    destination: Pubkey,
    owner: Pubkey,
    amount: Long,
    decimals: Int
  ): Instruction {
    val body = ByteArrayBuilder(8 + 1)
      .putLongLE(amount)
      .write(decimals and 0xff)
      .toByteArray()
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(12, body)
    )
  }

  fun syncNative(account: Pubkey): Instruction {
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(AccountMeta(account, isSigner = false, isWritable = true)),
      data = iData(17)
    )
  }
}
