package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import kotlin.math.min

/**
 * Greedy ALT planner for v0 transactions.
 *
 * Given a set of required AccountMeta entries and a set of available lookup tables,
 * selects a subset of tables that cover the maximum number of unresolved addresses.
 *
 * Notes:
 * - Signer keys must remain in the static account keys list (cannot be loaded from ALT).
 * - Program IDs are typically kept static; the compiler enforces this.
 */
object AltPlanner {

  data class Plan(
    val lookups: List<AddressTableLookup>,
    val loadedWritable: List<Pubkey>,
    val loadedReadonly: List<Pubkey>
  )

  /**
   * Plan lookup table usage for the provided metas.
   *
   * @param metas Account metas from all instructions (program ids excluded)
   * @param tables Candidate lookup tables (already fetched/decoded)
   * @param exclude Keys that must never be loaded from ALT (signers, payer, program ids)
   */
  fun plan(
    metas: List<AccountMeta>,
    tables: List<AddressLookupTableAccount>,
    exclude: Set<Pubkey>
  ): Plan {
    // Only non-signer keys are eligible.
    val required = metas
      .asSequence()
      .filter { !it.isSigner }
      .map { it.pubkey }
      .filter { !exclude.contains(it) }
      .toList()

    if (required.isEmpty() || tables.isEmpty()) {
      return Plan(emptyList(), emptyList(), emptyList())
    }

    val requiredSet = required.toMutableSet()
    val selected = mutableListOf<AddressLookupTableAccount>()

    // Greedy selection by coverage count
    val remainingTables = tables.toMutableList()
    while (requiredSet.isNotEmpty() && remainingTables.isNotEmpty()) {
      var bestIdx = -1
      var bestCover = 0

      for ((i, t) in remainingTables.withIndex()) {
        val cover = t.addresses.count { requiredSet.contains(it) }
        if (cover > bestCover) {
          bestCover = cover
          bestIdx = i
        }
      }

      if (bestIdx < 0 || bestCover == 0) break
      val best = remainingTables.removeAt(bestIdx)
      selected.add(best)
      // Remove covered keys
      for (k in best.addresses) requiredSet.remove(k)
    }

    if (selected.isEmpty()) {
      return Plan(emptyList(), emptyList(), emptyList())
    }

    // Determine writability per key (if any meta marks writable, treat writable)
    val writableSet = mutableSetOf<Pubkey>()
    val readonlySet = mutableSetOf<Pubkey>()
    for (m in metas) {
      if (m.isSigner) continue
      if (exclude.contains(m.pubkey)) continue
      if (m.isWritable) writableSet.add(m.pubkey) else readonlySet.add(m.pubkey)
    }
    // If a key is writable anywhere, treat it writable
    readonlySet.removeAll(writableSet)

    val lookups = mutableListOf<AddressTableLookup>()
    val loadedWritable = mutableListOf<Pubkey>()
    val loadedReadonly = mutableListOf<Pubkey>()

    for (t in selected) {
      // indexes must be u8
      val writableIdxs = mutableListOf<Byte>()
      val readonlyIdxs = mutableListOf<Byte>()

      for ((idx, key) in t.addresses.withIndex()) {
        if (idx > 255) break
        if (!writableSet.contains(key) && !readonlySet.contains(key)) continue

        if (writableSet.contains(key)) {
          writableIdxs.add(idx.toByte())
          loadedWritable.add(key)
        } else {
          readonlyIdxs.add(idx.toByte())
          loadedReadonly.add(key)
        }
      }

      if (writableIdxs.isEmpty() && readonlyIdxs.isEmpty()) continue

      lookups.add(
        AddressTableLookup(
          accountKey = t.key,
          writableIndexes = writableIdxs.toByteArray(),
          readonlyIndexes = readonlyIdxs.toByteArray()
        )
      )
    }

    return Plan(lookups, loadedWritable, loadedReadonly)
  }
}
