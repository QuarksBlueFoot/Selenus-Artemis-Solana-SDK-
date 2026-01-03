package com.selenus.artemis.candymachine

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey

object CandyMachinePdas {
  /**
   * Candy Machine authority PDA.
   *
   * Seeds: ["candy_machine", <candyMachineId>]
   */
  fun findCandyMachineAuthorityPda(candyMachineId: Pubkey): Pda.Result {
    val seeds = listOf("candy_machine".encodeToByteArray(), candyMachineId.bytes)
    return Pda.findProgramAddress(seeds, CandyMachineIds.CANDY_MACHINE_CORE)
  }
}
