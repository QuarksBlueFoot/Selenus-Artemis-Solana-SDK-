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
import com.selenus.artemis.wallet.SessionManager
import java.security.KeyStore
import java.security.SecureRandom
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
 * via KDoc - prefer [AuthTokenStore.default] in production code.
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
 * constructor throws - callers that require a functional token store should
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

/**
 * Persistent HMAC secret store for [SessionManager] auth-token validation.
 *
 * `SessionManager` can validate auth tokens after process death only when the
 * same 32-byte HMAC secret is reinstalled at app startup. The default Android
 * implementation stores that secret encrypted with AndroidKeyStore and refuses
 * to fall back to plaintext storage.
 */
interface MwaSessionSecretStore {
    fun getOrCreate(): ByteArray
    fun rotate(): ByteArray
    fun clear()

    fun installIntoSessionManager(): ByteArray {
        val secret = getOrCreate()
        SessionManager.installPersistedSecret(secret)
        return secret.copyOf()
    }

    companion object {
        @JvmStatic
        fun default(context: Context): MwaSessionSecretStore =
            KeystoreEncryptedMwaSessionSecretStore(context.applicationContext)

        @JvmStatic
        fun installDefault(context: Context): ByteArray =
            default(context).installIntoSessionManager()
    }
}

class InMemoryMwaSessionSecretStore(initialSecret: ByteArray? = null) : MwaSessionSecretStore {
    @Volatile private var secret: ByteArray? = initialSecret?.copyOf()?.also {
        require(it.size == SESSION_SECRET_SIZE) { "session secret must be $SESSION_SECRET_SIZE bytes" }
    }

    override fun getOrCreate(): ByteArray {
        secret?.let { return it.copyOf() }
        return rotate()
    }

    override fun rotate(): ByteArray {
        val fresh = randomSessionSecret()
        secret = fresh.copyOf()
        return fresh
    }

    override fun clear() {
        secret = null
    }
}

class KeystoreEncryptedMwaSessionSecretStore(
    context: Context,
    private val prefsName: String = "artemis_mwa_secure",
    private val keyAlias: String = "artemis_mwa_session_secret"
) : MwaSessionSecretStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val secretKey: SecretKey = loadOrCreateKey()

    @Volatile private var cached: ByteArray? = null

    override fun getOrCreate(): ByteArray {
        cached?.let { return it.copyOf() }
        val encrypted = prefs.getString(SESSION_SECRET_KEY, null)
        if (encrypted != null) {
            val secret = decryptSecret(encrypted)
            cached = secret.copyOf()
            return secret
        }
        return rotate()
    }

    override fun rotate(): ByteArray {
        val fresh = randomSessionSecret()
        val encoded = encryptSecret(fresh)
        prefs.edit().putString(SESSION_SECRET_KEY, encoded).apply()
        cached = fresh.copyOf()
        return fresh
    }

    override fun clear() {
        cached = null
        prefs.edit().remove(SESSION_SECRET_KEY).apply()
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

    private fun encryptSecret(secret: ByteArray): String {
        require(secret.size == SESSION_SECRET_SIZE) { "session secret must be $SESSION_SECRET_SIZE bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == GCM_IV_SIZE) { "expected $GCM_IV_SIZE-byte GCM IV, got ${iv.size}" }
        val ciphertext = cipher.doFinal(secret)
        val blob = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(blob, destinationOffset = 0)
        ciphertext.copyInto(blob, destinationOffset = iv.size)
        return android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)
    }

    private fun decryptSecret(encoded: String): ByteArray {
        val blob = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        if (blob.size <= GCM_IV_SIZE) {
            clear()
            throw IllegalStateException("Stored MWA session secret blob is truncated")
        }
        val iv = blob.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = blob.copyOfRange(GCM_IV_SIZE, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).also {
                require(it.size == SESSION_SECRET_SIZE) { "Stored MWA session secret has invalid length" }
            }
        } catch (e: Exception) {
            clear()
            throw IllegalStateException("Stored MWA session secret could not be decrypted", e)
        }
    }

    companion object {
        private const val SESSION_SECRET_KEY = "session_secret_v1"
    }
}

private const val SESSION_SECRET_SIZE = 32
private const val GCM_IV_SIZE = 12

private fun randomSessionSecret(): ByteArray = ByteArray(SESSION_SECRET_SIZE).also {
    SecureRandom().nextBytes(it)
}
