package com.selenus.artemis.candymachine

import com.selenus.artemis.runtime.Pubkey

object CandyMachineSysvars {
  /**
   * Instructions sysvar (instruction introspection).
   *
   * Address: Sysvar1nstructions1111111111111111111111111
   */
  val INSTRUCTIONS = Pubkey.fromBase58("Sysvar1nstructions1111111111111111111111111")

  /**
   * SlotHashes sysvar.
   *
   * Address: SysvarS1otHashes111111111111111111111111111
   */
  val SLOT_HASHES = Pubkey.fromBase58("SysvarS1otHashes111111111111111111111111111")
}
