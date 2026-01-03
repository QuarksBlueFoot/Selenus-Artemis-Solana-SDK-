package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey

object MetadataPdas {
  fun metadataPda(mint: Pubkey): Pubkey {
    val seeds = listOf(MetaplexIds.METADATA_SEED, MetaplexIds.TOKEN_METADATA_PROGRAM.bytes, mint.bytes)
    return Pda.findProgramAddress(seeds, MetaplexIds.TOKEN_METADATA_PROGRAM).address
  }

  fun masterEditionPda(mint: Pubkey): Pubkey {
    val seeds = listOf(
      MetaplexIds.METADATA_SEED,
      MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
      mint.bytes,
      MetaplexIds.EDITION_SEED
    )
    return Pda.findProgramAddress(seeds, MetaplexIds.TOKEN_METADATA_PROGRAM).address
  }
}
