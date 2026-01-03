package com.selenus.artemis.gaming

import com.selenus.artemis.compute.ComputeBudgetProgram
import com.selenus.artemis.tx.Instruction

/**
 * Game friendly compute and fee presets.
 *
 * This delegates to artemis-compute so the same logic is available to all apps.
 */
object ComputeBudgetPresets {

  enum class Tier(val units: Int, val microLamports: Long) {
    ARCADE(units = 200_000, microLamports = 100),
    COMPETITIVE(units = 400_000, microLamports = 500),
    BOSS_FIGHT(units = 800_000, microLamports = 2_000)
  }

  fun setComputeUnitLimit(units: Int): Instruction = ComputeBudgetProgram.setComputeUnitLimit(units)

  fun setComputeUnitPrice(microLamports: Long): Instruction =
    ComputeBudgetProgram.setComputeUnitPrice(microLamports)

  fun preset(tier: Tier): List<Instruction> {
    return listOf(
      ComputeBudgetProgram.setComputeUnitLimit(tier.units),
      ComputeBudgetProgram.setComputeUnitPrice(tier.microLamports)
    )
  }
}
