package com.selenus.artemis.vtx

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.Instruction

/**
 * End-to-end helpers:
 * - fetch lookup tables by address (slot-TTL cache)
 * - optionally optimize LUT subset for smallest message, or smallest compute+size using simulation
 * - compile and sign a v0 VersionedTransaction
 */
object AltToolkit {

  suspend fun fetchLookupTables(
    rpc: RpcApi,
    lookupTableAddresses: List<Pubkey>,
    commitment: String = "finalized",
    ttlSlots: Long = 150L
  ): List<AddressLookupTableAccount> {
    return AltResolver(rpc, ttlSlots).fetchMany(lookupTableAddresses, commitment)
  }

  suspend fun compileAndSignV0WithAutoAlts(
    rpc: RpcApi,
    feePayer: Signer,
    additionalSigners: List<Signer>,
    recentBlockhash: String,
    instructions: List<Instruction>,
    lookupTableAddresses: List<Pubkey>,
    commitment: String = "finalized",
    ttlSlots: Long = 150L,
    optimizeMode: AltOptimizer.Mode = AltOptimizer.Mode.SIZE,
    optimize: Boolean = true
  ): VersionedTransaction {
    val resolver = AltResolver(rpc, ttlSlots)
    val decoded = resolver.fetchMany(lookupTableAddresses, commitment)

    val chosen = if (optimize && decoded.isNotEmpty()) {
      when (optimizeMode) {
        AltOptimizer.Mode.SIZE ->
          AltOptimizer.optimize(
            feePayerKey = feePayer.publicKey,
            recentBlockhash = recentBlockhash,
            instructions = instructions,
            candidates = decoded
          ).selectedTables
        AltOptimizer.Mode.SIZE_AND_COMPUTE ->
          AltOptimizer.optimizeWithSimulation(
            rpc = rpc,
            feePayerKey = feePayer.publicKey,
            recentBlockhash = recentBlockhash,
            instructions = instructions,
            candidates = decoded
          ).selectedTables
      }
    } else decoded

    return V0MessageCompiler.compileAndSign(
      feePayer = feePayer,
      additionalSigners = additionalSigners,
      recentBlockhash = recentBlockhash,
      instructions = instructions,
      addressLookupTables = chosen
    )
  }
}
