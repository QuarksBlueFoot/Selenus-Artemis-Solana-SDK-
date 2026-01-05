package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SystemProgram {
  val PROGRAM_ID = ProgramIds.SYSTEM_PROGRAM

  /**
   * createAccount
   *
   * Creates a new account owned by [owner].
   *
   * NOTE: The new account must sign the transaction.
   */
  fun createAccount(
    from: Pubkey,
    newAccount: Pubkey,
    lamports: Long,
    space: Long,
    owner: Pubkey
  ): Instruction {
    val data = ByteBuffer.allocate(4 + 8 + 8 + 32)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(0) // SystemInstruction::CreateAccount
      .putLong(lamports)
      .putLong(space)
      .put(owner.bytes)
      .array()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(from, isSigner = true, isWritable = true),
        AccountMeta(newAccount, isSigner = true, isWritable = true)
      ),
      data = data
    )
  }

  /**
   * createAccountWithSeed
   *
   * Mobile-friendly variant that does NOT require the derived account to sign.
   * The [base] must sign.
   */
  fun createAccountWithSeed(
    from: Pubkey,
    newAccount: Pubkey,
    base: Pubkey,
    seed: String,
    lamports: Long,
    space: Long,
    owner: Pubkey
  ): Instruction {
    val seedBytes = seed.encodeToByteArray()
    require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }

    val bb = ByteBuffer.allocate(4 + 32 + 4 + seedBytes.size + 8 + 8 + 32)
      .order(ByteOrder.LITTLE_ENDIAN)
    bb.putInt(3) // SystemInstruction::CreateAccountWithSeed
    bb.put(base.bytes)
    bb.putInt(seedBytes.size)
    bb.put(seedBytes)
    bb.putLong(lamports)
    bb.putLong(space)
    bb.put(owner.bytes)

    val metas = mutableListOf<AccountMeta>()
    metas += AccountMeta(from, isSigner = true, isWritable = true)
    metas += AccountMeta(newAccount, isSigner = false, isWritable = true)
    // Base must sign. If base==from, keep a single meta.
    if (base != from) metas += AccountMeta(base, isSigner = true, isWritable = false)

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = metas,
      data = bb.array()
    )
  }

  /**
   * transfer
   *
   * Creates a system transfer instruction.
   */
  fun transfer(from: Pubkey, to: Pubkey, lamports: Long): Instruction {
    val data = ByteBuffer.allocate(4 + 8)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(2) // SystemInstruction::Transfer
      .putLong(lamports)
      .array()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(from, isSigner = true, isWritable = true),
        AccountMeta(to, isSigner = false, isWritable = true)
      ),
      data = data
    )
  }

  /**
   * assign
   *
   * Assigns an account to a program.
   */
  fun assign(account: Pubkey, programId: Pubkey): Instruction {
    val data = ByteBuffer.allocate(4 + 32)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(1) // SystemInstruction::Assign
      .put(programId.bytes)
      .array()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = true, isWritable = true)
      ),
      data = data
    )
  }

  /**
   * allocate
   *
   * Allocates space for an account.
   */
  fun allocate(account: Pubkey, space: Long): Instruction {
    val data = ByteBuffer.allocate(4 + 8)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(8) // SystemInstruction::Allocate
      .putLong(space)
      .array()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = true, isWritable = true)
      ),
      data = data
    )
  }
}
