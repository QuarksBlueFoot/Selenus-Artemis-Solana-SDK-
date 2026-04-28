/*
 * Drop-in source compatibility for the upstream walletlib request
 * types. Each upstream class is a thin wrapper around an inbound
 * JSON-RPC verb with `completeWith*` methods that the wallet UI
 * eventually calls. The shim mirrors the same shape and forwards
 * every completion to the Artemis request object the dispatcher
 * actually awaits.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.scenario

import android.net.Uri
import com.selenus.artemis.wallet.mwa.walletlib.AuthorizeRequest as ArtemisAuthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.AuthorizedAccount as ArtemisAuthorizedAccount
import com.selenus.artemis.wallet.mwa.walletlib.DeauthorizedEvent as ArtemisDeauthorizedEvent
import com.selenus.artemis.wallet.mwa.walletlib.ReauthorizeRequest as ArtemisReauthorizeRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignAndSendTransactionsRequest as ArtemisSignAndSendTransactionsRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignInResult as ArtemisSignInResult
import com.selenus.artemis.wallet.mwa.walletlib.SignMessagesRequest as ArtemisSignMessagesRequest
import com.selenus.artemis.wallet.mwa.walletlib.SignTransactionsRequest as ArtemisSignTransactionsRequest

class AuthorizedAccount(
    @JvmField val publicKey: ByteArray,
    @JvmField val accountLabel: String?,
    @JvmField val displayAddress: String?,
    @JvmField val displayAddressFormat: String?,
    @JvmField val accountIcon: Uri?,
    @JvmField val chains: Array<String>?,
    @JvmField val features: Array<String>?
) {
    internal fun toArtemis(): ArtemisAuthorizedAccount = ArtemisAuthorizedAccount(
        publicKey = publicKey,
        displayAddress = displayAddress,
        displayAddressFormat = displayAddressFormat,
        accountLabel = accountLabel,
        accountIcon = accountIcon,
        chains = chains?.toList() ?: emptyList(),
        features = features?.toList() ?: emptyList()
    )

    companion object {
        @JvmStatic
        internal fun fromArtemis(art: ArtemisAuthorizedAccount): AuthorizedAccount =
            AuthorizedAccount(
                publicKey = art.publicKey,
                accountLabel = art.accountLabel,
                displayAddress = art.displayAddress,
                displayAddressFormat = art.displayAddressFormat,
                accountIcon = art.accountIcon,
                chains = art.chains.toTypedArray().takeIf { it.isNotEmpty() },
                features = art.features.toTypedArray().takeIf { it.isNotEmpty() }
            )
    }
}

class SignInResult(
    @JvmField val publicKey: ByteArray,
    @JvmField val signedMessage: ByteArray,
    @JvmField val signature: ByteArray,
    @JvmField val signatureType: String
) {
    internal fun toArtemis(): ArtemisSignInResult = ArtemisSignInResult(
        publicKey = publicKey,
        signedMessage = signedMessage,
        signature = signature,
        signatureType = signatureType
    )
}

class AuthorizeRequest internal constructor(
    private val artemis: ArtemisAuthorizeRequest
) {
    @JvmField val identityName: String? = artemis.identityName
    @JvmField val identityUri: Uri? = artemis.identityUri
    @JvmField val iconRelativeUri: Uri? = artemis.iconRelativeUri
    @JvmField val chain: String? = artemis.chain
    @JvmField val features: Array<String> = artemis.features.toTypedArray()
    @JvmField val addresses: Array<ByteArray>? = artemis.addresses?.toTypedArray()
    /**
     * Bridges the wallet-side typed payload onto the upstream
     * `SignInWithSolana.Payload` shape callers already import from
     * `:artemis-mwa-common-compat`. Null when the dApp didn't include
     * a SIWS challenge.
     */
    @JvmField
    val signInPayload: com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload? =
        artemis.signInPayload?.let { p ->
            com.solana.mobilewalletadapter.common.protocol.SignInWithSolana.Payload(
                domain = p.domain,
                address = p.address,
                statement = p.statement,
                uri = p.uri,
                version = p.version,
                chainId = p.chainId,
                nonce = p.nonce,
                issuedAt = p.issuedAt,
                expirationTime = p.expirationTime,
                notBefore = p.notBefore,
                requestId = p.requestId,
                resources = p.resources.toTypedArray().takeIf { it.isNotEmpty() }
            )
        }

    /**
     * Legacy `cluster` field for MWA 1.x callers. Derived from [chain]
     * via the chain → cluster table; new code reads [chain] directly.
     */
    @Deprecated("Use chain.", ReplaceWith("chain"))
    @JvmField
    val cluster: String? = chain?.let {
        com.selenus.artemis.wallet.mwa.walletlib.ProtocolContract.clusterForChain(it) ?: it
    }

    @JvmOverloads
    fun completeWithAuthorize(
        accounts: Array<AuthorizedAccount>,
        walletUriBase: Uri? = null,
        scope: ByteArray = byteArrayOf(),
        signInResult: SignInResult? = null
    ) = artemis.completeWithAuthorize(
        accounts = accounts.map { it.toArtemis() },
        walletUriBase = walletUriBase,
        scope = scope,
        signInResult = signInResult?.toArtemis()
    )

    /**
     * Single-account legacy overload. MWA 1.x walletlib accepted the
     * raw byte[] publicKey + accountLabel directly; new code passes a
     * full [AuthorizedAccount].
     */
    @Deprecated(
        "Use the AuthorizedAccount[] overload.",
        ReplaceWith("completeWithAuthorize(arrayOf(AuthorizedAccount(publicKey, accountLabel, null, null, null, null, null)), walletUriBase, scope)")
    )
    @JvmOverloads
    fun completeWithAuthorize(
        publicKey: ByteArray,
        accountLabel: String? = null,
        walletUriBase: Uri? = null,
        scope: ByteArray = byteArrayOf()
    ) = completeWithAuthorize(
        accounts = arrayOf(
            AuthorizedAccount(
                publicKey = publicKey,
                accountLabel = accountLabel,
                displayAddress = null,
                displayAddressFormat = null,
                accountIcon = null,
                chains = null,
                features = null
            )
        ),
        walletUriBase = walletUriBase,
        scope = scope,
        signInResult = null
    )

    fun completeWithDecline() = artemis.completeWithDecline()
    fun completeWithChainNotSupported() = artemis.completeWithChainNotSupported()

    /**
     * Legacy alias kept for MWA 1.x callers that catch / call by the
     * old name. Routes to [completeWithChainNotSupported].
     */
    @Deprecated(
        "Use completeWithChainNotSupported().",
        ReplaceWith("completeWithChainNotSupported()")
    )
    fun completeWithClusterNotSupported() = completeWithChainNotSupported()
}

class ReauthorizeRequest internal constructor(
    private val artemis: ArtemisReauthorizeRequest
) {
    @JvmField val identityName: String? = artemis.identityName
    @JvmField val identityUri: Uri? = artemis.identityUri
    @JvmField val iconRelativeUri: Uri? = artemis.iconRelativeUri
    @JvmField val authToken: String = artemis.authToken
    /** CAIP-2 chain identifier the dApp asked to reauthorize against. */
    @JvmField val chain: String = artemis.chain
    /** Wallet-private scope bytes bound to this auth_token at issuance. */
    @JvmField val authorizationScope: ByteArray = artemis.authorizationScope

    /**
     * Legacy `cluster` string for MWA 1.x callers. Derived from [chain]
     * via the chain → cluster table; new code reads [chain] directly.
     */
    @Deprecated("Use chain.", ReplaceWith("chain"))
    @JvmField
    val cluster: String =
        com.selenus.artemis.wallet.mwa.walletlib.ProtocolContract.clusterForChain(chain) ?: chain

    fun completeWithReauthorize() = artemis.completeWithReauthorize()
    fun completeWithDecline() = artemis.completeWithDecline()
}

class SignTransactionsRequest internal constructor(
    private val artemis: ArtemisSignTransactionsRequest
) {
    @JvmField val payloads: Array<ByteArray> = artemis.payloads.toTypedArray()
    @JvmField val authorizedAccounts: Array<AuthorizedAccount> =
        artemis.authorizedAccounts.map { AuthorizedAccount.fromArtemis(it) }.toTypedArray()
    @JvmField val identityName: String? = artemis.identityName
    @JvmField val identityUri: Uri? = artemis.identityUri
    @JvmField val iconRelativeUri: Uri? = artemis.iconRelativeUri
    @Deprecated(
        "Use authorizedAccounts[0].publicKey.",
        ReplaceWith("authorizedAccounts[0].publicKey")
    )
    @JvmField
    val publicKey: ByteArray = authorizedAccounts.firstOrNull()?.publicKey ?: ByteArray(0)

    fun completeWithSignedPayloads(signedPayloads: Array<ByteArray>) =
        artemis.completeWithSignedPayloads(signedPayloads.toList())

    fun completeWithDecline() = artemis.completeWithDecline()

    fun completeWithInvalidPayloads(valid: BooleanArray) =
        artemis.completeWithInvalidPayloads(valid.toList())

    fun completeWithTooManyPayloads() = artemis.completeWithTooManyPayloads()
    fun completeWithAuthorizationNotValid() = artemis.completeWithAuthorizationNotValid()
}

class SignMessagesRequest internal constructor(
    private val artemis: ArtemisSignMessagesRequest
) {
    @JvmField val payloads: Array<ByteArray> = artemis.payloads.toTypedArray()
    @JvmField val addresses: Array<ByteArray> = artemis.addresses.toTypedArray()
    @JvmField val authorizedAccounts: Array<AuthorizedAccount> =
        artemis.authorizedAccounts.map { AuthorizedAccount.fromArtemis(it) }.toTypedArray()
    @JvmField val identityName: String? = artemis.identityName
    @JvmField val identityUri: Uri? = artemis.identityUri
    @JvmField val iconRelativeUri: Uri? = artemis.iconRelativeUri
    @Deprecated(
        "Use authorizedAccounts[0].publicKey.",
        ReplaceWith("authorizedAccounts[0].publicKey")
    )
    @JvmField
    val publicKey: ByteArray = authorizedAccounts.firstOrNull()?.publicKey ?: ByteArray(0)

    fun completeWithSignedPayloads(signedPayloads: Array<ByteArray>) =
        artemis.completeWithSignedPayloads(signedPayloads.toList())

    fun completeWithDecline() = artemis.completeWithDecline()

    fun completeWithInvalidPayloads(valid: BooleanArray) =
        artemis.completeWithInvalidPayloads(valid.toList())

    fun completeWithTooManyPayloads() = artemis.completeWithTooManyPayloads()
    fun completeWithAuthorizationNotValid() = artemis.completeWithAuthorizationNotValid()
}

class SignAndSendTransactionsRequest internal constructor(
    private val artemis: ArtemisSignAndSendTransactionsRequest
) {
    @JvmField val payloads: Array<ByteArray> = artemis.payloads.toTypedArray()
    @JvmField val authorizedAccounts: Array<AuthorizedAccount> =
        artemis.authorizedAccounts.map { AuthorizedAccount.fromArtemis(it) }.toTypedArray()
    @JvmField val identityName: String? = artemis.identityName
    @JvmField val identityUri: Uri? = artemis.identityUri
    @JvmField val iconRelativeUri: Uri? = artemis.iconRelativeUri
    @JvmField val minContextSlot: Int? = artemis.minContextSlot
    @JvmField val commitment: String? = artemis.commitment
    @JvmField val skipPreflight: Boolean? = artemis.skipPreflight
    @JvmField val maxRetries: Int? = artemis.maxRetries
    @JvmField val waitForCommitmentToSendNextTransaction: Boolean? =
        artemis.waitForCommitmentToSendNextTransaction

    /**
     * Legacy `publicKey` accessor: the first authorized account's
     * `publicKey`. MWA 1.x walletlib exposed this on every signing
     * request; new code iterates [authorizedAccounts] directly.
     */
    @Deprecated(
        "Use authorizedAccounts[0].publicKey or iterate authorizedAccounts.",
        ReplaceWith("authorizedAccounts[0].publicKey")
    )
    @JvmField
    val publicKey: ByteArray = authorizedAccounts.firstOrNull()?.publicKey ?: ByteArray(0)

    fun completeWithSignatures(signatures: Array<ByteArray>) =
        artemis.completeWithSignatures(signatures.toList())

    fun completeWithDecline() = artemis.completeWithDecline()

    fun completeWithInvalidSignatures(valid: BooleanArray) =
        artemis.completeWithInvalidSignatures(valid.toList())

    fun completeWithNotSubmitted(signatures: Array<ByteArray?>) =
        artemis.completeWithNotSubmitted(signatures.toList())

    fun completeWithTooManyPayloads() = artemis.completeWithTooManyPayloads()
    fun completeWithAuthorizationNotValid() = artemis.completeWithAuthorizationNotValid()
}

class DeauthorizedEvent internal constructor(
    private val artemis: ArtemisDeauthorizedEvent
) {
    @JvmField val authToken: String = artemis.authToken

    fun complete() = artemis.complete()
}
