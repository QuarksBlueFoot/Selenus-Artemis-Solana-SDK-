package com.selenus.artemis.wallet

class WalletCapabilityCache(private val adapter: WalletAdapter) {
  @Volatile
  private var cached: WalletCapabilities? = null

  suspend fun get(): WalletCapabilities {
    val v = cached
    if (v != null) return v
    val cap = adapter.getCapabilities()
    cached = cap
    return cap
  }
}
