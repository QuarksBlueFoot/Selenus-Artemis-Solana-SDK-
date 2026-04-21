/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.solana.mobilewalletadapter.clientlib

import android.net.Uri

/**
 * Result of an MWA authorization request.
 *
 * Shape matches upstream `com.solana.mobilewalletadapter.clientlib.AuthorizationResult`.
 * The flat [publicKey] and [accountLabel] fields are MWA-1.x era legacy;
 * callers targeting MWA 2.0 should read from [accounts] directly so
 * multi-account state, per-account chains, and per-account features are
 * not collapsed. Both legacy fields are marked [Deprecated] so the
 * compiler surfaces any call site that still treats authorization as
 * single-account.
 *
 * Equality and hashing include every field, not just [authToken] +
 * [publicKey]. That matters for:
 *   - test assertions that compare two results, where a difference in
 *     `accounts`, `walletUriBase`, or `signInResult` must not be silently
 *     dropped.
 *   - deduplication caches keyed on the full authorization state.
 */
data class AuthorizationResult(
    val authToken: String,
    @Deprecated(
        "MWA 2.0 authorizations may carry multiple accounts. Read from " +
            "accounts[0].publicKey (or iterate accounts) instead of relying " +
            "on the flat publicKey field."
    )
    val publicKey: ByteArray,
    @Deprecated(
        "MWA 2.0 authorizations may carry multiple accounts. Read from " +
            "accounts[0].label (or iterate accounts) instead of relying on " +
            "the flat accountLabel field."
    )
    val accountLabel: String?,
    val walletUriBase: Uri?,
    val walletIcon: Uri? = null,
    val accounts: List<Account> = emptyList(),
    val signInResult: SignInResult? = null
) {
    data class Account(
        val publicKey: ByteArray,
        val displayAddress: String?,
        val displayAddressFormat: String?,
        val label: String?,
        val chains: List<String>?,
        val features: List<String>?
    ) {
        /**
         * Structural equality across every MWA 2.0 field. Two accounts are
         * equal only when their public key, every display hint, and every
         * advertised chain or feature match. Any earlier "compare
         * publicKey + displayAddress" short-circuit caused cache and
         * dedup bugs because two accounts with the same address but
         * different chain or feature support were treated as
         * indistinguishable.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Account) return false
            return publicKey.contentEquals(other.publicKey) &&
                displayAddress == other.displayAddress &&
                displayAddressFormat == other.displayAddressFormat &&
                label == other.label &&
                chains == other.chains &&
                features == other.features
        }

        override fun hashCode(): Int {
            var h = publicKey.contentHashCode()
            h = 31 * h + (displayAddress?.hashCode() ?: 0)
            h = 31 * h + (displayAddressFormat?.hashCode() ?: 0)
            h = 31 * h + (label?.hashCode() ?: 0)
            h = 31 * h + (chains?.hashCode() ?: 0)
            h = 31 * h + (features?.hashCode() ?: 0)
            return h
        }
    }

    /**
     * Structural equality across the full authorization. Earlier revisions
     * compared only `authToken` + `publicKey`, which dropped per-account
     * state silently and produced false-positive equality on
     * multi-account results with the same primary key.
     */
    @Suppress("DEPRECATION")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthorizationResult) return false
        return authToken == other.authToken &&
            publicKey.contentEquals(other.publicKey) &&
            accountLabel == other.accountLabel &&
            walletUriBase == other.walletUriBase &&
            walletIcon == other.walletIcon &&
            accounts == other.accounts &&
            signInResult == other.signInResult
    }

    @Suppress("DEPRECATION")
    override fun hashCode(): Int {
        var h = authToken.hashCode()
        h = 31 * h + publicKey.contentHashCode()
        h = 31 * h + (accountLabel?.hashCode() ?: 0)
        h = 31 * h + (walletUriBase?.hashCode() ?: 0)
        h = 31 * h + (walletIcon?.hashCode() ?: 0)
        h = 31 * h + accounts.hashCode()
        h = 31 * h + (signInResult?.hashCode() ?: 0)
        return h
    }
}
