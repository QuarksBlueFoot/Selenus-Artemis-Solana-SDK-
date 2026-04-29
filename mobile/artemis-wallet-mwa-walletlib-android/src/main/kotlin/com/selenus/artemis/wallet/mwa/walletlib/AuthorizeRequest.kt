package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * Wallet-side view of a `authorize` JSON-RPC request.
 *
 * The dispatcher constructs one of these per inbound request and hands
 * it to [Scenario.Callbacks.onAuthorizeRequest]. The wallet UI shows a
 * confirmation prompt, then calls one of the `completeWith*` methods.
 *
 * @property identityName dApp-supplied human-readable name, e.g.
 *   "Phantom Test Dapp". Surface as the prompt heading.
 * @property identityUri dApp's canonical URL. Use to fetch favicon,
 *   resolve a known-good display name, or reject blocklist hits.
 * @property iconRelativeUri Path relative to [identityUri] pointing at
 *   the dApp's icon. The wallet typically resolves this against
 *   [identityUri] before fetching.
 * @property chain CAIP-2 chain identifier the dApp wants to operate
 *   on, e.g. `solana:mainnet`. `null` means the dApp left it
 *   unspecified (legacy behaviour).
 * @property features Optional features the dApp wants the wallet to
 *   advertise.
 * @property addresses Specific accounts the dApp would like authorized.
 *   `null` means "any account the wallet picks". Wallets may refuse
 *   when the requested set excludes the user's selection.
 * @property signInPayload SIWS challenge bundled with the authorize.
 *   When non-null the wallet should sign it during this turn and
 *   return the result through [completeWithAuthorize].
 */
open class AuthorizeRequest internal constructor(
    val identityName: String?,
    val identityUri: Uri?,
    val iconRelativeUri: Uri?,
    val chain: String?,
    val features: List<String>,
    val addresses: List<ByteArray>?,
    val signInPayload: SignInPayload?
) : MwaRequest() {

    /**
     * Legacy `cluster` string for MWA 1.x callers. Use [chain] in new
     * code. Returns the matching cluster name when [chain] resolves to
     * one of `mainnet-beta` / `testnet` / `devnet`, otherwise echoes
     * [chain] back verbatim so callers always see a non-null string
     * when [chain] is set.
     */
    @Deprecated(
        "Use chain. cluster is preserved for MWA 1.x compatibility only.",
        ReplaceWith("chain")
    )
    val cluster: String?
        get() = chain?.let { ProtocolContract.clusterForChain(it) ?: it }

    /**
     * Approve and issue an auth token for [accounts].
     *
     * @param accounts Accounts the wallet decided to expose. The first
     *   entry's [AuthorizedAccount.publicKey] is the canonical signer.
     * @param walletUriBase Wallet endpoint URI hint that the dApp can
     *   reuse next session to skip the chooser. `null` for chooser-only
     *   wallets.
     * @param scope Opaque per-authorization scope bytes. Wallets that
     *   tag tokens with policy info put the bytes here; the dApp echoes
     *   them on subsequent authorize calls so the wallet can match.
     * @param signInResult When the request carried a [signInPayload],
     *   the produced SIWS proof. `null` otherwise.
     */
    fun completeWithAuthorize(
        accounts: List<AuthorizedAccount>,
        walletUriBase: Uri? = null,
        scope: ByteArray = byteArrayOf(),
        signInResult: SignInResult? = null
    ) {
        require(accounts.isNotEmpty()) { "completeWithAuthorize requires at least one account" }
        if (signInPayload != null && signInResult != null) {
            // The SIWS result must come from the same identity that the
            // wallet is about to authorize. Cross-check up front so a
            // miswired UI cannot ship a SIWS proof for a different
            // pubkey than the auth token covers, the dApp would later
            // fail signature verification, but with a confusing
            // "auth_token issued, but signature does not verify" error
            // pointing nowhere actionable.
            require(accounts.any { it.publicKey.contentEquals(signInResult.publicKey) }) {
                "signInResult.publicKey must match one of the authorized accounts"
            }
        }
        completeInternal(
            MwaCompletion.Result(
                AuthorizeApproved(
                    accounts = accounts,
                    walletUriBase = walletUriBase,
                    scope = scope,
                    signInResult = signInResult
                )
            )
        )
    }

    /**
     * User declined the request. Surfaced to the dApp as a
     * `AUTHORIZATION_FAILED` error.
     */
    fun completeWithDecline() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.AUTHORIZATION_FAILED,
                message = "authorization declined by user"
            )
        )
    }

    /**
     * Wallet does not support the requested [chain]. Surfaced as
     * `CHAIN_NOT_SUPPORTED`. The dApp's clientlib retries against a
     * different cluster or surfaces the limitation to the user.
     */
    fun completeWithChainNotSupported() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.CHAIN_NOT_SUPPORTED,
                message = "wallet does not support chain `$chain`"
            )
        )
    }

    /**
     * Legacy alias for [completeWithChainNotSupported]. Preserved for
     * MWA 1.x callers; new code should use the chain-named variant.
     */
    @Deprecated(
        "Use completeWithChainNotSupported.",
        ReplaceWith("completeWithChainNotSupported()")
    )
    fun completeWithClusterNotSupported() = completeWithChainNotSupported()

    /**
     * Internal payload returned to the dispatcher when the wallet
     * approves. Kept package-internal so consumers cannot construct a
     * spoofed approval bypassing the typed completion API.
     */
    internal data class AuthorizeApproved(
        val accounts: List<AuthorizedAccount>,
        val walletUriBase: Uri?,
        val scope: ByteArray,
        val signInResult: SignInResult?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthorizeApproved) return false
            return accounts == other.accounts &&
                walletUriBase == other.walletUriBase &&
                scope.contentEquals(other.scope) &&
                signInResult == other.signInResult
        }

        override fun hashCode(): Int {
            var h = accounts.hashCode()
            h = 31 * h + (walletUriBase?.hashCode() ?: 0)
            h = 31 * h + scope.contentHashCode()
            h = 31 * h + (signInResult?.hashCode() ?: 0)
            return h
        }
    }
}
