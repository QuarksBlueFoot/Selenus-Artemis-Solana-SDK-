/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Wallet - High-level Seed Vault programming interface.
 * 
 * Full parity with com.solanamobile.seedvault.Wallet from the upstream SDK v0.4.0.
 * 
 * This class provides static helper methods for all Seed Vault interactions:
 * - Seed authorization (authorizeSeed, createSeed, importSeed)
 * - Transaction signing (signTransaction, signTransactions)
 * - Message signing (signMessage, signMessages)
 * - Public key requests (requestPublicKey, requestPublicKeys)
 * - Content provider queries (getAuthorizedSeeds, getAccounts, getImplementationLimits)
 * - Derivation path resolution (resolveDerivationPath)
 * - Seed settings display (seedSettings - privileged only)
 * - Account management (updateAccountName, updateAccountIsUserWallet, updateAccountIsValid)
 * 
 * All Intent-based methods return Intents for use with startActivityForResult.
 * Companion onXxxResult methods process the result from onActivityResult.
 */
package com.selenus.artemis.seedvault

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import com.selenus.artemis.seedvault.internal.SeedVaultCheck
import com.selenus.artemis.seedvault.internal.SeedVaultConstants
import com.selenus.artemis.seedvault.internal.SeedVaultConstants.AUTHORITY_WALLET

/**
 * High-level Seed Vault Wallet programming interface.
 * 
 * Full API parity with Solana Mobile Seed Vault SDK v0.4.0.
 * 
 * Usage:
 * ```kotlin
 * // Authorize seed
 * val intent = SeedVaultWallet.authorizeSeed(context, PURPOSE_SIGN_SOLANA_TRANSACTION)
 * startActivityForResult(intent, REQUEST_AUTHORIZE)
 * 
 * // In onActivityResult:
 * val authToken = SeedVaultWallet.onAuthorizeSeedResult(resultCode, data)
 * 
 * // Sign transaction
 * val signIntent = SeedVaultWallet.signTransaction(
 *     context, authToken, derivationPath.toUri(), transactionBytes
 * )
 * startActivityForResult(signIntent, REQUEST_SIGN)
 * ```
 */
object SeedVaultWallet {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Thrown when a modification target (e.g., account) does not exist.
     */
    class NotModifiedException(message: String) : Exception(message)
    
    /**
     * Thrown when a Seed Vault action fails.
     */
    class ActionFailedException(message: String, val resultCode: Int) : Exception(message)
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SEED AUTHORIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Request authorization of a seed for the specified purpose.
     * 
     * @param context The Context
     * @param purpose One of the PURPOSE_* constants
     * @return Intent for startActivityForResult
     */
    fun authorizeSeed(context: Context, purpose: Int): Intent {
        val intent = Intent()
            .setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
            .setAction(SeedVaultConstants.ACTION_AUTHORIZE_SEED_ACCESS)
            .putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from authorizeSeed.
     * 
     * @param resultCode Result code from onActivityResult
     * @param result Intent from onActivityResult
     * @return The auth token for the newly authorized seed
     * @throws ActionFailedException if authorization failed
     */
    fun onAuthorizeSeedResult(resultCode: Int, result: Intent?): Long {
        if (resultCode != Activity.RESULT_OK || result == null) {
            throw ActionFailedException(
                "Seed authorization failed with result code $resultCode",
                resultCode
            )
        }
        return result.getLongExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, -1L).also {
            if (it == -1L) throw ActionFailedException("No auth token in result", resultCode)
        }
    }
    
    /**
     * Request creation of a new seed.
     * 
     * NOTE: Uses implicit Intent (no package specified).
     * 
     * @param context The Context
     * @param purpose One of the PURPOSE_* constants
     * @return Intent for startActivityForResult
     */
    fun createSeed(context: Context, purpose: Int): Intent {
        val intent = Intent()
            .setAction(SeedVaultConstants.ACTION_CREATE_SEED)
            .putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from createSeed.
     * Returns the auth token for the newly created seed.
     */
    fun onCreateSeedResult(resultCode: Int, result: Intent?): Long {
        return onAuthorizeSeedResult(resultCode, result)
    }
    
    /**
     * Request import of an existing seed.
     * 
     * NOTE: Uses implicit Intent (no package specified).
     * 
     * @param context The Context
     * @param purpose One of the PURPOSE_* constants
     * @return Intent for startActivityForResult
     */
    fun importSeed(context: Context, purpose: Int): Intent {
        val intent = Intent()
            .setAction(SeedVaultConstants.ACTION_IMPORT_SEED)
            .putExtra(SeedVaultConstants.EXTRA_PURPOSE, purpose)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from importSeed.
     */
    fun onImportSeedResult(resultCode: Int, result: Intent?): Long {
        return onAuthorizeSeedResult(resultCode, result)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION SIGNING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sign a single transaction.
     * 
     * @param context The Context
     * @param authToken Auth token from seed authorization
     * @param derivationPath BIP derivation path URI for the signing key
     * @param transaction Transaction bytes to sign
     * @return Intent for startActivityForResult
     */
    fun signTransaction(
        context: Context,
        authToken: Long,
        derivationPath: Uri,
        transaction: ByteArray
    ): Intent {
        val req = SigningRequest(transaction, derivationPath)
        return signTransactions(context, authToken, arrayListOf(req))
    }
    
    /**
     * Sign multiple transactions.
     * 
     * @param context The Context
     * @param authToken Auth token from seed authorization
     * @param signingRequests List of signing requests
     * @return Intent for startActivityForResult
     */
    fun signTransactions(
        context: Context,
        authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent {
        require(signingRequests.isNotEmpty()) { "signingRequests must not be empty" }
        val intent = Intent()
            .setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
            .setAction(SeedVaultConstants.ACTION_SIGN_TRANSACTION)
            .putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            .putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from signTransaction(s).
     * 
     * @param resultCode Result code from onActivityResult
     * @param result Intent from onActivityResult
     * @return List of signing responses
     * @throws ActionFailedException if signing failed
     */
    fun onSignTransactionsResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> {
        if (resultCode != Activity.RESULT_OK || result == null) {
            throw ActionFailedException(
                "Transaction signing failed with result code $resultCode",
                resultCode
            )
        }
        @Suppress("DEPRECATION")
        return result.getParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_RESPONSE)
            ?: throw ActionFailedException("No signing response in result", resultCode)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MESSAGE SIGNING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sign a single message.
     */
    fun signMessage(
        context: Context,
        authToken: Long,
        derivationPath: Uri,
        message: ByteArray
    ): Intent {
        val req = SigningRequest(message, derivationPath)
        return signMessages(context, authToken, arrayListOf(req))
    }
    
    /**
     * Sign multiple messages.
     */
    fun signMessages(
        context: Context,
        authToken: Long,
        signingRequests: ArrayList<SigningRequest>
    ): Intent {
        require(signingRequests.isNotEmpty()) { "signingRequests must not be empty" }
        val intent = Intent()
            .setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
            .setAction(SeedVaultConstants.ACTION_SIGN_MESSAGE)
            .putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            .putParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_REQUEST, signingRequests)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from signMessage(s).
     */
    fun onSignMessagesResult(resultCode: Int, result: Intent?): ArrayList<SigningResponse> {
        if (resultCode != Activity.RESULT_OK || result == null) {
            throw ActionFailedException(
                "Message signing failed with result code $resultCode",
                resultCode
            )
        }
        @Suppress("DEPRECATION")
        return result.getParcelableArrayListExtra(SeedVaultConstants.EXTRA_SIGNING_RESPONSE)
            ?: throw ActionFailedException("No signing response in result", resultCode)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC KEY REQUESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Request a public key for a single derivation path.
     */
    fun requestPublicKey(
        context: Context,
        authToken: Long,
        derivationPath: Uri
    ): Intent {
        return requestPublicKeys(context, authToken, arrayListOf(derivationPath))
    }
    
    /**
     * Request public keys for multiple derivation paths.
     */
    fun requestPublicKeys(
        context: Context,
        authToken: Long,
        derivationPaths: ArrayList<Uri>
    ): Intent {
        require(derivationPaths.isNotEmpty()) { "derivationPaths must not be empty" }
        val intent = Intent()
            .setPackage(SeedVaultConstants.PACKAGE_SEED_VAULT)
            .setAction(SeedVaultConstants.ACTION_GET_PUBLIC_KEY)
            .putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
            .putParcelableArrayListExtra(SeedVaultConstants.EXTRA_DERIVATION_PATH, derivationPaths)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    /**
     * Process the result from requestPublicKey(s).
     */
    fun onRequestPublicKeysResult(resultCode: Int, result: Intent?): ArrayList<PublicKeyResponse> {
        if (resultCode != Activity.RESULT_OK || result == null) {
            throw ActionFailedException(
                "Public key request failed with result code $resultCode",
                resultCode
            )
        }
        @Suppress("DEPRECATION")
        return result.getParcelableArrayListExtra(SeedVaultConstants.EXTRA_PUBLIC_KEY)
            ?: throw ActionFailedException("No public key response in result", resultCode)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SEED SETTINGS (PRIVILEGED ONLY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Show seed settings UI. Requires privileged access.
     * 
     * @param context The Context
     * @param authToken Auth token for the seed to show settings for
     * @return Intent for startActivityForResult
     */
    fun seedSettings(context: Context, authToken: Long): Intent {
        val intent = Intent()
            .setAction(SeedVaultConstants.ACTION_SEED_SETTINGS)
            .putExtra(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken)
        SeedVaultCheck.resolveComponentForIntent(context, intent)
        return intent
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - SEEDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get authorized seeds for the current app.
     */
    fun getAuthorizedSeeds(
        context: Context,
        projection: Array<String>
    ): Cursor? {
        return getAuthorizedSeeds(context, projection, null, null)
    }
    
    /**
     * Get authorized seeds matching a query.
     */
    fun getAuthorizedSeeds(
        context: Context,
        projection: Array<String>,
        filterOnColumn: String?,
        value: String?
    ): Cursor? {
        val selection = if (filterOnColumn != null) "$filterOnColumn = ?" else null
        val selectionArgs = if (value != null) arrayOf(value) else null
        return context.contentResolver.query(
            SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
    }
    
    /**
     * Get a specific authorized seed.
     */
    fun getAuthorizedSeed(
        context: Context,
        authToken: Long,
        projection: Array<String>
    ): Cursor? {
        return context.contentResolver.query(
            SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI,
            projection,
            "${SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_TOKEN} = ?",
            arrayOf(authToken.toString()),
            null
        )
    }
    
    /**
     * Deauthorize a seed.
     * 
     * @param context The Context
     * @param authToken Auth token of the seed to deauthorize
     * @throws NotModifiedException if the seed was not authorized
     */
    fun deauthorizeSeed(context: Context, authToken: Long) {
        val rows = context.contentResolver.delete(
            SeedVaultConstants.AUTHORIZED_SEEDS_CONTENT_URI,
            "${SeedVaultConstants.AUTHORIZED_SEEDS_AUTH_TOKEN} = ?",
            arrayOf(authToken.toString())
        )
        if (rows == 0) {
            throw NotModifiedException("Seed with auth token $authToken was not authorized")
        }
    }
    
    /**
     * Check if there are unauthorized seeds available.
     */
    fun getUnauthorizedSeeds(
        context: Context,
        projection: Array<String>
    ): Cursor? {
        return context.contentResolver.query(
            SeedVaultConstants.UNAUTHORIZED_SEEDS_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
    }
    
    /**
     * Check if there are unauthorized seeds for a specific purpose.
     */
    fun hasUnauthorizedSeedsForPurpose(context: Context, purpose: Int): Boolean {
        val cursor = context.contentResolver.query(
            SeedVaultConstants.UNAUTHORIZED_SEEDS_CONTENT_URI,
            arrayOf(SeedVaultConstants.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS),
            "${SeedVaultConstants.UNAUTHORIZED_SEEDS_AUTH_PURPOSE} = ?",
            arrayOf(purpose.toString()),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getShort(0).toInt() == 1
            }
        }
        return false
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - ACCOUNTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get accounts for an authorized seed.
     */
    fun getAccounts(
        context: Context,
        authToken: Long,
        projection: Array<String>
    ): Cursor? {
        return getAccounts(context, authToken, projection, null, null)
    }
    
    /**
     * Get accounts with filter.
     */
    fun getAccounts(
        context: Context,
        authToken: Long,
        projection: Array<String>,
        filterOnColumn: String?,
        value: String?
    ): Cursor? {
        val uri = SeedVaultConstants.ACCOUNTS_CONTENT_URI.buildUpon()
            .appendQueryParameter(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toString())
            .build()
        
        val selection = if (filterOnColumn != null) "$filterOnColumn = ?" else null
        val selectionArgs = if (value != null) arrayOf(value) else null
        
        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)
    }
    
    /**
     * Get user wallet accounts.
     */
    fun getUserWallets(context: Context, authToken: Long): Cursor? {
        return getAccounts(
            context,
            authToken,
            SeedVaultConstants.ACCOUNTS_ALL_COLUMNS,
            SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_USER_WALLET,
            "1"
        )
    }
    
    /**
     * Update an account's name.
     */
    fun updateAccountName(
        context: Context,
        authToken: Long,
        accountId: Long,
        name: String?
    ) {
        val values = ContentValues().apply {
            put(SeedVaultConstants.ACCOUNTS_ACCOUNT_NAME, name ?: "")
        }
        updateAccount(context, authToken, accountId, values)
    }
    
    /**
     * Update an account's user wallet flag.
     */
    fun updateAccountIsUserWallet(
        context: Context,
        authToken: Long,
        accountId: Long,
        isUserWallet: Boolean
    ) {
        val values = ContentValues().apply {
            put(SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_USER_WALLET, if (isUserWallet) 1 else 0)
        }
        updateAccount(context, authToken, accountId, values)
    }
    
    /**
     * Update an account's validity flag.
     */
    fun updateAccountIsValid(
        context: Context,
        authToken: Long,
        accountId: Long,
        isValid: Boolean
    ) {
        val values = ContentValues().apply {
            put(SeedVaultConstants.ACCOUNTS_ACCOUNT_IS_VALID, if (isValid) 1 else 0)
        }
        updateAccount(context, authToken, accountId, values)
    }
    
    private fun updateAccount(
        context: Context,
        authToken: Long,
        accountId: Long,
        values: ContentValues
    ) {
        val uri = SeedVaultConstants.ACCOUNTS_CONTENT_URI.buildUpon()
            .appendQueryParameter(SeedVaultConstants.EXTRA_AUTH_TOKEN, authToken.toString())
            .build()
        
        val rows = context.contentResolver.update(
            uri,
            values,
            "${SeedVaultConstants.ACCOUNTS_ACCOUNT_ID} = ?",
            arrayOf(accountId.toString())
        )
        if (rows == 0) {
            throw NotModifiedException("Account $accountId not found")
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT PROVIDER - IMPLEMENTATION LIMITS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get implementation limits for the Seed Vault.
     */
    fun getImplementationLimits(context: Context): ImplementationLimits {
        val cursor = context.contentResolver.query(
            SeedVaultConstants.IMPLEMENTATION_LIMITS_CONTENT_URI,
            SeedVaultConstants.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            null,
            null,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val maxSigningReqs = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS)
                )
                val maxSigs = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES)
                )
                val maxPubKeys = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS)
                )
                return ImplementationLimits(maxSigningReqs, maxSigs, maxPubKeys)
            }
        }
        
        return ImplementationLimits.DEFAULT
    }
    
    /**
     * Get implementation limits for a specific purpose.
     */
    fun getImplementationLimitsForPurpose(context: Context, purpose: Int): ImplementationLimits {
        val cursor = context.contentResolver.query(
            SeedVaultConstants.IMPLEMENTATION_LIMITS_CONTENT_URI,
            SeedVaultConstants.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            "${SeedVaultConstants.IMPLEMENTATION_LIMITS_AUTH_PURPOSE} = ?",
            arrayOf(purpose.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val maxSigningReqs = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS)
                )
                val maxSigs = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES)
                )
                val maxPubKeys = it.getInt(
                    it.getColumnIndexOrThrow(SeedVaultConstants.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS)
                )
                return ImplementationLimits(maxSigningReqs, maxSigs, maxPubKeys)
            }
        }
        
        return ImplementationLimits.DEFAULT
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DERIVATION PATH RESOLUTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Resolve a BIP derivation path URI for a given purpose.
     * 
     * Translates BIP44 paths to BIP32, and applies purpose-specific normalization
     * (e.g., hardening all levels for Solana).
     * 
     * @param context The Context
     * @param derivationPath A bip32:// or bip44:// URI
     * @param purpose The PURPOSE_* constant
     * @return Resolved BIP32 derivation path URI
     */
    fun resolveDerivationPath(
        context: Context,
        derivationPath: Uri,
        purpose: Int
    ): Uri {
        val cursor = context.contentResolver.query(
            SeedVaultConstants.WALLET_PROVIDER_CONTENT_URI_BASE.buildUpon()
                .appendPath(SeedVaultConstants.RESOLVE_BIP32_DERIVATION_PATH_METHOD)
                .appendQueryParameter("derivation_path", derivationPath.toString())
                .appendQueryParameter("purpose", purpose.toString())
                .build(),
            null,
            null,
            null,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val resolved = it.getString(0)
                return Uri.parse(resolved)
            }
        }
        
        // Fallback: resolve locally
        return resolveDerivationPathLocal(derivationPath, purpose)
    }
    
    /**
     * Local derivation path resolution (without content provider).
     */
    private fun resolveDerivationPathLocal(derivationPath: Uri, purpose: Int): Uri {
        val bipPath = BipDerivationPath.fromUri(derivationPath)
        
        return when (bipPath) {
            is Bip32DerivationPath -> bipPath.normalize(purpose).toUri()
            is Bip44DerivationPath -> bipPath.toBip32DerivationPath(purpose).toUri()
        }
    }
}
