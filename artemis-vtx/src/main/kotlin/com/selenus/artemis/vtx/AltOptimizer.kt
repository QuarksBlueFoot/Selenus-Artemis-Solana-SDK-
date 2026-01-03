package com.selenus.artemis.vtx

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * ALT selection optimizer.
 *
 * Two modes:
 * - SIZE: chooses LUT subset that yields smallest serialized v0 message.
 * - SIZE_AND_COMPUTE: uses simulateTransaction(replaceRecentBlockhash=true) to also minimize compute units.
 *
 * Note:
 * - Compute simulation requires an RpcApi and does a small number of RPC calls.
 */
object AltOptimizer {

  enum class Mode { SIZE, SIZE_AND_COMPUTE }

  data class Result(
    val selectedTables: List<AddressLookupTableAccount>,
    val messageSizeBytes: Int,
    val loadedAddressCount: Int,
    val unitsConsumed: Long
  )

  private class DummySigner(override val publicKey: Pubkey) : Signer {
    override fun sign(message: ByteArray): ByteArray = ByteArray(64)
  }

  fun optimize(
    feePayerKey: Pubkey,
    recentBlockhash: String,
    instructions: List<Instruction>,
    candidates: List<AddressLookupTableAccount>,
    maxTablesToTry: Int = 4,
    topCandidates: Int = 12
  ): Result {
    return optimizeInternal(
      rpc = null,
      mode = Mode.SIZE,
      feePayerKey = feePayerKey,
      recentBlockhash = recentBlockhash,
      instructions = instructions,
      candidates = candidates,
      maxTablesToTry = maxTablesToTry,
      topCandidates = topCandidates
    )
  }

  fun optimizeWithSimulation(
    rpc: RpcApi,
    feePayerKey: Pubkey,
    recentBlockhash: String,
    instructions: List<Instruction>,
    candidates: List<AddressLookupTableAccount>,
    maxTablesToTry: Int = 4,
    topCandidates: Int = 12
  ): Result {
    return optimizeInternal(
      rpc = rpc,
      mode = Mode.SIZE_AND_COMPUTE,
      feePayerKey = feePayerKey,
      recentBlockhash = recentBlockhash,
      instructions = instructions,
      candidates = candidates,
      maxTablesToTry = maxTablesToTry,
      topCandidates = topCandidates
    )
  }

  private fun optimizeInternal(
    rpc: RpcApi?,
    mode: Mode,
    feePayerKey: Pubkey,
    recentBlockhash: String,
    instructions: List<Instruction>,
    candidates: List<AddressLookupTableAccount>,
    maxTablesToTry: Int,
    topCandidates: Int
  ): Result {
    val allMetas: List<AccountMeta> = instructions.flatMap { it.accounts }
    val programIds = instructions.map { it.programId }.toSet()
    val exclude = mutableSetOf<Pubkey>()
    exclude.add(feePayerKey)
    exclude.addAll(programIds)

    // Coverage score: how many required metas appear in a LUT
    val required = allMetas.map { it.pubkey }.toSet()

    val ranked = candidates
      .map { t -> t to t.addresses.count { required.contains(it) } }
      .sortedByDescending { it.second }
      .take(topCandidates)
      .map { it.first }

    fun eval(subset: List<AddressLookupTableAccount>): Result {
      val compiled = V0MessageCompiler.compile(DummySigner(feePayerKey), recentBlockhash, instructions, subset)
      val msgSize = compiled.message.serialize().size
      val plan = AltPlanner.plan(allMetas, subset, exclude)
      val loadedCount = plan.loadedWritable.size + plan.loadedReadonly.size

      val units = if (mode == Mode.SIZE_AND_COMPUTE && rpc != null) {
        // We can simulate with replaceRecentBlockhash, so even a dummy blockhash works.
        val vt = VersionedTransaction(
          signatures = listOf(ByteArray(64)), // dummy sig
          message = compiled.message
        )
        val b64 = vt.toBase64()
        val sim = rpc.simulateTransaction(transactionBase64 = b64, sigVerify = false, replaceRecentBlockhash = true)
        if (sim.err == null) sim.unitsConsumed else Long.MAX_VALUE
      } else -1L

      return Result(subset, msgSize, loadedCount, units)
    }

    var best = eval(emptyList())

    fun better(a: Result, b: Result): Boolean {
      return if (mode == Mode.SIZE) {
        a.messageSizeBytes < b.messageSizeBytes ||
          (a.messageSizeBytes == b.messageSizeBytes && a.loadedAddressCount > b.loadedAddressCount)
      } else {
        // SIZE_AND_COMPUTE: primary units, then size
        a.unitsConsumed < b.unitsConsumed ||
          (a.unitsConsumed == b.unitsConsumed && a.messageSizeBytes < b.messageSizeBytes)
      }
    }

    fun consider(sub: List<AddressLookupTableAccount>) {
      val r = eval(sub)
      if (better(r, best)) best = r
    }

    val n = ranked.size
    consider(emptyList())
    for (i in 0 until n) consider(listOf(ranked[i]))
    if (maxTablesToTry >= 2) {
      for (i in 0 until n) for (j in i + 1 until n) consider(listOf(ranked[i], ranked[j]))
    }
    if (maxTablesToTry >= 3) {
      for (i in 0 until n) for (j in i + 1 until n) for (k in j + 1 until n) consider(listOf(ranked[i], ranked[j], ranked[k]))
    }
    if (maxTablesToTry >= 4) {
      for (i in 0 until n)
        for (j in i + 1 until n)
          for (k in j + 1 until n)
            for (l in k + 1 until n)
              consider(listOf(ranked[i], ranked[j], ranked[k], ranked[l]))
    }

    return best
  }
}
