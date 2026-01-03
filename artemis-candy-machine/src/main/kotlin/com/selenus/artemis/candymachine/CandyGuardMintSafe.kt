package com.selenus.artemis.candymachine

import com.selenus.artemis.candymachine.guards.GuardArgs
import com.selenus.artemis.candymachine.planner.CandyGuardAccountPlanner
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * Safe Candy Guard `mint_v2` builder.
 */
object CandyGuardMintV2Safe {

  data class Result(
    val instruction: Instruction,
    val warnings: List<String> = emptyList(),
  )

  fun buildSafe(
    rpc: RpcApi,
    wallet: Pubkey,
    candyGuard: Pubkey,
    candyMachine: Pubkey,
    mint: Pubkey,
    group: String? = null,
    guardArgs: GuardArgs? = null,
    forcePnft: Boolean = false,
    mintIsSigner: Boolean = false,
  ): Result {
    val plan = CandyGuardAccountPlanner.planMint(
      rpc = rpc,
      candyGuard = candyGuard,
      candyMachine = candyMachine,
      wallet = wallet,
      mint = mint,
      group = group,
      guardArgs = guardArgs,
      forcePnft = forcePnft,
      mintIsSigner = mintIsSigner,
    )

    val ix = CandyGuardMintV2.build(
      args = CandyGuardMintV2.Args(group = group),
      accounts = plan.accounts,
      mintArgsBorsh = plan.mintArgsBorsh,
    )

    validateInstruction(ix)
    return Result(ix, warnings = plan.warnings)
  }

  private fun validateInstruction(ix: Instruction) {
    // Deterministic invariants for mint_v2.
    require(ix.accounts.size >= 20) { "mint_v2 expects at least the base account set" }

    fun assertMeta(i: Int, isWritable: Boolean, isSigner: Boolean? = null) {
      val m = ix.accounts[i]
      require(m.isWritable == isWritable) { "Account[$i] writable flag mismatch" }
      if (isSigner != null) require(m.isSigner == isSigner) { "Account[$i] signer flag mismatch" }
    }

    // candyGuard (w)
    assertMeta(0, isWritable = true)
    // candyMachineProgram (ro)
    assertMeta(1, isWritable = false)
    // candyMachine (w)
    assertMeta(2, isWritable = true)
    // authority PDA (ro)
    assertMeta(3, isWritable = false)
    // payer (w, signer)
    assertMeta(4, isWritable = true, isSigner = true)
    // minter (ro, signer)
    assertMeta(5, isWritable = false, isSigner = true)

    // No duplicate metas with conflicting flags.
    val byKey = ix.accounts.groupBy { it.pubkey }
    for ((k, metas) in byKey) {
      if (metas.size <= 1) continue
      val anySigner = metas.any { it.isSigner }
      val anyWritable = metas.any { it.isWritable }
      // If duplicates exist, all entries should be equivalent to the "max" flags.
      for (m in metas) {
        require(m.isSigner == anySigner || !m.isSigner) { "Duplicate meta for $k has inconsistent signer flag" }
        require(m.isWritable == anyWritable || !m.isWritable) { "Duplicate meta for $k has inconsistent writable flag" }
      }
    }
  }
}
