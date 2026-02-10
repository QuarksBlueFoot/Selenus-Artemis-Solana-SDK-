/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * PermissionedAccount - Helper for privileged derivation paths.
 * 
 * Full parity with com.solanamobile.seedvault.PermissionedAccount from upstream SDK v0.4.0.
 * 
 * When an app holds PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED, derivation paths of the form
 * m/44'/[coin]'/10000'/0/X are special: they can be used for signing without any user
 * authentication UI. This enables system-level wallet apps to provide seamless signing.
 */
package com.selenus.artemis.seedvault

import com.selenus.artemis.seedvault.internal.SeedVaultConstants

/**
 * Helper functions for permissioned account derivation paths.
 * 
 * Permissioned accounts use the form: m/44'/[coin]'/10000'/0/X
 * where X is a standard BIP-44 address index.
 * 
 * These paths allow privileged wallet apps to sign without user authentication UI.
 */
object PermissionedAccount {
    
    /**
     * Get the BIP44 derivation path for a permissioned account at the given address index.
     * 
     * @param addressIndex The address index
     * @return Bip44DerivationPath for the permissioned account
     */
    fun getPermissionedAccountDerivationPath(addressIndex: Int): Bip44DerivationPath {
        return Bip44DerivationPath.newBuilder()
            .setAccount(BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_ACCOUNT, true))
            .setChange(BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_CHANGE, false))
            .setAddressIndex(BipLevel(addressIndex, false))
            .build()
    }
    
    /**
     * Get the full BIP32 derivation path for a permissioned account.
     * 
     * @param addressIndex The address index
     * @param purpose The signing purpose
     * @return Full BIP32 path (e.g., m/44'/501'/10000'/0/X)
     */
    fun getPermissionedBip32Path(
        addressIndex: Int,
        purpose: Int = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION
    ): Bip32DerivationPath {
        return getPermissionedAccountDerivationPath(addressIndex)
            .toBip32DerivationPath(purpose)
    }
    
    /**
     * Check if a derivation path is a permissioned account path.
     */
    fun isPermissionedPath(path: Bip32DerivationPath): Boolean {
        val levels = path.levels
        if (levels.size < 5) return false
        
        // Check purpose=44', coinType (varies), account=10000', change=0
        return levels[0] == BipLevel(44, true) &&
               levels[2] == BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_ACCOUNT, true) &&
               levels[3] == BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_CHANGE, false)
    }
    
    /**
     * Check if a BIP44 derivation path is a permissioned account path.
     */
    fun isPermissionedPath(path: Bip44DerivationPath): Boolean {
        return path.account == BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_ACCOUNT, true) &&
               path.change == BipLevel(SeedVaultConstants.PERMISSIONED_BIP44_CHANGE, false)
    }
}
