package com.selenus.artemis.wallet

import com.selenus.artemis.errors.ErrorMappers

import com.selenus.artemis.compute.ComputeBudgetBuilder
import com.selenus.artemis.vtx.TxBudgetAdvisor

/**
 * SendPipeline
 *
 * A small, testable send facade for mobile:
 * - compute budget recommendations
 * - blockhash refresh and re-sign
 * - deterministic retry rules
 *
 * Caller supplies:
 * - blockhash provider
 * - message compiler
 * - rpc send/confirm
 */
object SendPipeline {

  data class Result<T>(
    val value: T,
    val attempts: Int,
    val usedReSign: Boolean,
    val notes: List<String>
  )

  data class Config(
    val maxAttempts: Int = 3,
    val desiredPriority0to100: Int = 35,
    val allowReSign: Boolean = true,
    val allowRetry: Boolean = true
  )

  suspend fun <T> send(
    config: Config = Config(),
    adapter: WalletAdapter,
    getLatestBlockhash: suspend () -> String,
    compileLegacyMessage: suspend (blockhash: String, computeIxs: List<com.selenus.artemis.tx.Instruction>) -> com.selenus.artemis.tx.Message,
    compileV0Message: (suspend (blockhash: String, computeIxs: List<com.selenus.artemis.tx.Instruction>) -> com.selenus.artemis.vtx.MessageV0)? = null,
    sendSigned: suspend (signed: ByteArray) -> T,
    isBlockhashFailure: (Throwable) -> Boolean
  ): Result<T> {
    val caps = adapter.getCapabilities()
    var attempt = 0
    var usedReSign = false
    var lastErr: Throwable? = null

    var blockhash = getLatestBlockhash()

    while (attempt < config.maxAttempts) {
      attempt += 1
      try {
        // Compile messages with compute budget inserted.
        val legacy = compileLegacyMessage(blockhash, emptyList())
        val v0 = compileV0Message?.invoke(blockhash, emptyList())

        val advice = TxBudgetAdvisor.advise(
          legacyMessage = legacy,
          v0Message = v0,
          desiredPriority = config.desiredPriority0to100
        )

        val computeIxs = ComputeBudgetBuilder()
          .fromAdvice(advice)
          .buildInstructions()

        // Re-compile with compute instructions included.
        val legacy2 = compileLegacyMessage(blockhash, computeIxs)
        val msgBytes = legacy2.serialize()

        val signed = adapter.signMessage(msgBytes, SignTxRequest(purpose = "send"))
        return Result(
          value = sendSigned(signed),
          attempts = attempt,
          usedReSign = usedReSign,
          notes = advice.notes
        )
      } catch (t: Throwable) {
        lastErr = t
        val canResign = config.allowReSign && caps.supportsReSign && isBlockhashFailure(t)
        val canRetry = config.allowRetry

        if (canResign) {
          usedReSign = true
          blockhash = getLatestBlockhash()
          continue
        }
        if (canRetry && attempt < config.maxAttempts) continue
        break
      }
    }
    throw lastErr ?: RuntimeException("send_failed")
  }

suspend fun <T> sendWithDefaultErrors(
  config: Config = Config(),
  adapter: WalletAdapter,
  getLatestBlockhash: suspend () -> String,
  compileLegacyMessage: suspend (blockhash: String, computeIxs: List<com.selenus.artemis.tx.Instruction>) -> com.selenus.artemis.tx.Message,
  compileV0Message: (suspend (blockhash: String, computeIxs: List<com.selenus.artemis.tx.Instruction>) -> com.selenus.artemis.vtx.MessageV0)? = null,
  sendSigned: suspend (signed: ByteArray) -> T
): Result<T> {
  return send(
    config = config,
    adapter = adapter,
    getLatestBlockhash = getLatestBlockhash,
    compileLegacyMessage = compileLegacyMessage,
    compileV0Message = compileV0Message,
    sendSigned = sendSigned,
    isBlockhashFailure = { err ->
      when (ErrorMappers.mapRpc(err)) {
        is com.selenus.artemis.errors.ArtemisError.BlockhashExpired,
        is com.selenus.artemis.errors.ArtemisError.BlockhashNotFound -> true
        else -> false
      }
    }
  )
}
}
