/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for com.solanamobile.seedvault.WalletContractV1.
 * Delegates to Artemis SeedVaultConstants for all protocol values.
 */
package com.solanamobile.seedvault

import android.net.Uri
import com.selenus.artemis.seedvault.internal.SeedVaultConstants

/**
 * Compatibility shim for `com.solanamobile.seedvault.WalletContractV1`.
 * 
 * Drop-in replacement — use this instead of the Solana Mobile Seed Vault SDK.
 * All constants match the upstream protocol exactly.
 */
object WalletContractV1 {
    // Purposes
    const val PURPOSE_SIGN_SOLANA_TRANSACTION = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION

    // Actions
    const val ACTION_AUTHORIZE_SEED_ACCESS = SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS
    const val ACTION_SIGN_TRANSACTION = SeedVaultConstants.ACTION_SIGN_TRANSACTION
    const val ACTION_SIGN_MESSAGE = SeedVaultConstants.ACTION_SIGN_MESSAGE
    const val ACTION_GET_PUBLIC_KEY = SeedVaultConstants.ACTION_GET_PUBLIC_KEY
    const val ACTION_CREATE_SEED = SeedVaultConstants.ACTION_CREATE_SEED
    const val ACTION_IMPORT_SEED = SeedVaultConstants.ACTION_IMPORT_SEED
    const val ACTION_SEED_SETTINGS = SeedVaultConstants.ACTION_SEED_SETTINGS

    // Extras
    const val EXTRA_PURPOSE = SeedVaultConstants.EXTRA_PURPOSE
    const val EXTRA_AUTH_TOKEN = SeedVaultConstants.EXTRA_AUTH_TOKEN
    const val EXTRA_SIGNING_REQUEST = SeedVaultConstants.EXTRA_SIGNING_REQUEST
    const val EXTRA_SIGNING_RESPONSE = SeedVaultConstants.EXTRA_SIGNING_RESPONSE
    const val EXTRA_DERIVATION_PATH = SeedVaultConstants.EXTRA_DERIVATION_PATH
    const val EXTRA_PUBLIC_KEY = SeedVaultConstants.EXTRA_PUBLIC_KEY

    // Result codes
    const val RESULT_UNSPECIFIED_ERROR = SeedVaultConstants.RESULT_UNSPECIFIED_ERROR
    const val RESULT_INVALID_AUTH_TOKEN = SeedVaultConstants.RESULT_INVALID_AUTH_TOKEN
    const val RESULT_INVALID_PAYLOAD = SeedVaultConstants.RESULT_INVALID_PAYLOAD
    const val RESULT_INVALID_TRANSACTION = SeedVaultConstants.RESULT_INVALID_TRANSACTION
    const val RESULT_AUTHENTICATION_FAILED = SeedVaultConstants.RESULT_AUTHENTICATION_FAILED
    const val RESULT_NO_AVAILABLE_SEEDS = SeedVaultConstants.RESULT_NO_AVAILABLE_SEEDS
    const val RESULT_INVALID_PURPOSE = SeedVaultConstants.RESULT_INVALID_PURPOSE
    const val RESULT_INVALID_DERIVATION_PATH = SeedVaultConstants.RESULT_INVALID_DERIVATION_PATH
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = SeedVaultConstants.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED

    // BIP URI constants
    const val BIP32_URI_SCHEME = SeedVaultConstants.BIP32_URI_SCHEME
    const val BIP44_URI_SCHEME = SeedVaultConstants.BIP44_URI_SCHEME
    const val BIP32_URI_MASTER_KEY_INDICATOR = SeedVaultConstants.BIP32_URI_MASTER_KEY_INDICATOR

    // Implementation limits
    const val MIN_SUPPORTED_SIGNING_REQUESTS = SeedVaultConstants.MIN_SUPPORTED_SIGNING_REQUESTS
    const val MIN_SUPPORTED_REQUESTED_SIGNATURES = SeedVaultConstants.MIN_SUPPORTED_REQUESTED_SIGNATURES
    const val MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS = SeedVaultConstants.MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS

    // Content Provider
    const val AUTHORIZED_SEEDS_TABLE = SeedVaultConstants.AUTHORIZED_SEEDS_TABLE
    val AUTHORIZED_SEEDS_CONTENT_URI: Uri = SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI
    const val AUTHORIZED_SEEDS_AUTH_TOKEN = SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_TOKEN
    const val AUTHORIZED_SEEDS_AUTH_PURPOSE = SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_PURPOSE
}
