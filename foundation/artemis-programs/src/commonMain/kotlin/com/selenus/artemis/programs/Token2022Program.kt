package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.ByteArrayBuilder
import com.selenus.artemis.tx.Instruction

/**
 * Token2022Program
 *
 * Instruction builders for Token-2022 program. These cover the most common actions.
 * Extensions are supported elsewhere via TLV decoding; initialization of extensions is intentionally modular.
 */
object Token2022Program {

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
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(AccountMeta(mint, isSigner = false, isWritable = true)),
      data = iData(20, body)
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
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(
        AccountMeta(source, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(12, body)
    )
  }

  fun mintToChecked(
    mint: Pubkey,
    destination: Pubkey,
    mintAuthority: Pubkey,
    amount: Long,
    decimals: Int
  ): Instruction {
    val body = ByteArrayBuilder(8 + 1)
      .putLongLE(amount)
      .write(decimals and 0xff)
      .toByteArray()
    return Instruction(
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(
        AccountMeta(mint, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(mintAuthority, isSigner = true, isWritable = false)
      ),
      data = iData(14, body)
    )
  }

  fun closeAccount(
    account: Pubkey,
    destination: Pubkey,
    owner: Pubkey
  ): Instruction {
    return Instruction(
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(destination, isSigner = false, isWritable = true),
        AccountMeta(owner, isSigner = true, isWritable = false)
      ),
      data = iData(9)
    )
  }

  /**
   * Initializes a Token-2022 account in a single instruction. Op 18,
   * `InitializeAccount3`. Unlike op 1 / 2, this carries the owner in
   * the data payload instead of as a separate account, which lets
   * callers create + initialize an account in two instructions instead
   * of three.
   */
  fun initializeAccount3(
    account: Pubkey,
    mint: Pubkey,
    owner: Pubkey
  ): Instruction {
    val body = ByteArray(32)
    owner.bytes.copyInto(body)
    return Instruction(
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true),
        AccountMeta(mint, isSigner = false, isWritable = false)
      ),
      data = iData(18, body)
    )
  }

  /**
   * Marks the account's owner as immutable. Op 22,
   * `InitializeImmutableOwner`. Always pair with `initializeAccount3`
   * for ATAs so the owner field cannot be reassigned via
   * `setAuthority`. The associated token program does this for free
   * on standard SPL ATAs; Token-2022 ATAs need it explicitly.
   */
  fun initializeImmutableOwner(account: Pubkey): Instruction {
    return Instruction(
      programId = ProgramIds.TOKEN_2022_PROGRAM,
      accounts = listOf(
        AccountMeta(account, isSigner = false, isWritable = true)
      ),
      data = iData(22)
    )
  }
}
