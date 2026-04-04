/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for com.solanamobile.seedvault.WalletContractV1.
 * Delegates to Artemis SeedVaultConstants for all protocol values.
 */
package com.solanamobile.seedvault

import android.net.Uri
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import com.selenus.artemis.seedvault.internal.SeedVaultConstants

/**
 * Compatibility shim for `com.solanamobile.seedvault.WalletContractV1`.
 *
 * Drop-in replacement — use this instead of the Solana Mobile Seed Vault SDK.
 * All constants match the upstream protocol exactly.
 */
object WalletContractV1 {

    // ── Annotations (upstream parity) ───────────────────────────────────────
    @Retention(AnnotationRetention.SOURCE)
    @IntRange(from = 0, to = Long.MAX_VALUE)
    annotation class AuthToken

    @Retention(AnnotationRetention.SOURCE)
    @IntRange(from = 0, to = Long.MAX_VALUE)
    annotation class AccountId

    @Retention(AnnotationRetention.SOURCE)
    @IntRange(from = 0, to = Int.MAX_VALUE.toLong())
    annotation class BipIndex

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(PURPOSE_SIGN_SOLANA_TRANSACTION)
    annotation class Purpose

    // ── Package & Permissions ───────────────────────────────────────────────
    const val PACKAGE_SEED_VAULT = SeedVaultConstants.PACKAGE_SEED_VAULT
    const val PERMISSION_ACCESS_SEED_VAULT = SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT
    const val PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED = SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED
    const val PERMISSION_SEED_VAULT_IMPL = SeedVaultConstants.PERMISSION_SEED_VAULT_IMPL
    const val AUTHORITY_WALLET = SeedVaultConstants.AUTHORITY_WALLET

    // ── Purposes ────────────────────────────────────────────────────────────
    const val PURPOSE_SIGN_SOLANA_TRANSACTION = SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION

    // ── Actions ─────────────────────────────────────────────────────────────
    const val ACTION_AUTHORIZE_SEED_ACCESS = SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS
    const val ACTION_SIGN_TRANSACTION = SeedVaultConstants.ACTION_SIGN_TRANSACTION
    const val ACTION_SIGN_MESSAGE = SeedVaultConstants.ACTION_SIGN_MESSAGE
    const val ACTION_GET_PUBLIC_KEY = SeedVaultConstants.ACTION_GET_PUBLIC_KEY
    const val ACTION_CREATE_SEED = SeedVaultConstants.ACTION_CREATE_SEED
    const val ACTION_IMPORT_SEED = SeedVaultConstants.ACTION_IMPORT_SEED
    const val ACTION_SEED_SETTINGS = SeedVaultConstants.ACTION_SEED_SETTINGS

    // ── Extras ──────────────────────────────────────────────────────────────
    const val EXTRA_PURPOSE = SeedVaultConstants.EXTRA_PURPOSE
    const val EXTRA_AUTH_TOKEN = SeedVaultConstants.EXTRA_AUTH_TOKEN
    const val EXTRA_SIGNING_REQUEST = SeedVaultConstants.EXTRA_SIGNING_REQUEST
    const val EXTRA_SIGNING_RESPONSE = SeedVaultConstants.EXTRA_SIGNING_RESPONSE
    const val EXTRA_DERIVATION_PATH = SeedVaultConstants.EXTRA_DERIVATION_PATH
    const val EXTRA_PUBLIC_KEY = SeedVaultConstants.EXTRA_PUBLIC_KEY
    const val EXTRA_RESOLVED_BIP32_DERIVATION_PATH = SeedVaultConstants.EXTRA_RESOLVED_BIP32_DERIVATION_PATH

    // ── Result codes ────────────────────────────────────────────────────────
    const val RESULT_UNSPECIFIED_ERROR = SeedVaultConstants.RESULT_UNSPECIFIED_ERROR
    const val RESULT_INVALID_AUTH_TOKEN = SeedVaultConstants.RESULT_INVALID_AUTH_TOKEN
    const val RESULT_INVALID_PAYLOAD = SeedVaultConstants.RESULT_INVALID_PAYLOAD
    const val RESULT_INVALID_TRANSACTION = SeedVaultConstants.RESULT_INVALID_TRANSACTION
    const val RESULT_AUTHENTICATION_FAILED = SeedVaultConstants.RESULT_AUTHENTICATION_FAILED
    const val RESULT_NO_AVAILABLE_SEEDS = SeedVaultConstants.RESULT_NO_AVAILABLE_SEEDS
    const val RESULT_INVALID_PURPOSE = SeedVaultConstants.RESULT_INVALID_PURPOSE
    const val RESULT_INVALID_DERIVATION_PATH = SeedVaultConstants.RESULT_INVALID_DERIVATION_PATH
    const val RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = SeedVaultConstants.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED

    // ── BIP URI constants ───────────────────────────────────────────────────
    const val BIP32_URI_SCHEME = SeedVaultConstants.BIP32_URI_SCHEME
    const val BIP44_URI_SCHEME = SeedVaultConstants.BIP44_URI_SCHEME
    const val BIP32_URI_MASTER_KEY_INDICATOR = SeedVaultConstants.BIP32_URI_MASTER_KEY_INDICATOR
    const val BIP_URI_HARDENED_INDEX_IDENTIFIER = SeedVaultConstants.BIP_URI_HARDENED_INDEX_IDENTIFIER
    const val BIP32_URI_MAX_DEPTH = SeedVaultConstants.BIP32_URI_MAX_DEPTH

    // ── Permissioned accounts ───────────────────────────────────────────────
    const val PERMISSIONED_BIP44_ACCOUNT = SeedVaultConstants.PERMISSIONED_BIP44_ACCOUNT
    const val PERMISSIONED_BIP44_CHANGE = SeedVaultConstants.PERMISSIONED_BIP44_CHANGE

    // ── Implementation limits ───────────────────────────────────────────────
    const val MIN_SUPPORTED_SIGNING_REQUESTS = SeedVaultConstants.MIN_SUPPORTED_SIGNING_REQUESTS
    const val MIN_SUPPORTED_REQUESTED_SIGNATURES = SeedVaultConstants.MIN_SUPPORTED_REQUESTED_SIGNATURES
    const val MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS = SeedVaultConstants.MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS

    // ── Content Provider - Authority ────────────────────────────────────────
    const val AUTHORITY_WALLET_PROVIDER = SeedVaultConstants.AUTHORITY_WALLET_PROVIDER
    @JvmField val WALLET_PROVIDER_CONTENT_URI_BASE: Uri = SeedVaultConstants.WALLET_PROVIDER_CONTENT_URI_BASE

    // ── Content Provider - Resolve method ───────────────────────────────────
    const val RESOLVE_BIP32_DERIVATION_PATH_METHOD = SeedVaultConstants.RESOLVE_BIP32_DERIVATION_PATH_METHOD

    // ── Content Provider - Authorized Seeds ─────────────────────────────────
    const val AUTHORIZED_SEEDS_TABLE = SeedVaultConstants.AUTHORIZED_SEEDS_TABLE
    @JvmField val AUTHORIZED_SEEDS_CONTENT_URI: Uri = SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI
    const val AUTHORIZED_SEEDS_AUTH_TOKEN = SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_TOKEN
    const val AUTHORIZED_SEEDS_AUTH_PURPOSE = SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_PURPOSE
    const val AUTHORIZED_SEEDS_SEED_NAME = SeedVaultConstants.AUTHORIZED_SEEDS_SEED_NAME
    const val AUTHORIZED_SEEDS_IS_BACKED_UP = SeedVaultConstants.AUTHORIZED_SEEDS_IS_BACKED_UP
    @JvmField val AUTHORIZED_SEEDS_ALL_COLUMNS: Array<String> = SeedVaultConstants.AUTHORIZED_SEEDS_ALL_COLUMNS
    const val AUTHORIZED_SEEDS_MIME_SUBTYPE = SeedVaultConstants.AUTHORIZED_SEEDS_MIME_SUBTYPE

    // ── Content Provider - Unauthorized Seeds ───────────────────────────────
    const val UNAUTHORIZED_SEEDS_TABLE = SeedVaultConstants.UNAUTHORIZED_SEEDS_TABLE
    @JvmField val UNAUTHORIZED_SEEDS_CONTENT_URI: Uri = SeedVaultConstants.UNAUTHORIZED_SEEDS_CONTENT_URI
    const val UNAUTHORIZED_SEEDS_AUTH_PURPOSE = SeedVaultConstants.UNAUTHORIZED_SEEDS_AUTH_PURPOSE
    const val UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS = SeedVaultConstants.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS
    @JvmField val UNAUTHORIZED_SEEDS_ALL_COLUMNS: Array<String> = SeedVaultConstants.UNAUTHORIZED_SEEDS_ALL_COLUMNS
    const val UNAUTHORIZED_SEEDS_MIME_SUBTYPE = SeedVaultConstants.UNAUTHORIZED_SEEDS_MIME_SUBTYPE

    // ── Content Provider - Accounts ─────────────────────────────────────────
    const val ACCOUNTS_TABLE = SeedVaultConstants.ACCOUNTS_TABLE
    @JvmField val ACCOUNTS_CONTENT_URI: Uri = SeedVaultConstants.ACCOUNTS_CONTENT_URI
    const val ACCOUNTS_ACCOUNT_ID = SeedVaultConstants.ACCOUNTS_ACCOUNT_ID
    const val ACCOUNTS_BIP32_DERIVATION_PATH = SeedVaultConstants.ACCOUNTS_BIP32_DERIVATION_PATH
    const val ACCOUNTS_PUBLIC_KEY_RAW = SeedVaultConstants.ACCOUNTS_PUBLIC_KEY_RAW
    const val ACCOUNTS_PUBLIC_KEY_ENCODED = SeedVaultConstants.ACCOUNTS_PUBLIC_KEY_ENCODED
    const val ACCOUNTS_ACCOUNT_NAME = SeedVaultConstants.ACCOUNTS_ACCOUNT_NAME
    const val ACCOUNTS_ACCOUNT_IS_USER_WALLET = SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_USER_WALLET
    const val ACCOUNTS_ACCOUNT_IS_VALID = SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_VALID
    @JvmField val ACCOUNTS_ALL_COLUMNS: Array<String> = SeedVaultConstants.ACCOUNTS_ALL_COLUMNS
    const val ACCOUNTS_MIME_SUBTYPE = SeedVaultConstants.ACCOUNTS_MIME_SUBTYPE

    // ── Content Provider - Implementation Limits ────────────────────────────
    const val IMPLEMENTATION_LIMITS_TABLE = SeedVaultConstants.IMPLEMENTATION_LIMITS_TABLE
    @JvmField val IMPLEMENTATION_LIMITS_CONTENT_URI: Uri = SeedVaultConstants.IMPLEMENTATION_LIMITS_CONTENT_URI
    const val IMPLEMENTATION_LIMITS_AUTH_PURPOSE = SeedVaultConstants.IMPLEMENTATION_LIMITS_AUTH_PURPOSE
    const val IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS = SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS
    const val IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES = SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES
    const val IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS = SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS
    @JvmField val IMPLEMENTATION_LIMITS_ALL_COLUMNS: Array<String> = SeedVaultConstants.IMPLEMENTATION_LIMITS_ALL_COLUMNS
    const val IMPLEMENTATION_LIMITS_MIME_SUBTYPE = SeedVaultConstants.IMPLEMENTATION_LIMITS_MIME_SUBTYPE
}
