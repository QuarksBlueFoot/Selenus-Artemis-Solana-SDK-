package com.selenus.artemis.programs

import com.selenus.artemis.tx.Instruction

object MemoProgram {

  /**
   * memo
   *
   * Creates a Memo instruction with UTF-8 payload.
   * Memo program does not require accounts.
   */
  fun memo(text: String): Instruction {
    val data = text.encodeToByteArray()
    return Instruction(
      programId = ProgramIds.MEMO_PROGRAM,
      accounts = emptyList(),
      data = data
    )
  }

  // Compatibility alias
  fun createInstruction(text: String): Instruction = memo(text)
}
