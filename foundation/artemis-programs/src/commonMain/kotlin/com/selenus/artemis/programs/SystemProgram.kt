package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

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
    val data = ByteArrayBuilder(4 + 8 + 8 + 32)
      .putIntLE(0) // SystemInstruction::CreateAccount
      .putLongLE(lamports)
      .putLongLE(space)
      .write(owner.bytes)
      .toByteArray()

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

    val data = ByteArrayBuilder(4 + 32 + 4 + seedBytes.size + 8 + 8 + 32)
      .putIntLE(3) // SystemInstruction::CreateAccountWithSeed
      .write(base.bytes)
      .putIntLE(seedBytes.size)
      .write(seedBytes)
      .putLongLE(lamports)
      .putLongLE(space)
      .write(owner.bytes)
      .toByteArray()

    val metas = mutableListOf<AccountMeta>()
    metas += AccountMeta(from, isSigner = true, isWritable = true)
    metas += AccountMeta(newAccount, isSigner = false, isWritable = true)
    // Base must sign. If base==from, keep a single meta.
    if (base != from) metas += AccountMeta(base, isSigner = true, isWritable = false)

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = metas,
      data = data
    )
  }

  /**
   * transfer
   *
   * Creates a system transfer instruction.
   */
  fun transfer(from: Pubkey, to: Pubkey, lamports: Long): Instruction {
    val data = ByteArrayBuilder(4 + 8)
      .putIntLE(2) // SystemInstruction::Transfer
      .putLongLE(lamports)
      .toByteArray()

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
    val data = ByteArrayBuilder(4 + 32)
      .putIntLE(1) // SystemInstruction::Assign
      .write(programId.bytes)
      .toByteArray()

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
    val data = ByteArrayBuilder(4 + 8)
      .putIntLE(8) // SystemInstruction::Allocate
      .putLongLE(space)
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = true, isWritable = true)
      ),
      data = data
    )
  }

  /**
   * transferWithSeed
   *
   * Transfer lamports from an account that was created with
   * [createAccountWithSeed]. Useful when the derived account cannot sign.
   */
  fun transferWithSeed(
    from: Pubkey,
    base: Pubkey,
    seed: String,
    owner: Pubkey,
    to: Pubkey,
    lamports: Long
  ): Instruction {
    val seedBytes = seed.encodeToByteArray()
    require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }

    val data = ByteArrayBuilder(4 + 8 + 4 + seedBytes.size + 32)
      .putIntLE(11) // SystemInstruction::TransferWithSeed
      .putLongLE(lamports)
      .putIntLE(seedBytes.size)
      .write(seedBytes)
      .write(owner.bytes)
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(from, isSigner = false, isWritable = true),
        AccountMeta(base, isSigner = true, isWritable = false),
        AccountMeta(to, isSigner = false, isWritable = true)
      ),
      data = data
    )
  }

  /**
   * allocateWithSeed
   *
   * Allocates space for an account using a base address + seed derivation.
   */
  fun allocateWithSeed(
    account: Pubkey,
    base: Pubkey,
    seed: String,
    space: Long,
    owner: Pubkey
  ): Instruction {
    val seedBytes = seed.encodeToByteArray()
    require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }

    val data = ByteArrayBuilder(4 + 32 + 4 + seedBytes.size + 8 + 32)
      .putIntLE(9) // SystemInstruction::AllocateWithSeed
      .write(base.bytes)
      .putIntLE(seedBytes.size)
      .write(seedBytes)
      .putLongLE(space)
      .write(owner.bytes)
      .toByteArray()

    val metas = mutableListOf<AccountMeta>()
    metas += AccountMeta(account, isSigner = false, isWritable = true)
    if (base != account) metas += AccountMeta(base, isSigner = true, isWritable = false)

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = metas,
      data = data
    )
  }

  /**
   * assignWithSeed
   *
   * Assigns a seeded account to a program.
   */
  fun assignWithSeed(
    account: Pubkey,
    base: Pubkey,
    seed: String,
    owner: Pubkey
  ): Instruction {
    val seedBytes = seed.encodeToByteArray()
    require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }

    val data = ByteArrayBuilder(4 + 32 + 4 + seedBytes.size + 32)
      .putIntLE(10) // SystemInstruction::AssignWithSeed
      .write(base.bytes)
      .putIntLE(seedBytes.size)
      .write(seedBytes)
      .write(owner.bytes)
      .toByteArray()

    val metas = mutableListOf<AccountMeta>()
    metas += AccountMeta(account, isSigner = false, isWritable = true)
    if (base != account) metas += AccountMeta(base, isSigner = true, isWritable = false)

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = metas,
      data = data
    )
  }

  // ════════════════════════════════════════════════════════════════════════
  // NONCE INSTRUCTIONS
  // Convenience wrappers for durable nonce operations.
  // Full nonce account management is in artemis-tx DurableNonce.
  // ════════════════════════════════════════════════════════════════════════

  /** SysvarRecentBlockhashes and Rent program IDs for nonce instructions. */
  private val RECENT_BLOCKHASHES_SYSVAR = Pubkey.fromBase58("SysvarRecentB1ockhashes11111111111111111111")
  private val RENT_SYSVAR = Pubkey.fromBase58("SysvarRent111111111111111111111111111111111")

  /**
   * advanceNonceAccount
   *
   * Advances the nonce in a nonce account. Must be the first instruction
   * in a durable nonce transaction.
   */
  fun advanceNonceAccount(
    nonceAccount: Pubkey,
    nonceAuthority: Pubkey
  ): Instruction {
    val data = ByteArrayBuilder(4)
      .putIntLE(4) // SystemInstruction::AdvanceNonceAccount
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(nonceAccount, isSigner = false, isWritable = true),
        AccountMeta(RECENT_BLOCKHASHES_SYSVAR, isSigner = false, isWritable = false),
        AccountMeta(nonceAuthority, isSigner = true, isWritable = false)
      ),
      data = data
    )
  }

  /**
   * withdrawNonceAccount
   *
   * Withdraws lamports from a nonce account.
   */
  fun withdrawNonceAccount(
    nonceAccount: Pubkey,
    nonceAuthority: Pubkey,
    toPubkey: Pubkey,
    lamports: Long
  ): Instruction {
    val data = ByteArrayBuilder(4 + 8)
      .putIntLE(5) // SystemInstruction::WithdrawNonceAccount
      .putLongLE(lamports)
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(nonceAccount, isSigner = false, isWritable = true),
        AccountMeta(toPubkey, isSigner = false, isWritable = true),
        AccountMeta(RECENT_BLOCKHASHES_SYSVAR, isSigner = false, isWritable = false),
        AccountMeta(RENT_SYSVAR, isSigner = false, isWritable = false),
        AccountMeta(nonceAuthority, isSigner = true, isWritable = false)
      ),
      data = data
    )
  }

  /**
   * initializeNonceAccount
   *
   * Initializes a nonce account with the given authority.
   */
  fun initializeNonceAccount(
    nonceAccount: Pubkey,
    authority: Pubkey
  ): Instruction {
    val data = ByteArrayBuilder(4 + 32)
      .putIntLE(6) // SystemInstruction::InitializeNonceAccount
      .write(authority.bytes)
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(nonceAccount, isSigner = false, isWritable = true),
        AccountMeta(RECENT_BLOCKHASHES_SYSVAR, isSigner = false, isWritable = false),
        AccountMeta(RENT_SYSVAR, isSigner = false, isWritable = false)
      ),
      data = data
    )
  }

  /**
   * authorizeNonceAccount
   *
   * Changes the authority of a nonce account.
   */
  fun authorizeNonceAccount(
    nonceAccount: Pubkey,
    currentAuthority: Pubkey,
    newAuthority: Pubkey
  ): Instruction {
    val data = ByteArrayBuilder(4 + 32)
      .putIntLE(7) // SystemInstruction::AuthorizeNonceAccount
      .write(newAuthority.bytes)
      .toByteArray()

    return Instruction(
      programId = ProgramIds.SYSTEM_PROGRAM,
      accounts = listOf(
        AccountMeta(nonceAccount, isSigner = false, isWritable = true),
        AccountMeta(currentAuthority, isSigner = true, isWritable = false)
      ),
      data = data
    )
  }
}
