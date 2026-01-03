package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey

/**
 * Minimal client-side view of an on-chain Address Lookup Table (ALT).
 *
 * The on-chain account stores meta in the first 56 bytes, followed by a raw list of 32-byte pubkeys.
 * Client code commonly only needs the addresses list to resolve lookup indices.
 */
data class AddressLookupTableAccount(
  val key: Pubkey,
  val addresses: List<Pubkey>
) {
  companion object {
    // From Solana lookup table program state: LOOKUP_TABLE_META_SIZE
    private const val META_SIZE = 56

    fun decode(key: Pubkey, data: ByteArray): AddressLookupTableAccount {
      if (data.size < META_SIZE) return AddressLookupTableAccount(key, emptyList())
      val addrs = mutableListOf<Pubkey>()
      var i = META_SIZE
      while (i + 32 <= data.size) {
        val slice = data.copyOfRange(i, i + 32)
        addrs.add(Pubkey(slice))
        i += 32
      }
      return AddressLookupTableAccount(key, addrs)
    }
  }
}
