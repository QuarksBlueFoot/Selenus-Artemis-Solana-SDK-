package com.selenus.artemis.wallet

/**
 * WalletAdapterSignAndSend
 *
 * Optional capability interface for wallets that can sign and submit transactions directly.
 * This is useful on mobile when the wallet can broadcast with its own RPC routing.
 */
interface WalletAdapterSignAndSend {
  suspend fun signAndSendTransactions(
    transactions: List<ByteArray>,
    request: WalletRequest = SignTxRequest(purpose = "signAndSend")
  ): List<String>
}
