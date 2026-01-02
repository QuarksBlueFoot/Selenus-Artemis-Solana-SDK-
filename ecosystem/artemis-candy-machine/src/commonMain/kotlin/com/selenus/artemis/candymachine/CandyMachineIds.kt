package com.selenus.artemis.candymachine

import com.selenus.artemis.runtime.Pubkey

/**
 * Program IDs for Metaplex Candy Machine (v3) and Candy Guard.
 *
 * These are stable, canonical program ids used on mainnet-beta.
 */
object CandyMachineIds {
  /** Metaplex Candy Machine Core program id. */
  val CANDY_MACHINE_CORE = Pubkey.fromBase58("CndyV3LdqHUfDLmE5naZjVN8rBZz4tqhdefbAnjHG3JR")

  /** Metaplex Candy Guard program id. */
  val CANDY_GUARD = Pubkey.fromBase58("Guard1JwRhJkVH6XZhzorc8AGB6C761TjR5mSxeMWxNk")
}
