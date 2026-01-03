package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * ArcanaFlowFrameComposer
 *
 * Produces a transaction plan for an ArcanaFlow frame:
 * - compute budget preset
 * - optional adaptive priority fee
 * - core game instructions
 * - recommended lookup tables to include (existing and/or proposed)
 *
 * This composer does not compile or sign v0 transactions. It returns a plan object
 * that can be executed using AltToolkit or your own send pipeline.
 */
class ArcanaFlowFrameComposer(
  private val programId: String,
  private val tier: ComputeBudgetPresets.Tier,
  private val oracle: PriorityFeeOracle? = null,
  private val cluster: String = "default"
) {

  data class FrameTxPlan(
    val computeInstructions: List<Instruction>,
    val frameInstructions: List<Instruction>,
    val lookupTableAddresses: List<Pubkey>,
    val suggestedMicroLamports: Int
  )

  /**
   * Build a plan from a frame.
   *
   * @param frame ArcanaFlow frame
   * @param knownLookupTables lookup tables already created for this game/session
   * @param proposal optional proposal addresses, used when your app plans to create or extend ALTs
   * @param deny optional denylist for lookup table inclusion
   */
  fun compose(
    frame: ArcanaFlow.Frame,
    knownLookupTables: List<Pubkey> = emptyList(),
    proposal: AltSessionBuilder.Proposal? = null,
    deny: Set<Pubkey> = emptySet()
  ): FrameTxPlan {
    val microLamports = oracle?.suggest(programId = programId, tier = tier, cluster = cluster)
      ?: ComputeBudgetPresets.Tier.COMPETITIVE.microLamports

    val compute = listOf(
      ComputeBudgetPresets.setComputeUnitLimit(tier.units),
      ComputeBudgetPresets.setComputeUnitPrice(microLamports)
    )

    // For now we only recommend existing tables. Proposal is for creation/extension pipelines.
    // Apps can decide when to create/extend and then add the new table address here.
    val luts = knownLookupTables.filter { it !in deny }

    return FrameTxPlan(
      computeInstructions = compute,
      frameInstructions = frame.instructions,
      lookupTableAddresses = luts,
      suggestedMicroLamports = microLamports
    )
  }
}
