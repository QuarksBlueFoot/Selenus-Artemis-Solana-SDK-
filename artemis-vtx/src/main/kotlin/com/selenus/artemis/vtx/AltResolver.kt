package com.selenus.artemis.vtx

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey

/**
 * Fetches and decodes Address Lookup Table accounts using RpcApi, with slot-based cache invalidation.
 *
 * LUTs are relatively stable, but they do get extended/deactivated. This resolver caches decoded LUTs
 * and refreshes them after a TTL (in slots).
 */
class AltResolver(
  private val rpc: RpcApi,
  private val ttlSlots: Long = 150L
) {
  private data class Entry(
    val table: AddressLookupTableAccount,
    val fetchedAtSlot: Long
  )

  private val cache = mutableMapOf<Pubkey, Entry>()

  fun clearCache() = cache.clear()

  fun getCached(key: Pubkey): AddressLookupTableAccount? = cache[key]?.table

  private fun isFresh(entry: Entry, currentSlot: Long): Boolean {
    return (currentSlot - entry.fetchedAtSlot) <= ttlSlots
  }

  fun fetchOne(
    key: Pubkey,
    commitment: String = "finalized"
  ): AddressLookupTableAccount? {
    val currentSlot = rpc.getSlot(commitment)
    val existing = cache[key]
    if (existing != null && isFresh(existing, currentSlot)) return existing.table

    val data = rpc.getAccountInfoBase64(key.toString(), commitment) ?: run {
      cache.remove(key)
      return null
    }
    val decoded = AddressLookupTableAccount.decode(key, data)
    cache[key] = Entry(decoded, currentSlot)
    return decoded
  }

  fun fetchMany(
    keys: List<Pubkey>,
    commitment: String = "finalized"
  ): List<AddressLookupTableAccount> {
    if (keys.isEmpty()) return emptyList()
    val currentSlot = rpc.getSlot(commitment)
    val out = mutableListOf<AddressLookupTableAccount>()
    for (k in keys) {
      val existing = cache[k]
      if (existing != null && isFresh(existing, currentSlot)) {
        out.add(existing.table)
        continue
      }
      val data = rpc.getAccountInfoBase64(k.toString(), commitment)
      if (data == null) {
        cache.remove(k)
        continue
      }
      val decoded = AddressLookupTableAccount.decode(k, data)
      cache[k] = Entry(decoded, currentSlot)
      out.add(decoded)
    }
    return out
  }
}
