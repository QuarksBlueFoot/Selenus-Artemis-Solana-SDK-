package com.selenus.artemis.gaming

import com.selenus.artemis.programs.AddressLookupTableProgram
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * AltSessionExecutor
 *
 * Builds instruction bundles for creating and extending Address Lookup Tables (ALTs)
 * based on deterministic proposals (AltSessionBuilder).
 *
 * This is a pure builder. It does not send transactions or fetch slots.
 */
object AltSessionExecutor {

  data class CreatePlan(
    val tableAddress: Pubkey,
    val instructions: List<Instruction>
  )

  /**
   * Create a new lookup table for the provided authority and recent slot, then extend it
   * with the proposal addresses in deterministic chunked batches.
   *
   * @param authority authority signer for the lookup table
   * @param payer fee payer signer that funds create/extend
   * @param recentSlot slot used for deterministic table address derivation
   * @param addresses deterministic address list (usually from AltSessionBuilder.Proposal.addresses)
   * @param extendChunk max number of addresses per extend instruction
   */
  fun createAndExtend(
    authority: Pubkey,
    payer: Pubkey,
    recentSlot: Long,
    addresses: List<Pubkey>,
    extendChunk: Int = 20
  ): CreatePlan {
    val (createIx, table) = AddressLookupTableProgram.createLookupTable(authority, payer, recentSlot)
    val extendIxs = extendExisting(
      lookupTable = table,
      authority = authority,
      payer = payer,
      newAddresses = addresses,
      extendChunk = extendChunk
    )
    return CreatePlan(table, listOf(createIx) + extendIxs)
  }

  /**
   * Extend an existing lookup table. Chunks are used to keep transaction sizes reasonable.
   * This function de-duplicates while preserving first-seen ordering.
   */
  fun extendExisting(
    lookupTable: Pubkey,
    authority: Pubkey,
    payer: Pubkey? = null,
    newAddresses: List<Pubkey>,
    extendChunk: Int = 20
  ): List<Instruction> {
    if (newAddresses.isEmpty()) return emptyList()
    val chunk = extendChunk.coerceAtLeast(1)

    val deduped = LinkedHashSet<Pubkey>()
    for (a in newAddresses) deduped.add(a)
    val ordered = deduped.toList()

    val out = ArrayList<Instruction>()
    var i = 0
    while (i < ordered.size) {
      val slice = ordered.subList(i, minOf(i + chunk, ordered.size))
      out.add(
        AddressLookupTableProgram.extendLookupTable(
          lookupTable = lookupTable,
          authority = authority,
          payer = payer,
          newAddresses = slice
        )
      )
      i += chunk
    }
    return out
  }
}
