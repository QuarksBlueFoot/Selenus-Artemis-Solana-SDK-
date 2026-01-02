package com.selenus.artemis.wallet.mwa

import com.selenus.artemis.rpc.RpcApi

/**
 * MwaFallbackRouter
 *
 * Convenience helpers for the sign-only fallback case.
 */
object MwaFallbackRouter {

  suspend fun signThenSend(
    wallet: MwaWalletAdapter,
    rpc: RpcApi,
    signedTxBytes: List<ByteArray>
  ): List<String> {
    // Caller already has signed transactions, so this just broadcasts.
    return signedTxBytes.map { rpc.sendRawTransaction(it) }
  }
}
