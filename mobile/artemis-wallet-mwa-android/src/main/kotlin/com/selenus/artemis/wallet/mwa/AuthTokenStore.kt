package com.selenus.artemis.wallet.mwa

import android.content.Context
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

/**
 * AuthTokenStore
 *
 * Mobile Wallet Adapter authToken can be persisted by apps (SharedPreferences, DataStore, etc).
 * Artemis provides a tiny interface so apps can control persistence.
 */
interface AuthTokenStore {
  fun get(): String?
  fun set(token: String?)
}

class InMemoryAuthTokenStore : AuthTokenStore {
  private var token: String? = null
  override fun get(): String? = token
  override fun set(token: String?) { this.token = token }
}

private val Context.artemisMwaDataStore by preferencesDataStore(name = "artemis_mwa")

class DataStoreAuthTokenStore(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AuthTokenStore {

  private val key = stringPreferencesKey("auth_token")
  @Volatile private var cached: String? = null

  init {
    // eager load once, async
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
