package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

object MemoProgram {

  /** The SPL Memo v2 program id. */
  val PROGRAM_ID: Pubkey = ProgramIds.MEMO_PROGRAM

  /**
   * memo
   *
   * Creates a Memo instruction with UTF-8 payload.
   *
   * The Memo program supports an optional list of [signers] whose signatures
   * are required for the memo to land. When the list is empty the instruction
   * carries no accounts and any valid transaction can include it; when one or
   * more signers are supplied they are added as read-only signer metas.
   */
  fun memo(text: String, signers: List<Pubkey> = emptyList()): Instruction {
    val data = text.encodeToByteArray()
    return Instruction(
      programId = PROGRAM_ID,
      accounts = signers.map { AccountMeta(it, isSigner = true, isWritable = false) },
      data = data
    )
  }

  // Compatibility aliases used by older call sites and by the solana-kmp /
  // sol4k drop-in layers.
  fun createInstruction(text: String): Instruction = memo(text)
  fun writeUtf8(memo: String, signers: List<Pubkey> = emptyList()): Instruction = memo(memo, signers)
}
