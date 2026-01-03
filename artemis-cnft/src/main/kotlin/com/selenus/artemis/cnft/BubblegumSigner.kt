package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.findProgramAddress

/**
 * Bubblegum "signer" PDA used for CPIs into Token Metadata during mintToCollectionV1.
 *
 * Seed used by common Bubblegum builds: ["collection_cpi"].
 * If your target program build differs, override by passing bubblegumSigner explicitly.
 */
object BubblegumSigner {
  fun pda(): Pubkey {
    return findProgramAddress(
      seeds = listOf("collection_cpi".encodeToByteArray()),
      programId = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID
    ).address
  }
}
