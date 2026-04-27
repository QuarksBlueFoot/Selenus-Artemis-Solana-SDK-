package com.selenus.artemis.wallet.mwa.walletlib

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days

/**
 * Auth-token issuance policy used by [AuthRepository] implementations.
 *
 * Wallets that ship their own [AuthRepository] are free to ignore some
 * or all of these knobs (the defaults are the values upstream enforces
 * in its SQLite-backed repository). When the wallet uses
 * [InMemoryAuthRepository] the limits and TTLs are honoured.
 *
 * @property name Human-readable wallet name. Surfaced through
 *   `getCapabilities` as part of the wallet metadata.
 * @property maxOutstandingTokensPerIdentity Cap on simultaneously valid
 *   auth tokens for a single dApp identity. Newer authorize requests
 *   beyond the cap evict the oldest token. Default 50 matches upstream.
 * @property authorizationValidityMs Lifetime of a freshly-issued auth
 *   token. After this window the token must be reauthorized. Default
 *   1 hour.
 * @property reauthorizationValidityMs Hard upper bound on a single
 *   token's lifetime, including any reauthorization extensions. Default
 *   30 days. Tokens older than this are revoked even if the dApp keeps
 *   reauthorizing.
 * @property reauthorizationNopDurationMs Window during which a
 *   reauthorization request returns the existing token unchanged
 *   instead of issuing a new one. Default 10 minutes.
 */
data class AuthIssuerConfig(
    val name: String,
    val maxOutstandingTokensPerIdentity: Int = 50,
    val authorizationValidity: Duration = 1.hours,
    val reauthorizationValidity: Duration = 30.days,
    val reauthorizationNopDuration: Duration = 10.minutes
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(maxOutstandingTokensPerIdentity > 0) {
            "maxOutstandingTokensPerIdentity must be positive"
        }
        require(authorizationValidity.isPositive()) {
            "authorizationValidity must be positive"
        }
        require(reauthorizationValidity.isPositive()) {
            "reauthorizationValidity must be positive"
        }
        require(reauthorizationNopDuration.isPositive()) {
            "reauthorizationNopDuration must be positive"
        }
    }

    /** Convenience accessor for callers that want the millisecond value. */
    val authorizationValidityMs: Long get() = authorizationValidity.inWholeMilliseconds

    /** Convenience accessor for callers that want the millisecond value. */
    val reauthorizationValidityMs: Long get() = reauthorizationValidity.inWholeMilliseconds

    /** Convenience accessor for callers that want the millisecond value. */
    val reauthorizationNopDurationMs: Long get() = reauthorizationNopDuration.inWholeMilliseconds
}
