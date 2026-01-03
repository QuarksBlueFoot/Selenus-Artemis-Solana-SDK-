package com.selenus.artemis.gaming

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.vtx.AltOptimizer
import com.selenus.artemis.vtx.AltToolkit
import com.selenus.artemis.vtx.VersionedTransaction
import com.selenus.artemis.tx.Instruction

/**
 * ArcanaFlowV0Compiler
 *
 * One-call compile for ArcanaFlow frame plans into a v0 VersionedTransaction.
 *
 * This is the bridge from:
 * - ArcanaFlowFrameComposer.FrameTxPlan
 * to:
 * - signed v0 transaction bytes
 *
 * It supports:
 * - auto fetch lookup tables
 * - optional LUT optimization (size or size+compute)
 */
object ArcanaFlowV0Compiler {

  fun compileFrame(
    rpc: RpcApi,
    feePayer: Signer,
    additionalSigners: List<Signer>,
    recentBlockhash: String,
    plan: ArcanaFlowFrameComposer.FrameTxPlan,
    commitment: String = "finalized",
    ttlSlots: Long = 150L,
    optimizeMode: AltOptimizer.Mode = AltOptimizer.Mode.SIZE,
    optimize: Boolean = true
  ): VersionedTransaction {
    val instructions: List<Instruction> = plan.computeInstructions + plan.frameInstructions
    return AltToolkit.compileAndSignV0WithAutoAlts(
      rpc = rpc,
      feePayer = feePayer,
      additionalSigners = additionalSigners,
      recentBlockhash = recentBlockhash,
      instructions = instructions,
      lookupTableAddresses = plan.lookupTableAddresses,
      commitment = commitment,
      ttlSlots = ttlSlots,
      optimizeMode = optimizeMode,
      optimize = optimize
    )
  }
}
