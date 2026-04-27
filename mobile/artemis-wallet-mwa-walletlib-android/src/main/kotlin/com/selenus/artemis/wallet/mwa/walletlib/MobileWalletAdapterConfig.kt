package com.selenus.artemis.wallet.mwa.walletlib

/**
 * Wallet-side configuration consumed by [Scenario] / [LocalScenario].
 *
 * Mirrors the upstream walletlib `MobileWalletAdapterConfig` shape so a
 * compat shim can typealias against this without translation. Defaults
 * match the upstream defaults verified against the 2.0.7 sources jar.
 *
 * @property maxTransactionsPerSigningRequest Cap on `payloads.size` for
 *   `sign_transactions` and `sign_and_send_transactions`. Requests above
 *   the cap are rejected with `TOO_MANY_PAYLOADS`.
 * @property maxMessagesPerSigningRequest Cap on `payloads.size` for
 *   `sign_messages`. Mirrors the same `TOO_MANY_PAYLOADS` rejection.
 * @property supportedTransactionVersions Versions advertised in
 *   `get_capabilities`. Wire encoding is `"legacy"` for [TxVersion.Legacy]
 *   and the integer `0` for [TxVersion.V0].
 * @property optionalFeatures CAIP-104 feature identifiers the wallet
 *   advertises beyond the mandatory baseline. See [Companion] for
 *   constants.
 * @property noConnectionWarningTimeoutMs Time the local scenario waits
 *   without an inbound WS connection before invoking
 *   `Callbacks.onLowPowerAndNoConnection`. Lets the wallet UI hint that
 *   the user may need to disable battery optimisation. Default 90s
 *   matches upstream.
 */
data class MobileWalletAdapterConfig(
    val maxTransactionsPerSigningRequest: Int = Int.MAX_VALUE,
    val maxMessagesPerSigningRequest: Int = Int.MAX_VALUE,
    val supportedTransactionVersions: List<TxVersion> = listOf(TxVersion.Legacy, TxVersion.V0),
    val optionalFeatures: Set<String> = emptySet(),
    val noConnectionWarningTimeoutMs: Long = 90_000L
) {
    init {
        require(maxTransactionsPerSigningRequest >= 0) {
            "maxTransactionsPerSigningRequest must be non-negative"
        }
        require(maxMessagesPerSigningRequest >= 0) {
            "maxMessagesPerSigningRequest must be non-negative"
        }
        require(noConnectionWarningTimeoutMs > 0) {
            "noConnectionWarningTimeoutMs must be positive"
        }
    }

    /**
     * Solana transaction wire-version supported by the wallet.
     *
     * Sealed (instead of `enum`) so future versions can add fields
     * without breaking exhaustive `when` callers in the dispatcher.
     */
    sealed class TxVersion {
        /** Legacy (pre-v0) transaction format. Wire token: `"legacy"`. */
        object Legacy : TxVersion() {
            override fun toString(): String = "Legacy"
        }
        /** Versioned transaction v0. Wire token: integer `0`. */
        object V0 : TxVersion() {
            override fun toString(): String = "V0"
        }
    }

    companion object {
        /**
         * MWA 2.0 CAIP-104 feature identifiers. Wallets that support a
         * feature add the corresponding constant to [optionalFeatures];
         * the `get_capabilities` reply is derived from that set.
         */
        const val FEATURE_SIGN_TRANSACTIONS: String = "solana:signTransactions"
        const val FEATURE_SIGN_AND_SEND_TRANSACTIONS: String = "solana:signAndSendTransaction"
        const val FEATURE_SIGN_MESSAGES: String = "solana:signMessages"
        const val FEATURE_SIGN_IN_WITH_SOLANA: String = "solana:signInWithSolana"
        const val FEATURE_CLONE_AUTHORIZATION: String = "solana:cloneAuthorization"
    }
}
