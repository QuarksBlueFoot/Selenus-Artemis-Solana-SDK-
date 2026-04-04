/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Drop-in replacement for com.solanamobile.seedvault.Wallet.
 * Delegates all calls to Artemis SeedVaultWallet.
 */
package com.solanamobile.seedvault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import com.selenus.artemis.seedvault.ImplementationLimits
import com.selenus.artemis.seedvault.SeedVaultWallet
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
    class ActionFailedException(message: String, val resultCode: Int) : Exception(message)

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
    @Deprecated("Use showSeedSettings", ReplaceWith("showSeedSettings(context, authToken)"))
    fun seedSettings(context: Context, @WalletContractV1.AuthToken authToken: Long): Intent =
        showSeedSettings(context, authToken)

    // ── Content Provider - Seeds ────────────────────────────────────────────
    @JvmStatic
    fun getAuthorizedSeeds(context: Context, projection: Array<String>): Cursor? =
        SeedVaultWallet.getAuthorizedSeeds(context, projection)

    @JvmStatic
    fun getAuthorizedSeeds(
        context: Context,
        projection: Array<String>,
        filterOnColumn: String?,
        value: String?
    ): Cursor? = SeedVaultWallet.getAuthorizedSeeds(context, projection, filterOnColumn, value)

    @JvmStatic
    fun getAuthorizedSeed(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>
    ): Cursor? = SeedVaultWallet.getAuthorizedSeed(context, authToken, projection)

    @JvmStatic
    fun deauthorizeSeed(context: Context, @WalletContractV1.AuthToken authToken: Long) =
        SeedVaultWallet.deauthorizeSeed(context, authToken)

    @JvmStatic
    fun getUnauthorizedSeeds(context: Context, projection: Array<String>): Cursor? =
        SeedVaultWallet.getUnauthorizedSeeds(context, projection)

    @JvmStatic
    fun hasUnauthorizedSeedsForPurpose(
        context: Context,
        @WalletContractV1.Purpose purpose: Int
    ): Boolean = SeedVaultWallet.hasUnauthorizedSeedsForPurpose(context, purpose)

    // ── Content Provider - Accounts ─────────────────────────────────────────
    @JvmStatic
    fun getAccounts(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>
    ): Cursor? = SeedVaultWallet.getAccounts(context, authToken, projection)

    @JvmStatic
    fun getAccounts(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        projection: Array<String>,
        filterOnColumn: String?,
        value: String?
    ): Cursor? = SeedVaultWallet.getAccounts(context, authToken, projection, filterOnColumn, value)

    @JvmStatic
    fun getAccount(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
        projection: Array<String>
    ): Cursor? = SeedVaultWallet.getAccount(context, authToken, accountId, projection)

    @JvmStatic
    fun updateAccountName(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
        name: String?
    ) = SeedVaultWallet.updateAccountName(context, authToken, accountId, name)

    @JvmStatic
    fun updateAccountIsUserWallet(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
        isUserWallet: Boolean
    ) = SeedVaultWallet.updateAccountIsUserWallet(context, authToken, accountId, isUserWallet)

    @JvmStatic
    fun updateAccountIsValid(
        context: Context,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
        isValid: Boolean
    ) = SeedVaultWallet.updateAccountIsValid(context, authToken, accountId, isValid)

    // ── Content Provider - Implementation Limits ────────────────────────────
    @JvmStatic
    fun getImplementationLimits(context: Context): ImplementationLimits =
        SeedVaultWallet.getImplementationLimits(context)

    @JvmStatic
    fun getImplementationLimitsForPurpose(
        context: Context,
        @WalletContractV1.Purpose purpose: Int
    ): ImplementationLimits = SeedVaultWallet.getImplementationLimitsForPurpose(context, purpose)

    // ── Derivation Path Resolution ──────────────────────────────────────────
    @JvmStatic
    fun resolveDerivationPath(
        context: Context,
        derivationPath: Uri,
        @WalletContractV1.Purpose purpose: Int
    ): Uri = SeedVaultWallet.resolveDerivationPath(context, derivationPath, purpose)
}
