package com.selenus.artemis.gaming

import com.selenus.artemis.tx.Instruction

/**
 * AltTxScheduler
 *
 * Splits lookup table maintenance instructions into multiple transactions using conservative limits.
 *
 * Why this exists:
 * - create + extend can exceed size limits if you try to do too much in one tx
 * - mobile pipelines want a predictable, deterministic schedule
 *
 * This is a pure scheduler. It does not sign or send.
 */
object AltTxScheduler {

  data class TxBatch(
    val instructions: List<Instruction>,
    val label: String
  )

  /**
   * Schedule a create+extend plan into transaction batches.
   *
   * Default behavior:
   * - tx0: create lookup table
   * - tx1..N: extend in chunks, up to maxExtendIxsPerTx each
   *
   * If mergeCreateAndFirstExtend is true and there is at least one extend instruction,
   * tx0 will contain create + first extend to reduce round trips.
   */
  fun scheduleCreatePlan(
    plan: AltSessionExecutor.CreatePlan,
    maxExtendIxsPerTx: Int = 2,
    mergeCreateAndFirstExtend: Boolean = true
  ): List<TxBatch> {
    val all = plan.instructions
    if (all.isEmpty()) return emptyList()

    val createIx = all.first()
    val extendIxs = all.drop(1)

    val out = ArrayList<TxBatch>()

    if (extendIxs.isEmpty()) {
      out.add(TxBatch(listOf(createIx), "alt-create"))
      return out
    }

    var start = 0
    if (mergeCreateAndFirstExtend) {
      out.add(TxBatch(listOf(createIx, extendIxs.first()), "alt-create+extend-0"))
      start = 1
    } else {
      out.add(TxBatch(listOf(createIx), "alt-create"))
    }

    val perTx = maxExtendIxsPerTx.coerceAtLeast(1)
    var idx = 0
    var i = start
    while (i < extendIxs.size) {
      val slice = extendIxs.subList(i, minOf(i + perTx, extendIxs.size))
      out.add(TxBatch(slice.toList(), "alt-extend-${idx + start}"))
      i += perTx
      idx++
    }

    return out
  }

  /**
   * Schedule extend instructions into batches.
   */
  fun scheduleExtend(
    extendInstructions: List<Instruction>,
    maxExtendIxsPerTx: Int = 2
  ): List<TxBatch> {
    if (extendInstructions.isEmpty()) return emptyList()
    val perTx = maxExtendIxsPerTx.coerceAtLeast(1)
    val out = ArrayList<TxBatch>()
    var i = 0
    var idx = 0
    while (i < extendInstructions.size) {
      val slice = extendInstructions.subList(i, minOf(i + perTx, extendInstructions.size))
      out.add(TxBatch(slice.toList(), "alt-extend-$idx"))
      i += perTx
      idx++
    }
    return out
  }
}
