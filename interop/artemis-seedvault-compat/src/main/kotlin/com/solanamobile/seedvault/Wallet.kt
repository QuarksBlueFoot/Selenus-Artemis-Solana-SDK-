/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for com.solanamobile.seedvault.Wallet.
 * Delegates all calls to Artemis SeedVaultWallet.
 */
package com.solanamobile.seedvault

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.ArrayMap
import com.selenus.artemis.seedvault.SeedVaultWallet
import com.selenus.artemis.seedvault.internal.SeedVaultConstants
import com.selenus.artemis.seedvault.SigningRequest
import com.selenus.artemis.seedvault.SigningResponse
import com.selenus.artemis.seedvault.PublicKeyResponse

/**
 * Compatibility shim for `com.solanamobile.seedvault.Wallet`.
 *
 * Drop-in replacement — migrate from the Solana Mobile Seed Vault SDK
 * by simply swapping the dependency to `artemis-seedvault-compat`.
 * No code changes required in your app.
 */
object Wallet {

    // ── Exceptions (upstream parity) ────────────────────────────────────────
    class NotModifiedException(message: String) : Exception(message)
    class ActionFailedException(message: String) : Exception(message)

    // ── Seed Authorization ──────────────────────────────────────────────────
    @JvmStatic
    fun authorizeSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent =
        SeedVaultWallet.authorizeSeed(context, purpose)

    @JvmStatic
    fun onAuthorizeSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onAuthorizeSeedResult(resultCode, result)

    @JvmStatic
    fun createSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent =
        SeedVaultWallet.createSeed(context, purpose)

    @JvmStatic
    fun onCreateSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onCreateSeedResult(resultCode, result)

    @JvmStatic
    fun importSeed(context: Context, @WalletContractV1.Purpose purpose: Int): Intent =
        SeedVaultWallet.importSeed(context, purpose)

    @JvmStatic
    fun onImportSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onImportSeedResult(resultCode, result)

    // ── Transaction Signing ─────────────────────────────────────────────────
    @JvmStatic
    fun signTransaction(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        derivationPath: Uri,
        transaction: ByteArray
    ): Intent = SeedVaultWallet.signTransaction(context, authToken, derivationPath, transaction)

    @JvmStatic
    fun signTransactions(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent = SeedVaultWallet.signTransactions(context, authToken, signingRequests)

    @JvmStatic
    fun onSignTransactionsResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> =
        SeedVaultWallet.onSignTransactionsResult(resultCode, result)

    // ── Message Signing ─────────────────────────────────────────────────────
    @JvmStatic
    fun signMessage(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        derivationPath: Uri,
        message: ByteArray
    ): Intent = SeedVaultWallet.signMessage(context, authToken, derivationPath, message)

    @JvmStatic
    fun signMessages(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent = SeedVaultWallet.signMessages(context, authToken, signingRequests)

    @JvmStatic
    fun onSignMessagesResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> =
        SeedVaultWallet.onSignMessagesResult(resultCode, result)

    // ── Public Keys ─────────────────────────────────────────────────────────
    @JvmStatic
    fun requestPublicKey(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        derivationPath: Uri
    ): Intent = SeedVaultWallet.requestPublicKey(context, authToken, derivationPath)

    @JvmStatic
    fun requestPublicKeys(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        derivationPaths: ArrayList<Uri>
    ): Intent = SeedVaultWallet.requestPublicKeys(context, authToken, derivationPaths)

    @JvmStatic
    fun onRequestPublicKeysResult(resultCode: Int, result: Intent?): ArrayList<PublicKeyResponse> =
        SeedVaultWallet.onRequestPublicKeysResult(resultCode, result)

    // ── Settings ────────────────────────────────────────────────────────────
    @JvmStatic
    fun showSeedSettings(context: Context, @WalletContractV1.AuthToken authToken: Long): Intent =
        SeedVaultWallet.seedSettings(context, authToken)

    @JvmStatic
    fun onShowSeedSettingsResult(resultCode: Int, result: Intent?) {
        if (resultCode != Activity.RESULT_OK && resultCode != Activity.RESULT_CANCELED) {
            throw ActionFailedException("showSeedSettings failed with result=$resultCode")
        }
    }

    @JvmStatic
    @Deprecated("Use showSeedSettings", ReplaceWith("showSeedSettings(context, authToken)"))
    fun seedSettings(context: Context, @WalletContractV1.AuthToken authToken: Long): Intent =
        showSeedSettings(context, authToken)

    // ── Content Provider - Seeds ────────────────────────────────────────────
    @JvmStatic
    fun getAuthorizedSeeds(context: Context, projection: Array<String>): Cursor? =
        getAuthorizedSeeds(context, projection, null, null)

    @JvmStatic
    fun getAuthorizedSeeds(
        context: Context,
        projection: Array<String>,
        filterOnColumn: String?,
        value: Any?
    ): Cursor? {
        val queryArgs = createSingleColumnQuery(
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS, filterOnColumn, value)
        return context.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            projection,
            queryArgs,
            null)
    }

    @JvmStatic
    fun getAuthorizedSeed(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>
    ): Cursor? = context.contentResolver.query(
        ContentUris.withAppendedId(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI, authToken),
        projection,
        null,
        null)

    @JvmStatic
    fun deauthorizeSeed(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long
    ) {
        if (context.contentResolver.delete(
                ContentUris.withAppendedId(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI, authToken),
                null) == 0) {
            throw NotModifiedException("deauthorizeSeed for AuthToken=$authToken")
        }
    }

    @JvmStatic
    fun getUnauthorizedSeeds(context: Context, projection: Array<String>): Cursor? =
        getUnauthorizedSeeds(context, projection, null, null)

    @JvmStatic
    fun getUnauthorizedSeeds(
        context: Context,
        projection: Array<String>,
        filterOnColumn: String?,
        value: Any?
    ): Cursor? {
        val queryArgs = createSingleColumnQuery(
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS, filterOnColumn, value)
        return context.contentResolver.query(
            WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
            projection,
            queryArgs,
            null)
    }

    @JvmStatic
    fun hasUnauthorizedSeedsForPurpose(
        context: Context,
        @WalletContractV1.Purpose purpose: Int
    ): Boolean {
        val c = context.contentResolver.query(
            ContentUris.withAppendedId(WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI, purpose.toLong()),
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            null,
            null)
        if (c == null || !c.moveToFirst()) {
            throw IllegalStateException("Cursor does not contain expected data")
        }
        val result = c.getShort(1).toInt() != 0
        c.close()
        return result
    }

    // ── Content Provider - Accounts ─────────────────────────────────────────
    @JvmStatic
    fun getAccounts(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>
    ): Cursor? = getAccounts(context, authToken, projection, null, null)

    @JvmStatic
    fun getAccounts(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>,
        filterOnColumn: String?,
        value: Any?
    ): Cursor? {
        val queryArgs = createSingleColumnQuery(
            WalletContractV1.ACCOUNTS_ALL_COLUMNS, filterOnColumn, value)
        queryArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        return context.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            projection,
            queryArgs,
            null)
    }

    @JvmStatic
    fun getAccount(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId id: Long,
        projection: Array<String>
    ): Cursor? {
        val queryArgs = Bundle()
        queryArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        return context.contentResolver.query(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
            projection,
            queryArgs,
            null)
    }

    @JvmStatic
    fun updateAccountName(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId id: Long,
        name: String?
    ) {
        val updateArgs = Bundle()
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        val updateValues = ContentValues(1)
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_NAME, name)
        if (context.contentResolver.update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw NotModifiedException("updateAccountName for AuthToken=$authToken/id=$id")
        }
    }

    @JvmStatic
    fun updateAccountIsUserWallet(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId id: Long,
        isUserWallet: Boolean
    ) {
        val updateArgs = Bundle()
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        val updateValues = ContentValues(1)
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET,
            if (isUserWallet) 1.toShort() else 0.toShort())
        if (context.contentResolver.update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw NotModifiedException("updateAccountIsUserWallet for AuthToken=$authToken/id=$id")
        }
    }

    @JvmStatic
    fun updateAccountIsValid(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId id: Long,
        isValid: Boolean
    ) {
        val updateArgs = Bundle()
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        val updateValues = ContentValues(1)
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID,
            if (isValid) 1.toShort() else 0.toShort())
        if (context.contentResolver.update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw NotModifiedException("updateAccountIsValid for AuthToken=$authToken/id=$id")
        }
    }

    // ── Content Provider - Implementation Limits ────────────────────────────
    @JvmStatic
    fun getImplementationLimits(
        context: Context,
        projection: Array<String>
    ): Cursor? = getImplementationLimits(context, projection, null, null)

    @JvmStatic
    fun getImplementationLimits(
        context: Context,
        projection: Array<String>,
        filterOnColumn: String?,
        value: Any?
    ): Cursor? {
        val queryArgs = createSingleColumnQuery(
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS, filterOnColumn, value)
        return context.contentResolver.query(
            WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
            projection,
            queryArgs,
            null)
    }

    @JvmStatic
    fun getImplementationLimitsForPurpose(
        context: Context,
        @WalletContractV1.Purpose purpose: Int
    ): ArrayMap<String, Long> {
        val c = context.contentResolver.query(
            ContentUris.withAppendedId(WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI, purpose.toLong()),
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            null,
            null)
        if (c == null || !c.moveToNext()) {
            throw UnsupportedOperationException("Failed to get implementation limits")
        }
        val map = ArrayMap<String, Long>(3)
        map[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS] = c.getShort(1).toLong()
        map[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES] = c.getShort(2).toLong()
        map[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS] = c.getShort(3).toLong()
        c.close()
        return map
    }

    // ── Derivation Path Resolution ──────────────────────────────────────────
    @JvmStatic
    fun resolveDerivationPath(
        context: Context,
        derivationPath: Uri,
        @WalletContractV1.Purpose purpose: Int
    ): Uri = SeedVaultWallet.resolveDerivationPath(context, derivationPath, purpose)

    // ── Private helpers ─────────────────────────────────────────────────────
    private fun createSingleColumnQuery(
        allColumns: Array<String>,
        filterOnColumn: String?,
        value: Any?
    ): Bundle {
        val queryArgs = Bundle()
        if (filterOnColumn != null) {
            requireNotNull(value) { "value cannot be null when filterOnColumn is specified" }
            require(allColumns.contains(filterOnColumn)) {
                "Column '$filterOnColumn' is not a valid column"
            }
            queryArgs.putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "$filterOnColumn=?")
            queryArgs.putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(value.toString()))
        }
        return queryArgs
    }
}
