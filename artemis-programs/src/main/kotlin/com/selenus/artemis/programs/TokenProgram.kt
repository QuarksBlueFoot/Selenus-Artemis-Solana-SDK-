package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TokenProgram
 *
 * SPL Token instruction builders for the classic Tokenkeg program.
 */
object TokenProgram {

  private fun iData(op: Int, body: ByteArray = byteArrayOf()): ByteArray {
    val out = ByteArray(1 + body.size)
    out[0] = (op and 0xff).toByte()
    System.arraycopy(body, 0, out, 1, body.size)
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
    System.arraycopy(mintAuthority.bytes, 0, body, 1, 32)
    body[33] = if (freezeAuthority != null) 1 else 0
    if (freezeAuthority != null) {
      System.arraycopy(freezeAuthority.bytes, 0, body, 34, 32)
    }
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(AccountMeta(mint, isSigner = false, isWritable = true)),
      data = iData(20, body) // InitializeMint2
    )
  }

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
    val bb = ByteBuffer.allocate(8 + 1).order(ByteOrder.LITTLE_ENDIAN)
    bb.putLong(amount)
    bb.put((decimals and 0xff).toByte())
    return Instruction(
      programId = ProgramIds.TOKEN_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(12, bb.array())
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
