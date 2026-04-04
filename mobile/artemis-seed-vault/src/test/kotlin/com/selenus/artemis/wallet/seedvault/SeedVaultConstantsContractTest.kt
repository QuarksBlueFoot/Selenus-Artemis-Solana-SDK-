/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Contract tests verifying SeedVaultConstants match upstream
 * com.solanamobile.seedvault.WalletContractV1 v0.4.0 exactly.
 */
package com.selenus.artemis.wallet.seedvault

import com.selenus.artemis.seedvault.internal.SeedVaultConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SeedVaultConstantsContractTest {

    private val AUTHORITY = "com.solanamobile.seedvault.wallet.v1"
    private val AUTHORITY_PROVIDER = "$AUTHORITY.walletprovider"

    // RESULT_FIRST_USER = 1 (android.app.Activity)
    private val RESULT_FIRST_USER = 1

    // ── Package & Permissions ───────────────────────────────────────────────
    @Test fun `PACKAGE_SEED_VAULT matches upstream`() =
        assertEquals("com.solanamobile.seedvaultimpl", SeedVaultConstants.PACKAGE_SEED_VAULT)

    @Test fun `PERMISSION_ACCESS_SEED_VAULT matches upstream`() =
        assertEquals("com.solanamobile.seedvault.ACCESS_SEED_VAULT", SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT)

    @Test fun `PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED matches upstream`() =
        assertEquals("com.solanamobile.seedvault.ACCESS_SEED_VAULT_PRIVILEGED", SeedVaultConstants.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED)

    @Test fun `PERMISSION_SEED_VAULT_IMPL matches upstream`() =
        assertEquals("com.solanamobile.seedvault.SEED_VAULT_IMPL", SeedVaultConstants.PERMISSION_SEED_VAULT_IMPL)

    @Test fun `AUTHORITY_WALLET matches upstream`() =
        assertEquals(AUTHORITY, SeedVaultConstants.AUTHORITY_WALLET)

    @Test fun `AUTHORITY_WALLET_PROVIDER matches upstream`() =
        assertEquals(AUTHORITY_PROVIDER, SeedVaultConstants.AUTHORITY_WALLET_PROVIDER)

    // ── Actions ─────────────────────────────────────────────────────────────
    @Test fun `ACTION_AUTHORIZE_SEED_ACCESS matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_AUTHORIZE_SEED_ACCESS", SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS)

    @Test fun `ACTION_SIGN_TRANSACTION matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_SIGN_TRANSACTION", SeedVaultConstants.ACTION_SIGN_TRANSACTION)

    @Test fun `ACTION_SIGN_MESSAGE matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_SIGN_MESSAGE", SeedVaultConstants.ACTION_SIGN_MESSAGE)

    @Test fun `ACTION_GET_PUBLIC_KEY matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_GET_PUBLIC_KEY", SeedVaultConstants.ACTION_GET_PUBLIC_KEY)

    @Test fun `ACTION_CREATE_SEED matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_CREATE_SEED", SeedVaultConstants.ACTION_CREATE_SEED)

    @Test fun `ACTION_IMPORT_SEED matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_IMPORT_SEED", SeedVaultConstants.ACTION_IMPORT_SEED)

    @Test fun `ACTION_SEED_SETTINGS matches upstream`() =
        assertEquals("$AUTHORITY.ACTION_SEED_SETTINGS", SeedVaultConstants.ACTION_SEED_SETTINGS)

    // ── Extras ──────────────────────────────────────────────────────────────
    @Test fun `EXTRA_PURPOSE matches upstream`() =
        assertEquals("Purpose", SeedVaultConstants.EXTRA_PURPOSE)

    @Test fun `EXTRA_AUTH_TOKEN matches upstream`() =
        assertEquals("AuthToken", SeedVaultConstants.EXTRA_AUTH_TOKEN)

    @Test fun `EXTRA_SIGNING_REQUEST matches upstream`() =
        assertEquals("SigningRequest", SeedVaultConstants.EXTRA_SIGNING_REQUEST)

    @Test fun `EXTRA_SIGNING_RESPONSE matches upstream`() =
        assertEquals("SigningResponse", SeedVaultConstants.EXTRA_SIGNING_RESPONSE)

    @Test fun `EXTRA_DERIVATION_PATH matches upstream`() =
        assertEquals("DerivationPath", SeedVaultConstants.EXTRA_DERIVATION_PATH)

    @Test fun `EXTRA_PUBLIC_KEY matches upstream`() =
        assertEquals("PublicKey", SeedVaultConstants.EXTRA_PUBLIC_KEY)

    @Test fun `EXTRA_RESOLVED_BIP32_DERIVATION_PATH matches upstream`() =
        assertEquals("ResolveBipDerivationPath_ResolvedBip32DerivationPath",
            SeedVaultConstants.EXTRA_RESOLVED_BIP32_DERIVATION_PATH)

    // ── Result Codes (RESULT_FIRST_USER + offset) ───────────────────────────
    @Test fun `RESULT_UNSPECIFIED_ERROR matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1000, SeedVaultConstants.RESULT_UNSPECIFIED_ERROR)

    @Test fun `RESULT_INVALID_AUTH_TOKEN matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1001, SeedVaultConstants.RESULT_INVALID_AUTH_TOKEN)

    @Test fun `RESULT_INVALID_PAYLOAD matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1002, SeedVaultConstants.RESULT_INVALID_PAYLOAD)

    @Test fun `RESULT_INVALID_TRANSACTION is alias for RESULT_INVALID_PAYLOAD`() =
        assertEquals(SeedVaultConstants.RESULT_INVALID_PAYLOAD, SeedVaultConstants.RESULT_INVALID_TRANSACTION)

    @Test fun `RESULT_AUTHENTICATION_FAILED matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1003, SeedVaultConstants.RESULT_AUTHENTICATION_FAILED)

    @Test fun `RESULT_NO_AVAILABLE_SEEDS matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1004, SeedVaultConstants.RESULT_NO_AVAILABLE_SEEDS)

    @Test fun `RESULT_INVALID_PURPOSE matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1005, SeedVaultConstants.RESULT_INVALID_PURPOSE)

    @Test fun `RESULT_INVALID_DERIVATION_PATH matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1006, SeedVaultConstants.RESULT_INVALID_DERIVATION_PATH)

    @Test fun `RESULT_IMPLEMENTATION_LIMIT_EXCEEDED matches upstream`() =
        assertEquals(RESULT_FIRST_USER + 1007, SeedVaultConstants.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED)

    // ── BIP URI Constants ───────────────────────────────────────────────────
    @Test fun `BIP32_URI_SCHEME matches upstream`() =
        assertEquals("bip32", SeedVaultConstants.BIP32_URI_SCHEME)

    @Test fun `BIP44_URI_SCHEME matches upstream`() =
        assertEquals("bip44", SeedVaultConstants.BIP44_URI_SCHEME)

    @Test fun `BIP32_URI_MAX_DEPTH matches upstream`() =
        assertEquals(20, SeedVaultConstants.BIP32_URI_MAX_DEPTH)

    @Test fun `BIP32_URI_MASTER_KEY_INDICATOR matches upstream`() =
        assertEquals("m", SeedVaultConstants.BIP32_URI_MASTER_KEY_INDICATOR)

    @Test fun `BIP_URI_HARDENED_INDEX_IDENTIFIER matches upstream`() =
        assertEquals("'", SeedVaultConstants.BIP_URI_HARDENED_INDEX_IDENTIFIER)

    // ── Permissioned Accounts ───────────────────────────────────────────────
    @Test fun `PERMISSIONED_BIP44_ACCOUNT matches upstream`() =
        assertEquals(10000, SeedVaultConstants.PERMISSIONED_BIP44_ACCOUNT)

    @Test fun `PERMISSIONED_BIP44_CHANGE matches upstream`() =
        assertEquals(0, SeedVaultConstants.PERMISSIONED_BIP44_CHANGE)

    // ── Implementation Limits ───────────────────────────────────────────────
    @Test fun `MIN_SUPPORTED_SIGNING_REQUESTS matches upstream`() =
        assertEquals(3, SeedVaultConstants.MIN_SUPPORTED_SIGNING_REQUESTS)

    @Test fun `MIN_SUPPORTED_REQUESTED_SIGNATURES matches upstream`() =
        assertEquals(3, SeedVaultConstants.MIN_SUPPORTED_REQUESTED_SIGNATURES)

    @Test fun `MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS matches upstream`() =
        assertEquals(10, SeedVaultConstants.MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS)

    // ── Content Provider Method ─────────────────────────────────────────────
    @Test fun `RESOLVE_BIP32_DERIVATION_PATH_METHOD matches upstream`() =
        assertEquals("ResolveBipDerivationPath", SeedVaultConstants.RESOLVE_BIP32_DERIVATION_PATH_METHOD)

    // ── Content Provider URIs ───────────────────────────────────────────────
    @Test fun `WALLET_PROVIDER_CONTENT_URI_BASE matches upstream`() =
        assertEquals("content://$AUTHORITY_PROVIDER", SeedVaultConstants.WALLET_PROVIDER_CONTENT_URI_BASE.toString())

    @Test fun `AUTHORIZED_SEEDS_CONTENT_URI matches upstream`() =
        assertEquals("content://$AUTHORITY_PROVIDER/authorizedseeds",
            SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI.toString())

    @Test fun `UNAUTHORIZED_SEEDS_CONTENT_URI matches upstream`() =
        assertEquals("content://$AUTHORITY_PROVIDER/unauthorizedseeds",
            SeedVaultConstants.UNAUTHORIZED_SEEDS_CONTENT_URI.toString())

    @Test fun `ACCOUNTS_CONTENT_URI matches upstream`() =
        assertEquals("content://$AUTHORITY_PROVIDER/accounts",
            SeedVaultConstants.ACCOUNTS_CONTENT_URI.toString())

    @Test fun `IMPLEMENTATION_LIMITS_CONTENT_URI matches upstream`() =
        assertEquals("content://$AUTHORITY_PROVIDER/implementationlimits",
            SeedVaultConstants.IMPLEMENTATION_LIMITS_CONTENT_URI.toString())

    // ── Table Names ─────────────────────────────────────────────────────────
    @Test fun `AUTHORIZED_SEEDS_TABLE matches upstream`() =
        assertEquals("authorizedseeds", SeedVaultConstants.AUTHORIZED_SEEDS_TABLE)

    @Test fun `UNAUTHORIZED_SEEDS_TABLE matches upstream`() =
        assertEquals("unauthorizedseeds", SeedVaultConstants.UNAUTHORIZED_SEEDS_TABLE)

    @Test fun `ACCOUNTS_TABLE matches upstream`() =
        assertEquals("accounts", SeedVaultConstants.ACCOUNTS_TABLE)

    @Test fun `IMPLEMENTATION_LIMITS_TABLE matches upstream`() =
        assertEquals("implementationlimits", SeedVaultConstants.IMPLEMENTATION_LIMITS_TABLE)

    // ── Column Names (Authorized Seeds) ─────────────────────────────────────
    @Test fun `AUTHORIZED_SEEDS_AUTH_TOKEN matches BaseColumns _ID`() =
        assertEquals("_id", SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_TOKEN)

    @Test fun `AUTHORIZED_SEEDS_AUTH_PURPOSE matches upstream`() =
        assertEquals("AuthorizedSeeds_AuthPurpose", SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_PURPOSE)

    @Test fun `AUTHORIZED_SEEDS_SEED_NAME matches upstream`() =
        assertEquals("AuthorizedSeeds_SeedName", SeedVaultConstants.AUTHORIZED_SEEDS_SEED_NAME)

    @Test fun `AUTHORIZED_SEEDS_IS_BACKED_UP matches upstream`() =
        assertEquals("AuthorizedSeeds_IsBackedUp", SeedVaultConstants.AUTHORIZED_SEEDS_IS_BACKED_UP)

    @Test fun `AUTHORIZED_SEEDS_ALL_COLUMNS matches upstream count and order`() {
        assertArrayEquals(
            arrayOf("_id", "AuthorizedSeeds_AuthPurpose", "AuthorizedSeeds_SeedName", "AuthorizedSeeds_IsBackedUp"),
            SeedVaultConstants.AUTHORIZED_SEEDS_ALL_COLUMNS
        )
    }

    // ── Column Names (Unauthorized Seeds) ───────────────────────────────────
    @Test fun `UNAUTHORIZED_SEEDS_AUTH_PURPOSE matches BaseColumns _ID`() =
        assertEquals("_id", SeedVaultConstants.UNAUTHORIZED_SEEDS_AUTH_PURPOSE)

    @Test fun `UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS matches upstream`() =
        assertEquals("UnauthorizedSeeds_HasUnauthorizedSeeds", SeedVaultConstants.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS)

    @Test fun `UNAUTHORIZED_SEEDS_ALL_COLUMNS matches upstream count and order`() {
        assertArrayEquals(
            arrayOf("_id", "UnauthorizedSeeds_HasUnauthorizedSeeds"),
            SeedVaultConstants.UNAUTHORIZED_SEEDS_ALL_COLUMNS
        )
    }

    // ── Column Names (Accounts) ─────────────────────────────────────────────
    @Test fun `ACCOUNTS_ACCOUNT_ID matches BaseColumns _ID`() =
        assertEquals("_id", SeedVaultConstants.ACCOUNTS_ACCOUNT_ID)

    @Test fun `ACCOUNTS_BIP32_DERIVATION_PATH matches upstream`() =
        assertEquals("Accounts_Bip32DerivationPath", SeedVaultConstants.ACCOUNTS_BIP32_DERIVATION_PATH)

    @Test fun `ACCOUNTS_PUBLIC_KEY_RAW matches upstream`() =
        assertEquals("Accounts_PublicKeyRaw", SeedVaultConstants.ACCOUNTS_PUBLIC_KEY_RAW)

    @Test fun `ACCOUNTS_PUBLIC_KEY_ENCODED matches upstream`() =
        assertEquals("Accounts_PublicKeyEncoded", SeedVaultConstants.ACCOUNTS_PUBLIC_KEY_ENCODED)

    @Test fun `ACCOUNTS_ACCOUNT_NAME matches upstream`() =
        assertEquals("Accounts_AccountName", SeedVaultConstants.ACCOUNTS_ACCOUNT_NAME)

    @Test fun `ACCOUNTS_ACCOUNT_IS_USER_WALLET matches upstream`() =
        assertEquals("Accounts_IsUserWallet", SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_USER_WALLET)

    @Test fun `ACCOUNTS_ACCOUNT_IS_VALID matches upstream`() =
        assertEquals("Accounts_IsValid", SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_VALID)

    @Test fun `ACCOUNTS_ALL_COLUMNS matches upstream count and order`() {
        assertArrayEquals(
            arrayOf("_id", "Accounts_Bip32DerivationPath", "Accounts_PublicKeyRaw",
                "Accounts_PublicKeyEncoded", "Accounts_AccountName", "Accounts_IsUserWallet",
                "Accounts_IsValid"),
            SeedVaultConstants.ACCOUNTS_ALL_COLUMNS
        )
    }

    // ── Column Names (Implementation Limits) ────────────────────────────────
    @Test fun `IMPLEMENTATION_LIMITS_AUTH_PURPOSE matches BaseColumns _ID`() =
        assertEquals("_id", SeedVaultConstants.IMPLEMENTATION_LIMITS_AUTH_PURPOSE)

    @Test fun `IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS matches upstream`() =
        assertEquals("MaxSigningRequests", SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS)

    @Test fun `IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES matches upstream`() =
        assertEquals("MaxRequestedSignatures", SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES)

    @Test fun `IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS matches upstream`() =
        assertEquals("MaxRequestedPublicKeys", SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS)

    @Test fun `IMPLEMENTATION_LIMITS_ALL_COLUMNS matches upstream count and order`() {
        assertArrayEquals(
            arrayOf("_id", "MaxSigningRequests", "MaxRequestedSignatures", "MaxRequestedPublicKeys"),
            SeedVaultConstants.IMPLEMENTATION_LIMITS_ALL_COLUMNS
        )
    }

    // ── MIME Subtypes ───────────────────────────────────────────────────────
    @Test fun `AUTHORIZED_SEEDS_MIME_SUBTYPE matches upstream`() =
        assertEquals("vnd.$AUTHORITY_PROVIDER.authorizedseeds",
            SeedVaultConstants.AUTHORIZED_SEEDS_MIME_SUBTYPE)

    @Test fun `UNAUTHORIZED_SEEDS_MIME_SUBTYPE matches upstream`() =
        assertEquals("vnd.$AUTHORITY_PROVIDER.unauthorizedseeds",
            SeedVaultConstants.UNAUTHORIZED_SEEDS_MIME_SUBTYPE)

    @Test fun `ACCOUNTS_MIME_SUBTYPE matches upstream`() =
        assertEquals("vnd.$AUTHORITY_PROVIDER.accounts",
            SeedVaultConstants.ACCOUNTS_MIME_SUBTYPE)

    @Test fun `IMPLEMENTATION_LIMITS_MIME_SUBTYPE matches upstream`() =
        assertEquals("vnd.$AUTHORITY_PROVIDER.implementationlimits",
            SeedVaultConstants.IMPLEMENTATION_LIMITS_MIME_SUBTYPE)

    // ── Purpose Constants ───────────────────────────────────────────────────
    @Test fun `PURPOSE_SIGN_SOLANA_TRANSACTION matches upstream`() =
        assertEquals(0, SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION)
}
