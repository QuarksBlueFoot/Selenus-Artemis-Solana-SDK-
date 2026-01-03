package com.selenus.artemis.compute

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * ComputeBudgetProgram instruction builders.
 *
 * These helpers are pure and safe to use anywhere.
 */
object ComputeBudgetProgram {

  private val PROGRAM_ID = Pubkey.fromBase58("ComputeBudget111111111111111111111111111111")

  fun setComputeUnitLimit(units: Int): Instruction {
    require(units > 0) { "units must be > 0" }
    val data = byteArrayOf(0x02) + le32(units)
    return Instruction(PROGRAM_ID, emptyList<AccountMeta>(), data)
  }

  fun setComputeUnitPrice(microLamports: Long): Instruction {
    require(microLamports >= 0L) { "microLamports must be >= 0" }
    val data = byteArrayOf(0x03) + le64(microLamports)
    return Instruction(PROGRAM_ID, emptyList<AccountMeta>(), data)
  }

  private fun le32(v: Int): ByteArray {
    return byteArrayOf(
      (v and 0xFF).toByte(),
      ((v shr 8) and 0xFF).toByte(),
      ((v shr 16) and 0xFF).toByte(),
      ((v shr 24) and 0xFF).toByte()
    )
  }

  private fun le64(v: Long): ByteArray {
    return byteArrayOf(
      (v and 0xFF).toByte(),
      ((v shr 8) and 0xFF).toByte(),
      ((v shr 16) and 0xFF).toByte(),
      ((v shr 24) and 0xFF).toByte(),
      ((v shr 32) and 0xFF).toByte(),
      ((v shr 40) and 0xFF).toByte(),
      ((v shr 48) and 0xFF).toByte(),
      ((v shr 56) and 0xFF).toByte()
    )
  }
}
