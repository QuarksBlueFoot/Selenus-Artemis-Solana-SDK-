package com.selenus.artemis.wallet

import java.util.concurrent.atomic.AtomicReference

class WalletCapabilityCache(private val adapter: WalletAdapter) {
  private val cached = AtomicReference<WalletCapabilities?>(null)

  suspend fun get(): WalletCapabilities {
    val v = cached.get()
    if (v != null) return v
    val cap = adapter.getCapabilities()
    cached.set(cap)
    return cap
  }
}
