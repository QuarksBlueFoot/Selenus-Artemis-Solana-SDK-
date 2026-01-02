/*
 * Copyright (c) 2024-2026 Bluefoot Labs. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * MwaSessionPersistence - Session recovery for Mobile Wallet Adapter.
 * 
 * Addresses common MWA issues:
 * - Sessions lost after app close/reopen (Issue #1364)
 * - PWA session persistence
 * - Background/foreground transitions
 * 
 * This is an Artemis enhancement not available in the base MWA SDK.
 */
package com.selenus.artemis.wallet.mwa

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Persistent session storage for MWA with encrypted credentials.
 * 
 * Enables session recovery after app restarts, addressing a major pain point
 * where users must reconnect their wallet every time the app is reopened.
 * 
 * Usage:
 * ```kotlin
 * val persistence = MwaSessionPersistence(context)
 * 
 * // After successful connection
 * persistence.saveSession(session)
 * 
 * // On app restart
 * val recovered = persistence.tryRecover()
 * if (recovered != null && persistence.validate(recovered)) {
 *     // Resume session without user interaction
 * } else {
 *     // Request fresh connection
 * }
 * ```
 */
class MwaSessionPersistence(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    
    private val prefs: SharedPreferences = try {
        // Use encrypted storage for auth tokens
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular prefs if encrypted storage fails
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val PREFS_NAME = "artemis_mwa_sessions"
        private const val KEY_SESSION = "session_data"
        private const val KEY_TIMESTAMP = "session_timestamp"
        private const val KEY_WALLET_NAME = "wallet_name"
        
        // Session considered stale after 24 hours
        private const val SESSION_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Persisted session data.
     */
    @Serializable
    data class PersistedSession(
        val authToken: String,
        val publicKeyBase64: String,
        val walletName: String?,
        val chain: String,
        val identityUri: String,
        val timestamp: Long
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > SESSION_MAX_AGE_MS
    }
    
    /**
     * Save session for later recovery.
     */
    suspend fun saveSession(
        authToken: String,
        publicKey: ByteArray,
        walletName: String?,
        chain: String,
        identityUri: String
    ) = withContext(Dispatchers.IO) {
        val session = PersistedSession(
            authToken = authToken,
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey),
            walletName = walletName,
            chain = chain,
            identityUri = identityUri,
            timestamp = System.currentTimeMillis()
        )
        
        prefs.edit()
            .putString(KEY_SESSION, json.encodeToString(session))
            .putLong(KEY_TIMESTAMP, session.timestamp)
            .putString(KEY_WALLET_NAME, walletName)
            .apply()
    }
    
    /**
     * Try to recover a previously saved session.
     * 
     * @return The persisted session if available and not expired, null otherwise
     */
    suspend fun tryRecover(): PersistedSession? = withContext(Dispatchers.IO) {
        val sessionJson = prefs.getString(KEY_SESSION, null) ?: return@withContext null
        
        try {
            val session = json.decodeFromString<PersistedSession>(sessionJson)
            if (session.isExpired) {
                clearSession()
                return@withContext null
            }
            session
        } catch (e: Exception) {
            clearSession()
            null
        }
    }
    
    /**
     * Get the last connected wallet name (for UI hints).
     */
    fun getLastWalletName(): String? = prefs.getString(KEY_WALLET_NAME, null)
    
    /**
     * Check if there's a potentially recoverable session.
     */
    fun hasSession(): Boolean {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
        return timestamp > 0 && System.currentTimeMillis() - timestamp < SESSION_MAX_AGE_MS
    }
    
    /**
     * Clear stored session data.
     */
    suspend fun clearSession() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    /**
     * Update the session timestamp to extend validity.
     */
    suspend fun touchSession() = withContext(Dispatchers.IO) {
        val sessionJson = prefs.getString(KEY_SESSION, null) ?: return@withContext
        try {
            val session = json.decodeFromString<PersistedSession>(sessionJson)
            val updated = session.copy(timestamp = System.currentTimeMillis())
            prefs.edit()
                .putString(KEY_SESSION, json.encodeToString(updated))
                .putLong(KEY_TIMESTAMP, updated.timestamp)
                .apply()
        } catch (e: Exception) {
            // Ignore - session might be corrupted
        }
    }
}

/**
 * Session recovery result.
 */
sealed class SessionRecoveryResult {
    /**
     * Session successfully recovered and validated.
     */
    data class Recovered(
        val authToken: String,
        val publicKey: ByteArray,
        val walletName: String?
    ) : SessionRecoveryResult()
    
    /**
     * No session to recover.
     */
    data object NoSession : SessionRecoveryResult()
    
    /**
     * Session found but expired.
     */
    data object Expired : SessionRecoveryResult()
    
    /**
     * Session found but validation failed (wallet may have revoked access).
     */
    data class Invalid(val reason: String) : SessionRecoveryResult()
}
