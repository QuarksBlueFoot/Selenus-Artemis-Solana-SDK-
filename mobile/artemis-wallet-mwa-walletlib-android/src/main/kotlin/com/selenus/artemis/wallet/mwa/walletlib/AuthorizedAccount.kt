package com.selenus.artemis.wallet.mwa.walletlib

import android.net.Uri

/**
 * One account the wallet authorized for a dApp identity.
 *
 * The MWA 2.0 wire shape pairs a canonical [publicKey] (raw 32-byte
 * Ed25519 public key for Solana) with optional display fields. Wallets
 * supporting non-Solana chains (e.g. Bitcoin via the same wallet
 * instance) populate [displayAddress] / [displayAddressFormat] so the
 * dApp can render a chain-appropriate string without re-encoding.
 *
 * `equals`/`hashCode` go through `contentEquals` on [publicKey] because
 * Kotlin's default ByteArray semantics use referential equality.
 */
data class AuthorizedAccount(
    val publicKey: ByteArray,
    val displayAddress: String? = null,
    val displayAddressFormat: String? = null,
    val accountLabel: String? = null,
    val accountIcon: Uri? = null,
    val chains: List<String> = emptyList(),
    val features: List<String> = emptyList()
) {
    init {
        require(publicKey.isNotEmpty()) { "publicKey must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthorizedAccount) return false
        return publicKey.contentEquals(other.publicKey) &&
            displayAddress == other.displayAddress &&
            displayAddressFormat == other.displayAddressFormat &&
            accountLabel == other.accountLabel &&
            accountIcon == other.accountIcon &&
            chains == other.chains &&
            features == other.features
    }

    override fun hashCode(): Int {
        var h = publicKey.contentHashCode()
        h = 31 * h + (displayAddress?.hashCode() ?: 0)
        h = 31 * h + (displayAddressFormat?.hashCode() ?: 0)
        h = 31 * h + (accountLabel?.hashCode() ?: 0)
        h = 31 * h + (accountIcon?.hashCode() ?: 0)
        h = 31 * h + chains.hashCode()
        h = 31 * h + features.hashCode()
        return h
    }
}
