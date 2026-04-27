/*
 * Drop-in source compatibility with com.solana.mobilewalletadapter.walletlib.authorization.
 *
 * Upstream exposes `AuthIssuerConfig` as a small POJO with five
 * `@JvmField` finals plus a name. The shim translates each one onto
 * the Artemis [com.selenus.artemis.wallet.mwa.walletlib.AuthIssuerConfig]
 * data class, keeping the upstream defaults verbatim.
 */
@file:Suppress("unused")
package com.solana.mobilewalletadapter.walletlib.authorization

import com.selenus.artemis.wallet.mwa.walletlib.AuthIssuerConfig as ArtemisAuthIssuerConfig
import kotlin.time.Duration.Companion.milliseconds

class AuthIssuerConfig
@JvmOverloads constructor(
    @JvmField val name: String,
    @JvmField val maxOutstandingTokensPerIdentity: Int = 50,
    @JvmField val authorizationValidityMs: Long = 60L * 60L * 1000L,
    @JvmField val reauthorizationValidityMs: Long = 30L * 24L * 60L * 60L * 1000L,
    @JvmField val reauthorizationNopDurationMs: Long = 10L * 60L * 1000L
) {

    fun toArtemis(): ArtemisAuthIssuerConfig = ArtemisAuthIssuerConfig(
        name = name,
        maxOutstandingTokensPerIdentity = maxOutstandingTokensPerIdentity,
        authorizationValidity = authorizationValidityMs.milliseconds,
        reauthorizationValidity = reauthorizationValidityMs.milliseconds,
        reauthorizationNopDuration = reauthorizationNopDurationMs.milliseconds
    )
}
