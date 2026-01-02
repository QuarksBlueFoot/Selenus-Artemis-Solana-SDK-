package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import java.util.LinkedHashSet

/**
 * AltSessionCache
 *
 * Tracks addresses that appear across a session so you can:
 * - prebuild lookup tables
 * - reuse the same tables across frames
 * - reduce tx size for long matches
 *
 * This cache does not create ALTs by itself.
 * It is designed to feed whichever ALT creation flow your app chooses.
 */
class AltSessionCache(
  private val maxAddresses: Int = 5_000
) {
  private val seen = LinkedHashSet<Pubkey>()

  fun add(address: Pubkey) {
    if (seen.size >= maxAddresses) return
    seen.add(address)
  }

  fun addAll(addresses: Collection<Pubkey>) {
    for (a in addresses) {
      if (seen.size >= maxAddresses) return
      seen.add(a)
    }
  }

  fun snapshot(): List<Pubkey> = seen.toList()

  fun clear() = seen.clear()
}
