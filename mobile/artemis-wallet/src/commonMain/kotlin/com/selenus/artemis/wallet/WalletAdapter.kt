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
   * Signs an arbitrary message (off-chain). Distinct from [signMessage] which signs a
   * transaction wire blob.
   *
   * Default behavior throws [UnsupportedOperationException]; concrete adapters override
   * this when their underlying transport supports off-chain signatures (e.g. MWA's
   * `sign_messages_detached` or Seed Vault's purpose-tagged signing flows). Callers
   * that need a portable fallback should `getCapabilities().supportsSignArbitraryMessage`
   * before calling, and route to a session-key flow on adapters that don't implement it.
   */
  suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
      throw UnsupportedOperationException(
          "signArbitraryMessage is not implemented by this WalletAdapter. " +
          "Check getCapabilities().supportsSignArbitraryMessage before invoking, " +
          "or override this method in your adapter to delegate to the underlying transport."
      )
  }
}
