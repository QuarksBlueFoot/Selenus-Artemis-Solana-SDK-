/*
 * Session bridge that turns an Artemis [MwaWalletAdapter] into a
 * [MobileWalletAdapterClient.SessionBridge]. Installed by [MobileWalletAdapterCompat]
 * when the ktx `transact` block opens, so that anything touching the
 * underlying [MobileWalletAdapterClient] (including code that reaches into
 * the non-ktx API through [scenario.LocalAssociationScenario.getMobileWalletAdapterClient])
 * sees live behaviour instead of the placeholder futures.
 *
 * The bridge speaks the CompletableFuture/Array shape the upstream Java
 * client expects, so no source-level changes are required on downstream
 * callers. Internally every call forwards to the Artemis adapter, which
 * speaks MWA 2.0 to the real wallet via the established WebSocket session.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.selenus.artemis.wallet.mwa.protocol.MwaAccount
import com.selenus.artemis.wallet.mwa.protocol.MwaAuthorizeResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SignInWithSolana
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

class MwaSessionBridge internal constructor(
    private val adapter: MwaWalletAdapter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MobileWalletAdapterClient.SessionBridge {

    override fun authorize(
        identityUri: Uri?,
        iconUri: Uri?,
        identityName: String?,
        chain: String?,
        authToken: String?,
        features: Array<String>?,
        addresses: Array<ByteArray>?,
        signInPayload: SignInWithSolana.Payload?
    ): CompletableFuture<MobileWalletAdapterClient.AuthorizationResult> = scope.future {
        if (signInPayload != null) {
            adapter.connectWithSignIn(signInPayload.toMwa())
        } else if (features != null || addresses != null) {
            adapter.connectWithFeatures(
                requestedFeatures = features?.toList(),
                addresses = addresses?.toList()
            )
        } else {
            adapter.connect()
        }
        val raw = adapter.lastAuthorization
            ?: error("adapter completed connect but did not expose an authorize result")
        raw.toAuthorizationResult()
    }

    override fun deauthorize(authToken: String): CompletableFuture<Void?> = scope.future {
        runCatching { adapter.deauthorize() }
        null
    }

    override fun getCapabilities(): CompletableFuture<MobileWalletAdapterClient.GetCapabilitiesResult> =
        scope.future {
            val c = adapter.getMwaCapabilities()
            MobileWalletAdapterClient.GetCapabilitiesResult(
                supportsCloneAuthorization = c.supportsCloneAuth(),
                supportsSignAndSendTransactions = c.supportsSignAndSend(),
                maxTransactionsPerSigningRequest = c.maxTransactionsPerRequest ?: 0,
                maxMessagesPerSigningRequest = c.maxMessagesPerRequest ?: 0,
                supportedTransactionVersions = buildList<Any> {
                    if (c.supportsLegacyTransactions()) add("legacy")
                    if (c.supportsVersionedTransactions()) add(0)
                }.toTypedArray(),
                supportedOptionalFeatures = c.allFeatures().toTypedArray()
            )
        }

    override fun signTransactions(
        transactions: Array<ByteArray>
    ): CompletableFuture<MobileWalletAdapterClient.SignPayloadsResult> = scope.future {
        val signed = adapter.signMessages(
            transactions.toList(),
            com.selenus.artemis.wallet.SignTxRequest(purpose = "signTransactions")
        )
        MobileWalletAdapterClient.SignPayloadsResult(signed.toTypedArray())
    }

    override fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): CompletableFuture<MobileWalletAdapterClient.SignMessagesResult> = scope.future {
        val signed = messages.mapIndexed { i, msg ->
            val signerAddr = addresses.getOrNull(i) ?: addresses.first()
            val sig = adapter.signArbitraryMessage(
                msg,
                com.selenus.artemis.wallet.SignTxRequest(purpose = "signMessage")
            )
            MobileWalletAdapterClient.SignMessagesResult.SignedMessage(
                message = msg,
                signatures = arrayOf(sig),
                addresses = arrayOf(signerAddr)
            )
        }.toTypedArray()
        MobileWalletAdapterClient.SignMessagesResult(signed)
    }

    override fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        waitForCommitmentToSendNextTransaction: Boolean?
    ): CompletableFuture<MobileWalletAdapterClient.SignAndSendTransactionsResult> = scope.future {
        val options = com.selenus.artemis.wallet.SendTransactionOptions(
            skipPreflight = skipPreflight ?: false,
            maxRetries = maxRetries,
            minContextSlot = minContextSlot?.toLong(),
            waitForCommitmentToSendNextTransaction =
                waitForCommitmentToSendNextTransaction ?: false,
            commitment = mapCommitment(commitment)
                ?: com.selenus.artemis.wallet.Commitment.CONFIRMED
        )
        val batch = adapter.signAndSendTransactions(transactions.toList(), options)
        val signatures = batch.results.map { result ->
            com.selenus.artemis.runtime.Base58.decode(result.signature)
        }.toTypedArray()
        MobileWalletAdapterClient.SignAndSendTransactionsResult(signatures)
    }

    /** Stops the coroutine scope. Call when the session tears down. */
    fun close() {
        runCatching { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }

    private fun mapCommitment(wireValue: String?): com.selenus.artemis.wallet.Commitment? =
        when (wireValue?.lowercase()) {
            "processed" -> com.selenus.artemis.wallet.Commitment.PROCESSED
            "confirmed" -> com.selenus.artemis.wallet.Commitment.CONFIRMED
            "finalized" -> com.selenus.artemis.wallet.Commitment.FINALIZED
            null, "" -> null
            else -> null
        }

    private fun MwaAuthorizeResult.toAuthorizationResult(): MobileWalletAdapterClient.AuthorizationResult {
        val decoded = accounts.map { it.toAuthorizedAccount() }.toTypedArray()
        val signIn = signInResult?.let { sir ->
            MobileWalletAdapterClient.AuthorizationResult.SignInResult(
                publicKey = android.util.Base64.decode(sir.address, android.util.Base64.NO_WRAP),
                signedMessage = android.util.Base64.decode(sir.signedMessage, android.util.Base64.NO_WRAP),
                signature = android.util.Base64.decode(sir.signature, android.util.Base64.NO_WRAP),
                signatureType = sir.signatureType
            )
        }
        return MobileWalletAdapterClient.AuthorizationResult(
            authToken = authToken,
            accounts = decoded,
            walletUriBase = walletUriBase?.let { Uri.parse(it) },
            walletIcon = walletIcon?.let { Uri.parse(it) },
            signInResult = signIn
        )
    }

    private fun MwaAccount.toAuthorizedAccount(): MobileWalletAdapterClient.AuthorizationResult.AuthorizedAccount =
        MobileWalletAdapterClient.AuthorizationResult.AuthorizedAccount(
            publicKey = android.util.Base64.decode(address, android.util.Base64.NO_WRAP),
            accountLabel = label,
            chains = chains?.toTypedArray(),
            features = features?.toTypedArray(),
            displayAddress = displayAddress,
            displayAddressFormat = displayAddressFormat,
            icon = icon?.let { Uri.parse(it) }
        )

    companion object {
        /**
         * Factory that creates a bridge for [adapter] and installs it on the
         * scenario's [MobileWalletAdapterClient], so raw-clientlib call sites
         * (that is, anyone that didn't go through [MobileWalletAdapter])
         * still see a working client.
         */
        @JvmStatic
        fun attach(
            scenario: com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario,
            adapter: MwaWalletAdapter
        ): MwaSessionBridge {
            val bridge = MwaSessionBridge(adapter)
            scenario.getMobileWalletAdapterClient().installBridge(bridge)
            return bridge
        }
    }
}
