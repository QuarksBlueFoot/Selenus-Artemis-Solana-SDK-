package com.selenus.artemis.programs

import com.selenus.artemis.tx.Instruction
import java.nio.charset.StandardCharsets

object MemoProgram {

  /**
   * memo
   *
   * Creates a Memo instruction with UTF-8 payload.
   * Memo program does not require accounts.
   */
  fun memo(text: String): Instruction {
    val data = text.toByteArray(StandardCharsets.UTF_8)
    return Instruction(
      programId = ProgramIds.MEMO_PROGRAM,
      accounts = emptyList(),
      data = data
    )
  }
}
