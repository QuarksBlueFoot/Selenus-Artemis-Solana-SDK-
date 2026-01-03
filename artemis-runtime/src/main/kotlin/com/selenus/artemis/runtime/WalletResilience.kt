package com.selenus.artemis.runtime

/**
 * WalletResilience
 *
 * A small set of interfaces that allow mobile wallets and apps to:
 * - refresh recent blockhash
 * - re-sign deterministically
 * - retry sends safely
 *
 * This avoids brittle wallet-specific hacks and keeps the pipeline testable.
 */
object WalletResilience {

  data class SignerCapabilities(
    val supportsPartialSign: Boolean = false,
    val supportsFeePayerSwap: Boolean = false,
    val supportsReSign: Boolean = true
  )

  interface SignRequest {
    val purpose: String
  }

  data class ReSignRequest(
    override val purpose: String,
    val oldBlockhash: String,
    val newBlockhash: String
  ) : SignRequest

  interface WalletSigner {
    val capabilities: SignerCapabilities
    suspend fun sign(message: ByteArray, request: SignRequest): ByteArray
  }

  /**
   * Default re-sign loop:
   * - build message
   * - sign
   * - send
   * - if blockhash not found / expired, refresh and re-sign
   */
  suspend fun <T> withReSign(
    maxAttempts: Int = 3,
    getLatestBlockhash: suspend () -> String,
    buildMessage: suspend (blockhash: String) -> ByteArray,
    signer: WalletSigner,
    send: suspend (signedMessage: ByteArray) -> T,
    isBlockhashFailure: (Throwable) -> Boolean
  ): T {
    var lastBlockhash = getLatestBlockhash()
    var attempt = 0
    var lastErr: Throwable? = null

    while (attempt < maxAttempts) {
      attempt += 1
      try {
        val msg = buildMessage(lastBlockhash)
        val sig = signer.sign(
          message = msg,
          request = ReSignRequest(
            purpose = "send",
            oldBlockhash = lastBlockhash,
            newBlockhash = lastBlockhash
          )
        )
        return send(sig)
      } catch (t: Throwable) {
        lastErr = t
        if (!isBlockhashFailure(t) || !signer.capabilities.supportsReSign) break
        lastBlockhash = getLatestBlockhash()
      }
    }
    throw lastErr ?: RuntimeException("send_failed")
  }
}
