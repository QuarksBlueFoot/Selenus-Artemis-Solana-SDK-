package com.selenus.artemis.wallet

/**
 * WalletRequests
 *
 * Request hints allow wallets and apps to coordinate user approvals.
 */
sealed interface WalletRequest {
  val purpose: String
}

data class SignTxRequest(
  override val purpose: String,
  val allowReSign: Boolean = true,
  val allowPartialSign: Boolean = false
) : WalletRequest

data class ReSignTxRequest(
  override val purpose: String,
  val oldBlockhash: String,
  val newBlockhash: String
) : WalletRequest

data class FeePayerSwapRequest(
  override val purpose: String,
  val newFeePayerBase58: String
) : WalletRequest
