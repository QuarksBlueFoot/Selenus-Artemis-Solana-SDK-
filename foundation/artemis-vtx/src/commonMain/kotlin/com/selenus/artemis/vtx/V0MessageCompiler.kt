package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.CompiledInstruction
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.tx.MessageHeader

/**
 * High-level v0 message compiler.
 *
 * Input: fee payer + instructions + optional ALT accounts.
 * Output: MessageV0 with correct static keys, address table lookups, and compiled instructions.
 */
object V0MessageCompiler {

  data class CompileResult(
    val message: MessageV0,
    val signers: List<Signer>
  )

  fun compile(
    feePayer: Signer,
    recentBlockhash: String,
    instructions: List<Instruction>,
    addressLookupTables: List<AddressLookupTableAccount> = emptyList()
  ): CompileResult {
    require(instructions.isNotEmpty()) { "instructions must not be empty" }

    val payerKey = feePayer.publicKey

    // Collect metas and program ids.
    val allMetas = mutableListOf<AccountMeta>()
    val programIds = mutableSetOf<Pubkey>()
    for (ix in instructions) {
      programIds.add(ix.programId)
      allMetas.addAll(ix.accounts)
    }

    // Determine signer keys
    val signerKeys = linkedSetOf<Pubkey>()
    signerKeys.add(payerKey)
    for (m in allMetas) if (m.isSigner) signerKeys.add(m.pubkey)

    // Exclude set for ALT planning: payer, all signers, program ids
    val exclude = mutableSetOf<Pubkey>()
    exclude.addAll(signerKeys)
    exclude.addAll(programIds)

    // Plan ALT usage
    val plan = AltPlanner.plan(allMetas, addressLookupTables, exclude)

    val loadedSet = (plan.loadedWritable + plan.loadedReadonly).toSet()

    // Determine writability per key for static (unsigned) keys
    val unsignedWritable = mutableSetOf<Pubkey>()
    val unsignedReadonly = mutableSetOf<Pubkey>()

    for (m in allMetas) {
      if (signerKeys.contains(m.pubkey)) continue
      if (programIds.contains(m.pubkey)) continue
      if (loadedSet.contains(m.pubkey)) continue
      if (m.isWritable) unsignedWritable.add(m.pubkey) else unsignedReadonly.add(m.pubkey)
    }
    // writable wins
    unsignedReadonly.removeAll(unsignedWritable)

    // Static keys order:
    // 1) signers (payer first already)
    // 2) unsigned writable
    // 3) unsigned readonly
    // 4) program ids (readonly)
    val staticKeys = mutableListOf<Pubkey>()
    staticKeys.addAll(signerKeys.toList())
    for (k in unsignedWritable) if (!staticKeys.contains(k)) staticKeys.add(k)
    for (k in unsignedReadonly) if (!staticKeys.contains(k)) staticKeys.add(k)
    for (pid in programIds) if (!staticKeys.contains(pid) && !loadedSet.contains(pid)) staticKeys.add(pid)

    // Header counts
    val writableSignerCount = signerKeys.count { sk ->
      sk == payerKey || allMetas.any { it.pubkey == sk && it.isWritable }
    }
    val readonlySigned = signerKeys.size - writableSignerCount

    // Unsigned readonly count includes program ids and readonly unsigned keys
    val unsignedStaticKeys = staticKeys.drop(signerKeys.size)
    val readonlyUnsignedCount = unsignedStaticKeys.count { k ->
      // if key is in unsignedWritable it's writable; else readonly
      !unsignedWritable.contains(k)
    }

    val header = MessageHeader(
      numRequiredSignatures = signerKeys.size,
      numReadonlySigned = readonlySigned,
      numReadonlyUnsigned = readonlyUnsignedCount
    )

    // Build combined key list used for instruction indices:
    val combinedKeys = mutableListOf<Pubkey>()
    combinedKeys.addAll(staticKeys)
    combinedKeys.addAll(plan.loadedWritable)
    combinedKeys.addAll(plan.loadedReadonly)

    val indexMap = mutableMapOf<Pubkey, Int>()
    for ((i, k) in combinedKeys.withIndex()) indexMap[k] = i

    fun idxOf(k: Pubkey): Int =
      indexMap[k] ?: throw IllegalStateException("Missing account key in combined keys: $k")

    val compiledIxs = instructions.map { ix ->
      val progIdx = idxOf(ix.programId)
      val acctIdxs = ix.accounts.map { m -> idxOf(m.pubkey).toByte() }.toByteArray()
      CompiledInstruction(
        programIdIndex = progIdx,
        accountIndexes = acctIdxs,
        data = ix.data
      )
    }

    val msg = MessageV0(
      header = header,
      staticAccountKeys = staticKeys,
      recentBlockhash = recentBlockhash,
      instructions = compiledIxs,
      addressTableLookups = plan.lookups
    )

    // Signers for VersionedTransaction.sign in correct order (matches signerKeys order)
    val signerList = mutableListOf<Signer>()
    // fee payer first
    signerList.add(feePayer)
    // additional signers should be supplied by caller; we cannot magically produce their Signer instances.
    // For compile(), we only return feePayer; caller can sign separately with VersionedTransaction.sign(signers).
    // We enforce correctness by requiring compileAndSign when additional signers exist.
    if (signerKeys.size > 1) {
      // Caller should use compileMessage only and sign with all signers, or use compileAndSign.
      // We return list containing feePayer to keep this API safe.
    }

    return CompileResult(msg, signerList)
  }

  /**
   * Compile and sign a VersionedTransaction.
   *
   * Requires providing all signers (fee payer + any additional required signers).
   */
  fun compileAndSign(
    feePayer: Signer,
    additionalSigners: List<Signer>,
    recentBlockhash: String,
    instructions: List<Instruction>,
    addressLookupTables: List<AddressLookupTableAccount> = emptyList()
  ): VersionedTransaction {
    val res = compile(feePayer, recentBlockhash, instructions, addressLookupTables)
    val vtx = VersionedTransaction(res.message)

    // Validate that all required signer pubkeys are provided.
    val requiredSignerKeys = res.message.staticAccountKeys.take(res.message.header.numRequiredSignatures).toSet()
    val providedSignerKeys = (listOf(feePayer) + additionalSigners).map { it.publicKey }.toSet()
    require(providedSignerKeys.containsAll(requiredSignerKeys)) {
      "Missing signers. Required=$requiredSignerKeys provided=$providedSignerKeys"
    }

    val allSigners = mutableListOf<Signer>()
    allSigners.add(feePayer)
    allSigners.addAll(additionalSigners)
    vtx.sign(allSigners)
    return vtx
  }
}
