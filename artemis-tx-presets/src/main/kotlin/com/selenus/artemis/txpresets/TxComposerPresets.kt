package com.selenus.artemis.txpresets

import com.selenus.artemis.programs.AssociatedToken
import com.selenus.artemis.programs.AssociatedTokenProgram
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.tx.Transaction
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.SendPipeline
import com.selenus.artemis.wallet.WalletAdapter

/**
 * TxComposerPresets
 *
 * v59 goal: reusable, mobile-first transaction composition presets.
 *
 * What this gives you:
 * - optional ATA creation (only if missing)
 * - compute budget insertion via SendPipeline
 * - resend + confirm loop for flaky mobile networks
 *
 * This module is intentionally small and avoids any paid dependencies.
 */
object TxComposerPresets {

  data class AtaIntent(
    val owner: Pubkey,
    val mint: Pubkey,
    val tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
  ) {
    fun ataAddress(): Pubkey = AssociatedToken.address(owner = owner, mint = mint, tokenProgram = tokenProgram)
  }

  data class ResendConfig(
    val maxResends: Int = 2,
    val confirmMaxAttempts: Int = 30,
    val confirmSleepMs: Long = 500,
    val skipPreflightOnResend: Boolean = true
  )

  data class ComposeResult(
    val signature: String,
    val notes: List<String>,
    val warnings: List<String>
  )

  /**
   * Ensures associated token accounts exist.
   *
   * Returns a list of create-ATA instructions (empty if all already exist).
   */
  fun buildAtaCreateIxs(
    rpc: RpcApi,
    payer: Pubkey,
    intents: List<AtaIntent>,
    commitment: String = "confirmed"
  ): Pair<List<Instruction>, List<String>> {
    if (intents.isEmpty()) return Pair(emptyList(), emptyList())

    val warnings = mutableListOf<String>()
    val ixs = mutableListOf<Instruction>()

    for (intent in intents) {
      val ata = intent.ataAddress()
      val exists = rpc.getAccountInfoBase64(ata.toString(), commitment = commitment) != null
      if (!exists) {
        ixs.add(
          AssociatedTokenProgram.createAssociatedTokenAccount(
            payer = payer,
            ata = ata,
            owner = intent.owner,
            mint = intent.mint,
            tokenProgram = intent.tokenProgram
          )
        )
      }
      // Basic sanity: do not silently create ATAs for a different token program.
      if (intent.tokenProgram != ProgramIds.TOKEN_PROGRAM) {
        warnings.add("ATA intent uses a non-standard token program. Proceed carefully.")
      }
    }
    return Pair(ixs, warnings)
  }

  /**
   * Preset: Compose + sign + send + confirm with resend.
   *
   * You supply:
   * - base instructions (program specific)
   * - optional ATA intents
   *
   * We supply:
   * - compute budget insertion via SendPipeline
   * - resend/confirm loop
   */
  suspend fun sendWithAtaAndPriority(
    rpc: RpcApi,
    adapter: WalletAdapter,
    instructions: List<Instruction>,
    ataIntents: List<AtaIntent> = emptyList(),
    sendConfig: SendPipeline.Config = SendPipeline.Config(),
    resendConfig: ResendConfig = ResendConfig(),
    feePayer: Pubkey = adapter.publicKey
  ): ComposeResult {
    val (ataIxs, ataWarnings) = buildAtaCreateIxs(rpc = rpc, payer = feePayer, intents = ataIntents)

    val pipelineRes = SendPipeline.sendWithDefaultErrors(
      config = sendConfig,
      adapter = adapter,
      getLatestBlockhash = { rpc.getLatestBlockhash().blockhash },
      compileLegacyMessage = { blockhash, computeIxs ->
        val all = computeIxs + ataIxs + instructions
        Transaction(
          feePayer = feePayer,
          recentBlockhash = blockhash,
          instructions = all
        ).compileMessage()
      },
      // v0 optional later; keep v59 minimal and deterministic.
      compileV0Message = null,
      sendSigned = { signedTxBytes ->
        // Wallet adapters used in production should return raw tx bytes ready for sendRawTransaction.
        val sig = rpc.sendRawTransaction(signedTxBytes, skipPreflight = false)
        val ok = confirmWithResend(
          rpc = rpc,
          signature = sig,
          signedTxBytes = signedTxBytes,
          resendConfig = resendConfig
        )
        if (!ok) throw RuntimeException("transaction_not_confirmed")
        sig
      }
    )

    return ComposeResult(
      signature = pipelineRes.value,
      notes = pipelineRes.notes,
      warnings = ataWarnings
    )
  }

  private fun confirmWithResend(
    rpc: RpcApi,
    signature: String,
    signedTxBytes: ByteArray,
    resendConfig: ResendConfig
  ): Boolean {
    // First confirm attempt.
    if (rpc.confirmTransaction(signature, maxAttempts = resendConfig.confirmMaxAttempts, sleepMs = resendConfig.confirmSleepMs)) {
      return true
    }

    var resends = 0
    while (resends < resendConfig.maxResends) {
      resends += 1
      try {
        rpc.sendRawTransaction(
          signedTxBytes,
          skipPreflight = resendConfig.skipPreflightOnResend,
          maxRetries = null
        )
      } catch (_: Throwable) {
        // Ignore: if the tx already landed, resend may error or be redundant.
      }
      if (rpc.confirmTransaction(signature, maxAttempts = resendConfig.confirmMaxAttempts, sleepMs = resendConfig.confirmSleepMs)) {
        return true
      }
    }
    return false
  }
}
