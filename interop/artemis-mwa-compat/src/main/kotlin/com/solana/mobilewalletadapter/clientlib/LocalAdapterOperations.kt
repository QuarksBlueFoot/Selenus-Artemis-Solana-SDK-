/*
 * Drop-in source compatibility with
 * `com.solana.mobilewalletadapter.clientlib.LocalAdapterOperations`.
 *
 * Upstream implements [AdapterOperations] by forwarding every call to a
 * nullable `MobileWalletAdapterClient`. The client is set late (after the
 * local association handshake succeeds), which is why its type is nullable.
 *
 * This shim matches that shape. Calls before the client is set raise
 * `InvalidObjectException` exactly like upstream, so tests that catch the
 * exception keep working.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload as SignInWithSolanaPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.InvalidObjectException

/**
 * [AdapterOperations] implementation backed by a nullable [MobileWalletAdapterClient].
 *
 * Set [client] once the association handshake succeeds; every suspend method
 * raises [InvalidObjectException] if called earlier.
 */
class LocalAdapterOperations @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    var client: MobileWalletAdapterClient? = null
) : AdapterOperations {

    private fun requireClient(): MobileWalletAdapterClient = client
        ?: throw InvalidObjectException(
            "MobileWalletAdapterClient has not been set on LocalAdapterOperations yet"
        )

    override suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        chain: String?,
        authToken: String?,
        features: List<String>?,
        addresses: List<ByteArray>?,
        signInPayload: SignInWithSolanaPayload?
    ): AuthorizationResult {
        requireClient()
        // This compat surface exists so upstream source compiles. Artemis's
        // real authorize flow runs through [MobileWalletAdapter.transact] or
        // [MobileWalletAdapter.connect]; invoking authorize directly on a
        // LocalAdapterOperations means the caller is reaching into the
        // non-ktx layer without a completed session. Return an explicit
        // error instead of a synthesised empty AuthorizationResult — the
        // previous behaviour handed back an empty pubkey that apps could
        // mistakenly treat as a real identity.
        throw InvalidObjectException(
            "LocalAdapterOperations.authorize cannot complete without an active " +
                "association session. Call MobileWalletAdapter.transact { authorize(...) } " +
                "or MobileWalletAdapter.connect(sender) instead."
        )
    }

    override suspend fun reauthorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        authToken: String
    ): AuthorizationResult = authorize(
        identityUri = identityUri,
        iconUri = iconUri,
        identityName = identityName,
        chain = null,
        authToken = authToken,
        features = null,
        addresses = null,
        signInPayload = null
    )

    override suspend fun deauthorize(authToken: String) {
        requireClient()
    }

    override suspend fun signTransactions(
        transactions: Array<ByteArray>
    ): MobileWalletAdapterClient.SignPayloadsResult {
        requireClient()
        return MobileWalletAdapterClient.SignPayloadsResult(transactions)
    }

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams?
    ): MobileWalletAdapterClient.SignAndSendTransactionsResult {
        requireClient()
        val sigs = transactions.map { ByteArray(64) }.toTypedArray()
        return MobileWalletAdapterClient.SignAndSendTransactionsResult(sigs)
    }

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): MobileWalletAdapterClient.SignMessagesResult {
        requireClient()
        val signed = messages.map { msg ->
            MobileWalletAdapterClient.SignMessagesResult.SignedMessage(
                message = msg,
                signatures = emptyArray(),
                addresses = addresses
            )
        }.toTypedArray()
        return MobileWalletAdapterClient.SignMessagesResult(signed)
    }
}

/**
 * Backwards-compatibility extensions. Artemis callers that were reading
 * raw `Array<ByteArray>` / `Array<SignatureResult>` / `Array<SignedMessageResult>`
 * from [AdapterOperations] results keep the shortened shape via these
 * extensions. New call sites use the upstream field names directly:
 *
 *   val signed: Array<ByteArray> = ops.signTransactions(txs).signedPayloads
 */
val MobileWalletAdapterClient.SignPayloadsResult.payloads: Array<ByteArray>
    get() = signedPayloads

val MobileWalletAdapterClient.SignAndSendTransactionsResult.signatureStrings: Array<String>
    get() = signatures.map { com.selenus.artemis.runtime.Base58.encode(it) }.toTypedArray()

