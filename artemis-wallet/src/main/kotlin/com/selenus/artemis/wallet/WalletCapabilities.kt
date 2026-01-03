package com.selenus.artemis.wallet

/**
 * WalletCapabilities
 *
 * A normalized capability contract for wallets and signers.
 * This avoids wallet-specific branching across apps.
 */
data class WalletCapabilities(
  val supportsReSign: Boolean = true,
  val supportsPartialSign: Boolean = false,
  val supportsFeePayerSwap: Boolean = false,
  val supportsMultipleMessages: Boolean = true,
  val supportsPreAuthorize: Boolean = false
) {
  companion object {
    fun defaultMobile(): WalletCapabilities = WalletCapabilities(
      supportsReSign = true,
      supportsPartialSign = false,
      supportsFeePayerSwap = false,
      supportsMultipleMessages = true,
      supportsPreAuthorize = false
    )
  }
}
