/*
 * Drop-in source compatibility with org.sol4k.RpcUrl.
 *
 * IMPORTANT: sol4k 0.7.x has a typo in this enum — `MAINNNET` has three N's.
 * The shim MUST preserve it verbatim, otherwise sol4k users who wrote
 * `RpcUrl.MAINNNET` in their app will see a compile error when they swap in
 * the Artemis shim. A corrected alias `MAINNET` is added on the side so
 * new code can use the right spelling without giving up drop-in parity.
 */
@file:Suppress("EnumEntryName")
package org.sol4k

/**
 * Cluster URL presets. Enum values match sol4k 0.7.x verbatim, including the
 * `MAINNNET` typo. Use [MAINNET] in new code; both resolve to the same value.
 */
enum class RpcUrl(val value: String) {
    DEVNET("https://api.devnet.solana.com"),
    MAINNNET("https://api.mainnet-beta.solana.com"),
    TESTNET("https://api.testnet.solana.com");

    override fun toString(): String = value

    companion object {
        /**
         * Correctly-spelled alias for callers who want a future-proof name.
         * Resolves to the same URL as [MAINNNET].
         */
        @JvmField val MAINNET: RpcUrl = MAINNNET
    }
}
