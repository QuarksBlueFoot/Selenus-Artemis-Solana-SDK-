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
    // ── Seed Authorization ──────────────────────────────────────────────────
    fun authorizeSeed(context: Context, purpose: Int): Intent =
        SeedVaultWallet.authorizeSeed(context, purpose)

    fun onAuthorizeSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onAuthorizeSeedResult(resultCode, result)

    fun createSeed(context: Context, purpose: Int): Intent =
        SeedVaultWallet.createSeed(context, purpose)

    fun onCreateSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onCreateSeedResult(resultCode, result)

    fun importSeed(context: Context, purpose: Int): Intent =
        SeedVaultWallet.importSeed(context, purpose)

    fun onImportSeedResult(resultCode: Int, result: Intent?): Long =
        SeedVaultWallet.onImportSeedResult(resultCode, result)

    // ── Transaction Signing ─────────────────────────────────────────────────
    fun signTransaction(
        context: Context,
        authToken: Long,
        derivationPath: Uri,
        transaction: ByteArray
    ): Intent = SeedVaultWallet.signTransaction(context, authToken, derivationPath, transaction)

    fun signTransactions(
        context: Context,
        authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent = SeedVaultWallet.signTransactions(context, authToken, signingRequests)

    fun onSignTransactionsResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> =
        SeedVaultWallet.onSignTransactionsResult(resultCode, result)

    // ── Message Signing ─────────────────────────────────────────────────────
    fun signMessage(
        context: Context,
        authToken: Long,
        derivationPath: Uri,
        message: ByteArray
    ): Intent = SeedVaultWallet.signMessage(context, authToken, derivationPath, message)

    fun signMessages(
        context: Context,
        authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent = SeedVaultWallet.signMessages(context, authToken, signingRequests)

    fun onSignMessagesResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> =
        SeedVaultWallet.onSignMessagesResult(resultCode, result)

    // ── Public Keys ─────────────────────────────────────────────────────────
    fun requestPublicKey(
        context: Context,
        authToken: Long,
        derivationPath: Uri
    ): Intent = SeedVaultWallet.requestPublicKey(context, authToken, derivationPath)

    fun requestPublicKeys(
        context: Context,
        authToken: Long,
        derivationPaths: ArrayList<Uri>
    ): Intent = SeedVaultWallet.requestPublicKeys(context, authToken, derivationPaths)

    fun onRequestPublicKeysResult(resultCode: Int, result: Intent?): ArrayList<PublicKeyResponse> =
        SeedVaultWallet.onRequestPublicKeysResult(resultCode, result)

    // ── Content Provider ────────────────────────────────────────────────────
    fun getAuthorizedSeeds(context: Context, projection: Array<String>? = null): Cursor? =
        SeedVaultWallet.getAuthorizedSeeds(context, projection)

    fun getAccounts(context: Context, authToken: Long, projection: Array<String>? = null): Cursor? =
        SeedVaultWallet.getAccounts(context, authToken, projection)

    // ── Settings ────────────────────────────────────────────────────────────
    fun seedSettings(context: Context, authToken: Long): Intent =
        SeedVaultWallet.seedSettings(context, authToken)
}
