package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * AltSessionBuilder
 *
 * Builds stable lookup set proposals for Address Lookup Tables.
 *
 * Goals:
 * - Suggest a stable set of addresses that appear across ArcanaFlow frames
 * - Keep ordering deterministic so tables do not churn between runs
 * - Provide a scoring model so you can cap table size and still get value
 *
 * This builder does not create ALTs on chain. It produces deterministic address lists
 * you can pass into your ALT creation flow.
 */
class AltSessionBuilder(
  private val maxAddresses: Int = 256
) {

  data class Proposal(
    val addresses: List<Pubkey>,
    val scores: Map<Pubkey, Int>
  )

  private val counts = LinkedHashMap<Pubkey, Int>()
  private val seenOrder = LinkedHashSet<Pubkey>()

  /**
   * Feed a frame worth of instructions. You can call this for every ArcanaFlow frame.
   */
  fun ingest(instructions: List<Instruction>) {
    for (ix in instructions) {
      note(ix.programId)
      for (m in ix.accounts) note(m.pubkey)
    }
  }

  /**
   * Feed many frames at once.
   */
  fun ingestFrames(frames: List<ArcanaFlow.Frame>) {
    for (f in frames) ingest(f.instructions)
  }

  /**
   * Produce a deterministic proposal.
   *
   * Scoring:
   * - higher score if an address shows up more often
   * - ties broken by first seen order
   */
  fun propose(limit: Int = maxAddresses): Proposal {
    val scored = counts.entries.toList()
      .sortedWith(
        compareByDescending<Map.Entry<Pubkey, Int>> { it.value }
          .thenComparator { a, b ->
            // tie break by seen order
            val ai = indexOfSeen(a.key)
            val bi = indexOfSeen(b.key)
            ai.compareTo(bi)
          }
      )

    val out = ArrayList<Pubkey>(minOf(limit, scored.size))
    for (e in scored) {
      if (out.size >= limit) break
      out.add(e.key)
    }
    return Proposal(addresses = out, scores = counts.toMap())
  }

  fun reset() {
    counts.clear()
    seenOrder.clear()
  }

  private fun note(pk: Pubkey) {
    if (counts.size >= 50_000) return
    counts[pk] = (counts[pk] ?: 0) + 1
    seenOrder.add(pk)
  }

  private fun indexOfSeen(pk: Pubkey): Int {
    var i = 0
    for (x in seenOrder) {
      if (x == pk) return i
      i++
    }
    return Int.MAX_VALUE
  }
}
