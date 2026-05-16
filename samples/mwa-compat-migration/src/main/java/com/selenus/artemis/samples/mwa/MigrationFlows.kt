package com.selenus.artemis.samples.mwa

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.mwa.DataStoreAuthTokenStore
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AuthorizationResult
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionBatchResult
import com.solana.mobilewalletadapter.clientlib.TransactionParams
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.programs.SystemProgram
import com.solana.publickey.asWeb3Solana
import com.solana.transaction.Message

class CompatMigrationFlow(
  private val adapter: MobileWalletAdapter = MobileWalletAdapter(defaultIdentity(), Solana.Devnet)
) {
  suspend fun authorize(sender: ActivityResultSender): TransactionResult<AuthorizationResult> =
    adapter.transact(sender) { authResult -> authResult }

  @Suppress("DEPRECATION")
  suspend fun signMessage(
    sender: ActivityResultSender,
    message: ByteArray
  ): TransactionResult<Int> = adapter.transact(sender) { authResult ->
    val signer = authResult.accounts.firstOrNull()?.publicKey ?: authResult.publicKey
    signMessagesDetached(arrayOf(message), arrayOf(signer)).messages.sumOf { signed ->
      signed.signatures.size
    }
  }

  suspend fun signAndSend(
    sender: ActivityResultSender,
    transaction: ByteArray
  ): TransactionResult<TransactionBatchResult> = adapter.transact(sender) {
    signAndSendTransactionsBatch(
      transactions = arrayOf(transaction),
      params = TransactionParams(commitment = "confirmed", skipPreflight = false)
    )
  }

  companion object {
    fun defaultIdentity(): ConnectionIdentity = ConnectionIdentity(
      identityUri = Uri.parse("https://artemis.selenus.dev"),
      iconUri = Uri.parse("https://artemis.selenus.dev/favicon.ico"),
      identityName = "Artemis MWA Migration"
    )
  }
}

object NativeMigrationFlow {
  fun adapter(activity: Activity): MwaWalletAdapter = MwaWalletAdapter(
    activity = activity,
    identityUri = Uri.parse("https://artemis.selenus.dev"),
    iconPath = "https://artemis.selenus.dev/favicon.ico",
    identityName = "Artemis Native MWA",
    chain = "solana:devnet",
    authStore = DataStoreAuthTokenStore.from(activity)
  )

  suspend fun connect(activity: Activity): Pubkey = adapter(activity).connect()
}

object MixedMigrationFlow {
  fun buildLamportTransferMessage(
    feePayer: Pubkey,
    recipient: Pubkey,
    recentBlockhash: String,
    lamports: Long
  ): ByteArray {
    val from = feePayer.asWeb3Solana()
    val to = recipient.asWeb3Solana()
    return Message.Builder()
      .addFeePayer(from)
      .setRecentBlockhash(recentBlockhash)
      .addInstruction(SystemProgram.transfer(from, to, lamports))
      .build()
      .serialize()
  }
}