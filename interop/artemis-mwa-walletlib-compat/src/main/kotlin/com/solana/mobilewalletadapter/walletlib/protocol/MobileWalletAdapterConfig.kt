/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig.
 *
 * Upstream's class is a Java POJO with five `@JvmField`-style public
 * finals reachable by both Java and Kotlin callers. The shim exposes
 * the same shape and translates each field onto the Artemis
 * [com.selenus.artemis.wallet.mwa.walletlib.MobileWalletAdapterConfig]
 * data class.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.protocol

import com.selenus.artemis.wallet.mwa.walletlib.MobileWalletAdapterConfig as ArtemisMobileWalletAdapterConfig

/**
 * Wallet-side configuration. Construct positionally; upstream's Java
 * callers do `new MobileWalletAdapterConfig(maxTx, maxMsg, txVersions,
 * optionalFeatures, timeout)`.
 *
 * Transaction versions on the wire are either the string `"legacy"` or
 * the integer `0`; upstream uses `Object[]` to carry both shapes. The
 * shim accepts the upstream `Object[]` and projects each entry onto
 * the typed Artemis [TxVersion].
 */
class MobileWalletAdapterConfig(
    @JvmField val maxTransactionsPerSigningRequest: Int = Int.MAX_VALUE,
    @JvmField val maxMessagesPerSigningRequest: Int = Int.MAX_VALUE,
    @JvmField val supportedTransactionVersions: Array<Any> = arrayOf("legacy", 0),
    @JvmField val optionalFeatures: Array<String> = emptyArray(),
    @JvmField val noConnectionWarningTimeoutMs: Long = 90_000L
) {
    /** Bridge into the Artemis type the runtime actually operates on. */
    fun toArtemis(): ArtemisMobileWalletAdapterConfig {
        val versions = supportedTransactionVersions.map { entry ->
            when (entry) {
                is Number -> ArtemisMobileWalletAdapterConfig.TxVersion.V0
                is String -> when (entry.lowercase()) {
                    "legacy" -> ArtemisMobileWalletAdapterConfig.TxVersion.Legacy
                    "0" -> ArtemisMobileWalletAdapterConfig.TxVersion.V0
                    else -> ArtemisMobileWalletAdapterConfig.TxVersion.Legacy
                }
                else -> ArtemisMobileWalletAdapterConfig.TxVersion.Legacy
            }
        }
        return ArtemisMobileWalletAdapterConfig(
            maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest,
            maxMessagesPerSigningRequest = maxMessagesPerSigningRequest,
            supportedTransactionVersions = versions,
            optionalFeatures = optionalFeatures.toSet(),
            noConnectionWarningTimeoutMs = noConnectionWarningTimeoutMs
        )
    }

    companion object {
        /**
         * MWA 2.0 CAIP-104 feature identifiers. Mirrors upstream
         * walletlib constants verbatim so callers that import them
         * through the static do not need source changes.
         */
        const val OPTIONAL_FEATURE_SIGN_TRANSACTIONS: String = "solana:signTransactions"
        const val OPTIONAL_FEATURE_SIGN_AND_SEND_TRANSACTIONS: String =
            "solana:signAndSendTransaction"
        const val OPTIONAL_FEATURE_SIGN_MESSAGES: String = "solana:signMessages"
        const val OPTIONAL_FEATURE_SIGN_IN_WITH_SOLANA: String = "solana:signInWithSolana"
        const val OPTIONAL_FEATURE_CLONE_AUTHORIZATION: String = "solana:cloneAuthorization"
    }
}
