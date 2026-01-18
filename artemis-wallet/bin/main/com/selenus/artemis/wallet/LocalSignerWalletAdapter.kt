package com.selenus.artemis.wallet

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey

/**
 * LocalSignerWalletAdapter
 *
 * Reference WalletAdapter for server-side or test environments.
 * Uses an in-memory Ed25519 keypair.
 */
class LocalSignerWalletAdapter(
  private val keypair: Keypair
) : WalletAdapter {

  override val publicKey: Pubkey = keypair.publicKey

  override suspend fun getCapabilities(): WalletCapabilities {
    return WalletCapabilities(
      supportsReSign = true,
      supportsPartialSign = true,
      supportsFeePayerSwap = true,
      supportsMultipleMessages = true,
      supportsPreAuthorize = false
    )
  }

  override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
    // For local signing we return a detached signature + original message packed as:
    // [sigLen(2)][sig][msgLen(4)][msg]
    val sig = keypair.sign(message)
    val out = ByteArray(2 + sig.size + 4 + message.size)
    out[0] = ((sig.size shr 8) and 0xff).toByte()
    out[1] = (sig.size and 0xff).toByte()
    System.arraycopy(sig, 0, out, 2, sig.size)
    val ml = message.size
    val o = 2 + sig.size
    out[o] = ((ml shr 24) and 0xff).toByte()
    out[o + 1] = ((ml shr 16) and 0xff).toByte()
    out[o + 2] = ((ml shr 8) and 0xff).toByte()
    out[o + 3] = (ml and 0xff).toByte()
    System.arraycopy(message, 0, out, o + 4, message.size)
    return out
  }

  companion object {
    fun generate(): LocalSignerWalletAdapter = LocalSignerWalletAdapter(Keypair.generate())
  }
}
