package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Pubkey

/**
 * WalletAdapter
 *
 * Apps implement this to wrap MWA, Wallet Standard, or custom signers.
 * It is intentionally minimal and test-friendly.
 */
interface WalletAdapter {
  val publicKey: Pubkey
  suspend fun getCapabilities(): WalletCapabilities

  /**
   * Signs a single compiled transaction message.
   * Returns the signed wire bytes (transaction or message depending on adapter).
   */
  suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray

  /**
   * Optional batch signing. If not supported, apps can fall back to per-message signing.
   */
  suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
    // default fallback
    return messages.map { signMessage(it, request) }
  }

  /**
   * Signs an arbitrary message (off-chain).
   * This is distinct from signMessage which signs a transaction.
   */
  suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
      throw UnsupportedOperationException("signArbitraryMessage not supported")
  }
}
