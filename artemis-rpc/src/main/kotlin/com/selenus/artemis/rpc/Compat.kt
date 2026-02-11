package com.selenus.artemis.rpc

/**
 * Commitment levels for Solana RPC requests.
 *
 * Replaces the old `object Commitment` with an enum that provides both
 * the enum constant style and the string constant style:
 *
 * ```kotlin
 * // Enum style (sol4k parity):
 * val c = Commitment.CONFIRMED
 *
 * // String constant style (backward compatible):
 * val s: String = Commitment.CONFIRMED.value  // "confirmed"
 * ```
 */
enum class Commitment(val value: String) {
    PROCESSED("processed"),
    CONFIRMED("confirmed"),
    FINALIZED("finalized");

    override fun toString(): String = value

    companion object {
        /** Parse from a string value (case-insensitive). */
        fun fromValue(value: String): Commitment =
            entries.find { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown commitment: $value")
    }
}

/**
 * Well-known Solana cluster endpoints.
 * Matches sol4k RpcUrl for parity.
 */
enum class SolanaCluster(val endpoint: String) {
    MAINNET_BETA("https://api.mainnet-beta.solana.com"),
    DEVNET("https://api.devnet.solana.com"),
    TESTNET("https://api.testnet.solana.com");
}
