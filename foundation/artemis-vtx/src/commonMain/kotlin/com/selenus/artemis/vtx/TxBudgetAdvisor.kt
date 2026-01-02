package com.selenus.artemis.vtx

import com.selenus.artemis.tx.Message
import kotlin.math.max
import kotlin.math.min

/**
 * TxBudgetAdvisor
 *
 * Helps mobile apps and games build transactions that:
 * - stay under size limits
 * - use v0 + ALTs when helpful
 * - request sane compute budget settings
 *
 * This is a heuristic advisor. It never mutates your transaction.
 */
object TxBudgetAdvisor {

  data class Advice(
    val legacySizeBytes: Int,
    val v0SizeBytes: Int?,
    val shouldPreferV0: Boolean,
    val computeUnitLimit: Int,
    val computeUnitPriceMicroLamports: Long,
    val notes: List<String>
  )

  /**
   * Advises compute and format choices.
   *
   * Inputs:
   * - legacyMessage: compiled legacy message
   * - v0Message: compiled v0 message if available (with ALTs resolved)
   * - desiredPriority: 0..100, higher means more likely to pay a higher CU price for faster inclusion
   */
  fun advise(
    legacyMessage: Message,
    v0Message: MessageV0? = null,
    desiredPriority: Int = 35
  ): Advice {
    val legacySize = legacyMessage.serialize().size
    val v0Size = v0Message?.serialize()?.size

    val shouldPreferV0 = when {
      v0Size == null -> false
      legacySize > 1180 && v0Size < legacySize -> true
      legacySize > 1232 -> true
      else -> false
    }

    val prio = desiredPriority.coerceIn(0, 100)
    val price = computePriceFromPriority(prio)
    val limit = computeLimitFromSize(legacySize, v0Size)

    val notes = mutableListOf<String>()
    if (legacySize > 1200) notes.add("Legacy message is large. Consider v0 with address lookup tables.")
    if (v0Size != null && v0Size < legacySize) notes.add("v0 message is smaller than legacy with current lookups.")
    if (prio >= 70) notes.add("High priority selected. Compute unit price will be higher.")
    if (prio <= 15) notes.add("Low priority selected. Good for games batching many low value actions.")

    return Advice(
      legacySizeBytes = legacySize,
      v0SizeBytes = v0Size,
      shouldPreferV0 = shouldPreferV0,
      computeUnitLimit = limit,
      computeUnitPriceMicroLamports = price,
      notes = notes
    )
  }

  private fun computePriceFromPriority(priority: Int): Long {
    // Heuristic curve for microLamports per CU.
    // Intentionally conservative for mobile.
    val p = priority.coerceIn(0, 100)
    val base = 200L
    val extra = (p.toLong() * p.toLong() * 25L) / 100L
    return base + extra
  }

  private fun computeLimitFromSize(legacySize: Int, v0Size: Int?): Int {
    // Size is a decent proxy for instruction count and decoding work.
    // Keep default under typical limits unless user is doing heavy CPI or CNFT decompression.
    val s = min(legacySize, v0Size ?: legacySize)
    val rough = 200_000 + (s * 220)
    return max(200_000, min(1_000_000, rough))
  }
}
