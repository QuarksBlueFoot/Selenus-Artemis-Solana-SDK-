package com.selenus.artemis.wallet.mwa

import android.app.Activity
import android.net.Uri
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.wallet.BatchSendResult
import com.selenus.artemis.wallet.SendTransactionOptions
import com.selenus.artemis.wallet.SendTransactionResult
import com.selenus.artemis.wallet.WalletAdapter
import com.selenus.artemis.wallet.WalletAdapterSignAndSend
import com.selenus.artemis.wallet.WalletCapabilities
import com.selenus.artemis.wallet.WalletRequest
import com.selenus.artemis.wallet.mwa.protocol.MwaClient
import com.selenus.artemis.wallet.mwa.protocol.MwaCapabilities
import com.selenus.artemis.wallet.mwa.protocol.MwaIdentity
import com.selenus.artemis.wallet.mwa.protocol.MwaSendOptions
import com.selenus.artemis.wallet.mwa.protocol.MwaSession
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult


/**
 * MwaWalletAdapter
 *
 * Native Mobile Wallet Adapter (MWA 2.x) client implementation with:
 * - feature detection (get_capabilities)
 * - fallback routing between sign-only and sign-and-send
 */
class MwaWalletAdapter(
  private val activity: Activity,
  private val identityUri: Uri,
  private val iconPath: String,
  private val identityName: String,
  private val chain: String = "solana:mainnet",
  private val authStore: AuthTokenStore = InMemoryAuthTokenStore(),
  private val client: MwaClient = MwaClient()
) : WalletAdapter, WalletAdapterSignAndSend {

  @Volatile private var pk: Pubkey? = null
  @Volatile private var session: MwaSession? = null
  @Volatile private var caps: MwaCapabilities? = null

  override val publicKey: Pubkey
    get() = pk ?: throw IllegalStateException("Wallet not connected. Call connect() first.")

  override suspend fun getCapabilities(): WalletCapabilities {
    // Expose Artemis capabilities (not MWA capabilities).
    val c = ensureMwaCapabilities()
    return WalletCapabilities.defaultMobile().copy(
      supportsPreAuthorize = true,
      supportsMultipleMessages = true
    )
  }

  suspend fun connect(): Pubkey {
    val (s, _) = client.openSession(activity)
    session = s

    // First handshake method apps call. Load wallet caps early.
    caps = client.getCapabilities(s)

    val identity = MwaIdentity(
      uri = identityUri.toString(),
      icon = iconPath,
      name = identityName
    )

    val res = client.authorize(
      session = s,
      identity = identity,
      chain = chain,
      authToken = authStore.get()
    )
    authStore.set(res.authToken)

    val first = res.accounts.firstOrNull() ?: throw IllegalStateException("No accounts returned by wallet")
    val addr = java.util.Base64.getDecoder().decode(first.address)
    val out = Pubkey(addr)
    pk = out
    return out
  }

  suspend fun connectWithSignIn(payload: MwaSignInPayload): MwaSignInResult {
    val (s, _) = client.openSession(activity)
    session = s

    caps = client.getCapabilities(s)

    val identity = MwaIdentity(
      uri = identityUri.toString(),
      icon = iconPath,
      name = identityName
    )

    val res = client.authorize(
      session = s,
      identity = identity,
      chain = chain,
      authToken = authStore.get(),
      signInPayload = payload
    )
    authStore.set(res.authToken)
    
    val first = res.accounts.firstOrNull() ?: throw IllegalStateException("No accounts returned by wallet")
    val addr = java.util.Base64.getDecoder().decode(first.address)
    pk = Pubkey(addr)

    return res.signInResult ?: throw IllegalStateException("Wallet connected but returned no Sign In result")
  }

  override suspend fun signMessage(message: ByteArray, request: WalletRequest): ByteArray {
    // For MWA we treat WalletAdapter.signMessage as "sign tx bytes".
    if (pk == null || session == null) connect()
    val s = session ?: throw IllegalStateException("MWA session not ready")
    val c = ensureMwaCapabilities()

    // If wallet cannot sign transactions, fail loudly.
    if (!c.supportsSignTransactions()) {
      throw IllegalStateException("Wallet does not support sign_transactions")
    }
    return client.signTransactions(s, listOf(message)).first()
  }

  override suspend fun signMessages(messages: List<ByteArray>, request: WalletRequest): List<ByteArray> {
  if (pk == null || session == null) connect()
  val s = session ?: throw IllegalStateException("MWA session not ready")
  val c = ensureMwaCapabilities()
  if (!c.supportsSignTransactions()) {
    throw IllegalStateException("Wallet does not support sign_transactions")
  }

  val max = c.maxMessagesPerRequest ?: c.maxTransactionsPerRequest ?: messages.size
  val chunks = chunk(messages, max)
  val out = ArrayList<ByteArray>(messages.size)
  for (part in chunks) {
    out.addAll(client.signTransactions(s, part))
  }
  return out
}

  override suspend fun signArbitraryMessage(message: ByteArray, request: WalletRequest): ByteArray {
    if (pk == null || session == null) connect()
    val s = session ?: throw IllegalStateException("MWA session not ready")
    
    // MWA 2.0 spec: sign_messages
    val address = pk?.bytes ?: throw IllegalStateException("Wallet not connected")
    
    // MWA sign_messages takes a list of payloads and a list of addresses.
    // We are signing one message with one address.
    val signatures = client.signMessages(s, listOf(message), listOf(address))
    return signatures.first()
  }

  /**
   * signAndSendTransaction (Single Transaction)
   *
   * Signs and sends a single transaction via MWA, returning a structured result.
   * This is the primary method for the new WalletAdapterSignAndSend interface.
   */
  override suspend fun signAndSendTransaction(
    transaction: ByteArray,
    options: SendTransactionOptions
  ): SendTransactionResult {
    if (pk == null || session == null) connect()
    val s = session ?: throw IllegalStateException("MWA session not ready")
    val c = ensureMwaCapabilities()

    return try {
      if (c.supportsSignAndSend()) {
        // Use MWA sign-and-send with options mapped to MwaSendOptions
        val mwaOptions = mapToMwaOptions(options)
        val sigs = client.signAndSend(s, listOf(transaction), options = mwaOptions)
        val signature = sigs.firstOrNull() ?: ""
        SendTransactionResult(
          signature = signature,
          confirmed = options.waitForConfirmation,
          slot = null,
          error = null
        )
      } else {
        // Fallback: sign only, no broadcast - caller needs to handle sending
        client.signTransactions(s, listOf(transaction))
        SendTransactionResult(
          signature = "",
          confirmed = false,
          slot = null,
          error = "Wallet does not support sign-and-send. Transaction signed but not broadcast."
        )
      }
    } catch (e: Exception) {
      SendTransactionResult(
        signature = "",
        confirmed = false,
        slot = null,
        error = e.message ?: "Unknown error during signAndSendTransaction"
      )
    }
  }

  /**
   * signAndSendTransactions (Batch with Options)
   *
   * Signs and sends multiple transactions via MWA, returning structured batch results.
   * Supports ordered/dependent transactions via waitForCommitmentToSendNextTransaction.
   */
  override suspend fun signAndSendTransactions(
    transactions: List<ByteArray>,
    options: SendTransactionOptions
  ): BatchSendResult {
    if (transactions.isEmpty()) {
      return BatchSendResult(results = emptyList())
    }

    if (pk == null || session == null) connect()
    val s = session ?: throw IllegalStateException("MWA session not ready")
    val c = ensureMwaCapabilities()

    val max = c.maxTransactionsPerRequest ?: transactions.size
    val parts = chunk(transactions, max)
    val results = ArrayList<SendTransactionResult>(transactions.size)

    if (c.supportsSignAndSend()) {
      val mwaOptions = mapToMwaOptions(options)
      
      for (part in parts) {
        try {
          val sigs = client.signAndSend(s, part, options = mwaOptions)
          // Map each signature to a result
          for ((index, sig) in sigs.withIndex()) {
            results.add(SendTransactionResult(
              signature = sig,
              confirmed = options.waitForConfirmation,
              slot = null,
              error = null
            ))
          }
        } catch (e: Exception) {
          // Mark all transactions in this batch as failed
          for (i in part.indices) {
            results.add(SendTransactionResult(
              signature = "",
              confirmed = false,
              slot = null,
              error = e.message ?: "Batch send failed"
            ))
          }
        }
      }
    } else {
      // Fallback: sign only
      for (part in parts) {
        try {
          client.signTransactions(s, part)
          for (i in part.indices) {
            results.add(SendTransactionResult(
              signature = "",
              confirmed = false,
              slot = null,
              error = "Wallet does not support sign-and-send. Signed but not broadcast."
            ))
          }
        } catch (e: Exception) {
          for (i in part.indices) {
            results.add(SendTransactionResult(
              signature = "",
              confirmed = false,
              slot = null,
              error = e.message ?: "Sign failed"
            ))
          }
        }
      }
    }

    return BatchSendResult(results = results)
  }

  /**
   * Maps Artemis SendTransactionOptions to MWA-specific options.
   */
  private fun mapToMwaOptions(options: SendTransactionOptions): MwaSendOptions? {
    // Create MWA options from Artemis options
    return MwaSendOptions(
      minContextSlot = options.minContextSlot,
      skipPreflight = options.skipPreflight,
      maxRetries = options.maxRetries,
      preflightCommitment = options.preflightCommitment.name.lowercase()
    )
  }

  /**
   * signAndSendTransactions (Legacy)
   *
   * If the wallet supports sign-and-send, use it and return signatures.
   * Otherwise fall back to sign-only and return an empty signature list.
   *
   * For the fallback path, use MwaFallbackRouter.sendViaRpc(rpc, signedTxBytes) to broadcast with Artemis RPC.
   */
  @Deprecated("Use signAndSendTransactions with SendTransactionOptions instead")
  suspend fun signAndSendTransactionsLegacy(
  transactions: List<ByteArray>,
  request: WalletRequest
): List<String> {
  if (pk == null || session == null) connect()
  val s = session ?: throw IllegalStateException("MWA session not ready")
  val c = ensureMwaCapabilities()

  val max = c.maxTransactionsPerRequest ?: transactions.size
  val parts = chunk(transactions, max)

  if (c.supportsSignAndSend()) {
    val sigs = ArrayList<String>(transactions.size)
    for (part in parts) {
      sigs.addAll(client.signAndSend(s, part, options = null))
    }
    return sigs
  }

  // Sign-only fallback (caller can broadcast). We still perform signing in batches here.
  for (part in parts) {
    client.signTransactions(s, part)
  }
  return emptyList()
}


  suspend fun signThenSendViaRpc(
  rpcSend: suspend (ByteArray) -> String,
  transactions: List<ByteArray>,
  options: MwaSendOptions? = null
): List<String> {
  if (pk == null || session == null) connect()
  val s = session ?: throw IllegalStateException("MWA session not ready")
  val c = ensureMwaCapabilities()

  val max = c.maxTransactionsPerRequest ?: transactions.size
  val parts = chunk(transactions, max)

  // Prefer wallet broadcast when it is supported.
  if (c.supportsSignAndSend()) {
    val sigs = ArrayList<String>(transactions.size)
    for (part in parts) {
      sigs.addAll(client.signAndSend(s, part, options = options))
    }
    return sigs
  }

  // Otherwise: sign-only and broadcast through provided RPC callback.
  val sigs = ArrayList<String>(transactions.size)
  for (part in parts) {
    val signed = client.signTransactions(s, part)
    for (tx in signed) {
      sigs.add(rpcSend(tx))
    }
  }
  return sigs
}


private fun <T> chunk(list: List<T>, maxSize: Int): List<List<T>> {
  if (maxSize <= 0) return listOf(list)
  if (list.isEmpty()) return emptyList()
  if (list.size <= maxSize) return listOf(list)
  val out = ArrayList<List<T>>()
  var i = 0
  while (i < list.size) {
    val end = minOf(i + maxSize, list.size)
    out.add(list.subList(i, end))
    i = end
  }
  return out
}

  private suspend fun ensureMwaCapabilities(): MwaCapabilities {
    val s = session ?: run {
      connect()
      return caps ?: throw IllegalStateException("MWA capabilities not available")
    }
    val current = caps
    if (current != null) return current
    val loaded = client.getCapabilities(s)
    caps = loaded
    return loaded
  }

  suspend fun reauthorize() {
    val s = session ?: throw IllegalStateException("MWA session not ready")
    val token = authStore.get() ?: throw IllegalStateException("No auth token to reauthorize")

    val identity = MwaIdentity(
      uri = identityUri.toString(),
      icon = iconPath,
      name = identityName
    )

    val res = client.reauthorize(s, identity, token)
    authStore.set(res.authToken)
  }

  suspend fun deauthorize() {
    val s = session
    val token = authStore.get()
    if (s != null && token != null) {
      try {
        client.deauthorize(s, token)
      } catch (_: Exception) {
        // Best effort
      }
    }
    authStore.set(null)
    session = null
    pk = null
    caps = null
  }

  /**
   * Sign off-chain messages (e.g. authentication challenges).
   * This maps to MWA `sign_messages`.
   */
  suspend fun signOffChainMessages(messages: List<ByteArray>): List<ByteArray> {
    if (pk == null || session == null) connect()
    val s = session ?: throw IllegalStateException("MWA session not ready")
    val currentPk = pk ?: throw IllegalStateException("Wallet not connected")

    // MWA expects a list of addresses corresponding to each message.
    // We assume all messages are signed by the connected wallet.
    val addresses = List(messages.size) { currentPk.bytes }

    return client.signMessages(s, messages, addresses)
  }
}
