/*
 * Drop-in source compatibility with com.solanamobile.seedvault.PermissionedAccount.
 *
 * Upstream ships this class empty of public constructors. The only exposed
 * surface is a static helper that builds a BIP-44 derivation path for a
 * given address index using the permissioned Solana path
 * `m/44'/501'/10000'/0'/{addressIndex}'`. Apps call it to provision accounts
 * that the Seed Vault UI permits without an additional user prompt.
 *
 * The shim keeps the class present with a private constructor and provides
 * the exact static method, so `PermissionedAccount.getPermissionedAccountDerivationPath(i)`
 * continues to resolve.
 */
package com.solanamobile.seedvault

import com.selenus.artemis.seedvault.BipLevel as ArtemisBipLevel
import com.selenus.artemis.seedvault.Bip44DerivationPath as ArtemisBip44

/** Seed Vault permissioned account helper. */
class PermissionedAccount private constructor() {
    companion object {
        /**
         * Build the BIP-44 derivation path reserved for permissioned accounts:
         * `m/44'/501'/10000'/0'/{addressIndex}'`. The `10000` account index is
         * defined by [WalletContractV1.PERMISSIONED_BIP44_ACCOUNT].
         */
        @JvmStatic
        fun getPermissionedAccountDerivationPath(addressIndex: Int): Bip44DerivationPath =
            ArtemisBip44.newBuilder()
                .setAccount(ArtemisBipLevel(WalletContractV1.PERMISSIONED_BIP44_ACCOUNT, hardened = true))
                .setChange(ArtemisBipLevel(WalletContractV1.PERMISSIONED_BIP44_CHANGE, hardened = true))
                .setAddressIndex(ArtemisBipLevel(addressIndex, hardened = true))
                .build()
    }
}
