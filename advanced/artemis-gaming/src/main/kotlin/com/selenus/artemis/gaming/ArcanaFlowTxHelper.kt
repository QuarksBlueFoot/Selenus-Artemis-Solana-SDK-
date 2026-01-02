package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * ArcanaFlowTxHelper
 *
 * Helper utilities for turning ArcanaFlow frames into transaction plans.
 *
 * This module stays chain agnostic. It does not send transactions.
 */
object ArcanaFlowTxHelper {

  /**
   * Collect addresses into a session cache and an ALT builder proposal.
   *
   * Returns a proposal you can use to build or extend lookup tables.
   */
  fun collectForAltPlanning(
    frame: ArcanaFlow.Frame,
    sessionCache: AltSessionCache,
    builder: AltSessionBuilder,
    proposalLimit: Int = 256
  ): AltSessionBuilder.Proposal {
    ingest(frame.instructions, sessionCache, builder)
    return builder.propose(proposalLimit)
  }

  fun ingest(instructions: List<Instruction>, sessionCache: AltSessionCache, builder: AltSessionBuilder) {
    for (ix in instructions) {
      sessionCache.add(ix.programId)
      builder.ingest(listOf(ix))
      for (m in ix.accounts) {
        sessionCache.add(m.pubkey)
      }
    }
  }

  /**
   * Very small heuristic to filter out obvious low value keys if you want.
   * This is optional.
   */
  fun filterProposal(
    proposal: AltSessionBuilder.Proposal,
    deny: Set<Pubkey> = emptySet()
  ): AltSessionBuilder.Proposal {
    val addrs = proposal.addresses.filter { it !in deny }
    val scores = proposal.scores.filterKeys { it in addrs }
    return AltSessionBuilder.Proposal(addrs, scores)
  }
}
