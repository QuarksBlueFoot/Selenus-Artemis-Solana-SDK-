package com.selenus.artemis.wallet.mwa

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AuthTokenStore
 *
 * Persists the MWA auth token. The default [AuthTokenStore.default] returns
 * a Keystore-backed implementation so tokens sit behind a non-exportable
 * AES-256-GCM key tied to the device. Apps that genuinely need plaintext
 * (e.g. uninstalled-key testing scenarios) can opt in via
 * [DataStoreAuthTokenStore] explicitly.
 */
interface AuthTokenStore {
    fun get(): String?
    fun set(token: String?)

    companion object {
        /**
         * Platform default. Uses [KeystoreEncryptedAuthTokenStore] when
         * Android Keystore is reachable; raises [IllegalStateException] if
         * the keystore cannot initialise so apps fail closed instead of
         * silently degrading to plaintext storage.
         */
        @JvmStatic
        fun default(context: Context): AuthTokenStore =
            KeystoreEncryptedAuthTokenStore(context.applicationContext)
    }
}

class InMemoryAuthTokenStore : AuthTokenStore {
    @Volatile private var token: String? = null
    override fun get(): String? = token
    override fun set(token: String?) { this.token = token }
}

private val Context.artemisMwaDataStore by preferencesDataStore(name = "artemis_mwa")

/**
 * Plaintext DataStore-backed store. Retained for apps that opt out of the
 * keystore path (testing, non-production builds). The companion now warns
 * via KDoc — prefer [AuthTokenStore.default] in production code.
 */
class DataStoreAuthTokenStore(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AuthTokenStore {

    private val key = stringPreferencesKey("auth_token")
    @Volatile private var cached: String? = null

    init {
        scope.launch {
            cached = context.artemisMwaDataStore.data.first()[key]
        }
    }

    override fun get(): String? = cached

    override fun set(token: String?) {
        cached = token
        scope.launch {
            context.artemisMwaDataStore.edit { prefs ->
                if (token == null) prefs.remove(key) else prefs[key] = token
            }
        }
    }

    companion object {
        fun from(context: Context): DataStoreAuthTokenStore = DataStoreAuthTokenStore(context)
    }
}

/**
 * Keystore-backed AES-256-GCM store.
 *
 * Key lives in the AndroidKeyStore under alias `artemis_mwa_auth`. The token
 * bytes are serialised as `[12-byte IV][ciphertext+tag]` and stored in a
 * dedicated SharedPreferences bucket. On any initialisation failure the
 * constructor throws — callers that require a functional token store should
 * react to the exception rather than fall through to plaintext storage.
 */
class KeystoreEncryptedAuthTokenStore(
    context: Context,
    private val prefsName: String = "artemis_mwa_secure",
    private val keyAlias: String = "artemis_mwa_auth"
) : AuthTokenStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val secretKey: SecretKey = loadOrCreateKey()

    @Volatile private var cached: String? = try {
        decryptFromPrefs()
    } catch (e: Exception) {
        // Decryption failure on a stale ciphertext (keystore reset, app
        // reinstall): wipe the bucket so the next set() writes a fresh
        // blob. Do NOT fall back to plaintext.
        prefs.edit().remove(TOKEN_KEY).apply()
        null
    }

    override fun get(): String? = cached

    override fun set(token: String?) {
        cached = token
        if (token == null) {
            prefs.edit().remove(TOKEN_KEY).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == 12) { "expected 12-byte GCM IV, got ${iv.size}" }
        val ct = cipher.doFinal(token.encodeToByteArray())
        val blob = ByteArray(iv.size + ct.size).also { buf ->
            iv.copyInto(buf, 0)
            ct.copyInto(buf, iv.size)
        }
        val encoded = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)
        prefs.edit().putString(TOKEN_KEY, encoded).apply()
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    private fun decryptFromPrefs(): String? {
        val encoded = prefs.getString(TOKEN_KEY, null) ?: return null
        val blob = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        if (blob.size <= 12) return null
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ct).decodeToString()
    }

    companion object {
        private const val TOKEN_KEY = "auth_token_v1"
    }
}
