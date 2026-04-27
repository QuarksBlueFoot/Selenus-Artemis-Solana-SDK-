package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * Wallet-side view of a `reauthorize` JSON-RPC request, or an
 * `authorize` request that arrived with an `auth_token` parameter
 * (the MWA 2.0 unified flow).
 *
 * The wallet typically auto-approves when the token is fresh and the
 * identity matches the one that originally issued it, falling back to
 * a confirmation prompt when the token is older than
 * [AuthIssuerConfig.reauthorizationNopDuration].
 *
 * Mirrors upstream walletlib's `BaseVerifiableIdentityRequest`
 * exposure of [authorizationScope] and [chain] so wallets that need
 * either field for policy decisions (e.g. tag-based scope routing,
 * chain-specific UI) can read them without re-querying the repo.
 *
 * @property chain CAIP-2 chain the dApp asked to reauthorize against.
 *   Always populated; the dispatcher defaults to `solana:mainnet` when
 *   the dApp omits the field.
 * @property authorizationScope Wallet-private scope bytes that were
 *   bound to this auth_token at issuance. Echoed back from the repo so
 *   the wallet UI can surface scope-derived policy without a lookup.
 */
open class ReauthorizeRequest internal constructor(
    val identityName: String?,
    val identityUri: Uri?,
    val iconRelativeUri: Uri?,
    val authToken: String,
    val chain: String,
    val authorizationScope: ByteArray
) : MwaRequest() {

    /**
     * Legacy `cluster` string for MWA 1.x callers. Derived from
     * [chain] via [ProtocolContract.clusterForChain]; returns the raw
     * CAIP-2 string for chains that don't have a 1.x alias.
     */
    @Deprecated(
        "Use chain. cluster is preserved for MWA 1.x compatibility only.",
        ReplaceWith("chain")
    )
    val cluster: String
        get() = ProtocolContract.clusterForChain(chain) ?: chain

    /**
     * Token still valid; the dApp keeps using the same auth token. The
     * wallet may extend the token's expiry inside [AuthRepository]; the
     * wire reply does not carry a new token.
     */
    fun completeWithReauthorize() {
        completeInternal(MwaCompletion.Result(ReauthorizeApproved))
    }

    /**
     * User declined or the wallet revoked the token. Surfaced to the
     * dApp as `AUTHORIZATION_FAILED` so the dApp re-runs the full
     * `authorize` flow.
     */
    fun completeWithDecline() {
        completeInternal(
            MwaCompletion.Error(
                code = MwaErrorCodes.AUTHORIZATION_FAILED,
                message = "reauthorization declined"
            )
        )
    }

    /** Sentinel marker; the dispatcher serializes a no-data success reply. */
    internal object ReauthorizeApproved
}
