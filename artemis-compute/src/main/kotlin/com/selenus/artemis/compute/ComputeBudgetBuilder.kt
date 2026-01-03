package com.selenus.artemis.compute

import com.selenus.artemis.vtx.TxBudgetAdvisor

/**
 * ComputeBudgetBuilder
 *
 * Opinionated compute + priority fee tuning built for mobile and games.
 * This does not mutate your transactions. It only builds instructions.
 */
class ComputeBudgetBuilder {

  enum class GamePriority(val priority0to100: Int) {
    BACKGROUND_SYNC(5),
    LOW(15),
    NORMAL(35),
    PLAYER_ACTION(60),
    COMBAT_CRITICAL(85)
  }

  data class Params(
    val computeUnitLimit: Int,
    val computeUnitPriceMicroLamports: Long
  )

  private var computeUnitLimit: Int? = null
  private var computeUnitPrice: Long? = null
  private var desiredPriority: Int = GamePriority.NORMAL.priority0to100
  private var legacySizeBytes: Int? = null
  private var v0SizeBytes: Int? = null

  fun forGameAction(priority: GamePriority): ComputeBudgetBuilder = apply {
    desiredPriority = priority.priority0to100
  }

  fun withDesiredPriority(priority0to100: Int): ComputeBudgetBuilder = apply {
    desiredPriority = priority0to100.coerceIn(0, 100)
  }

  fun withLegacySizeBytes(bytes: Int): ComputeBudgetBuilder = apply {
    legacySizeBytes = bytes
  }

  fun withV0SizeBytes(bytes: Int?): ComputeBudgetBuilder = apply {
    v0SizeBytes = bytes
  }

  fun withComputeUnitLimit(units: Int): ComputeBudgetBuilder = apply {
    computeUnitLimit = units
  }

  fun withComputeUnitPriceMicroLamports(price: Long): ComputeBudgetBuilder = apply {
    computeUnitPrice = price
  }

  fun fromAdvice(advice: TxBudgetAdvisor.Advice): ComputeBudgetBuilder = apply {
    legacySizeBytes = advice.legacySizeBytes
    v0SizeBytes = advice.v0SizeBytes
    computeUnitLimit = advice.computeUnitLimit
    computeUnitPrice = advice.computeUnitPriceMicroLamports
  }

  fun buildParams(): Params {
    val size = (v0SizeBytes ?: legacySizeBytes) ?: 0

    val limit = computeUnitLimit ?: heuristicLimit(size)
    val price = computeUnitPrice ?: heuristicPrice(desiredPriority)

    return Params(
      computeUnitLimit = limit.coerceIn(50_000, 1_000_000),
      computeUnitPriceMicroLamports = price.coerceIn(0L, 200_000L)
    )
  }

  fun buildInstructions(): List<com.selenus.artemis.tx.Instruction> {
    val p = buildParams()
    return listOf(
      ComputeBudgetProgram.setComputeUnitLimit(p.computeUnitLimit),
      ComputeBudgetProgram.setComputeUnitPrice(p.computeUnitPriceMicroLamports)
    )
  }

  private fun heuristicLimit(sizeBytes: Int): Int {
    // Conservative defaults with room for NFT/gaming CPI.
    if (sizeBytes <= 0) return 200_000
    val base = 200_000
    val scaled = base + (sizeBytes * 240)
    return scaled
  }

  private fun heuristicPrice(priority0to100: Int): Long {
    // Same curve as TxBudgetAdvisor but exposed here for callers that do not compile messages yet.
    val p = priority0to100.coerceIn(0, 100).toLong()
    val base = 200L
    val extra = (p * p * 25L) / 100L
    return base + extra
  }
}
