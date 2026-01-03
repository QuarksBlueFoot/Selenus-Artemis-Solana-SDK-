package com.selenus.artemis.nft

import com.selenus.artemis.runtime.Pubkey

/**
 * PDA helpers.
 *
 * Compatible with Metaplex Token Metadata addressing conventions.
 */
object Pdas {

  fun metadataPda(mint: Pubkey): Pubkey {
    return Pubkey.findProgramAddress(
      seeds = listOf(
        "metadata".toByteArray(),
        MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
        mint.bytes
      ),
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM
    ).address
  }

  fun masterEditionPda(mint: Pubkey): Pubkey {
    return Pubkey.findProgramAddress(
      seeds = listOf(
        "metadata".toByteArray(),
        MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
        mint.bytes,
        "edition".toByteArray()
      ),
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM
    ).address
  }

  fun editionMarkerPda(mint: Pubkey, edition: Long): Pubkey {
    val marker = (edition / 248L).toString()
    return Pubkey.findProgramAddress(
      seeds = listOf(
        "metadata".toByteArray(),
        MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
        mint.bytes,
        "edition".toByteArray(),
        marker.toByteArray()
      ),
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM
    ).address
  }

  fun tokenRecordPda(mint: Pubkey, tokenAccount: Pubkey): Pubkey {
    return Pubkey.findProgramAddress(
      seeds = listOf(
        "metadata".toByteArray(),
        MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
        mint.bytes,
        "token_record".toByteArray(),
        tokenAccount.bytes
      ),
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM
    ).address
  }

  fun collectionAuthorityRecordPda(collectionMint: Pubkey, authority: Pubkey): Pubkey {
    return Pubkey.findProgramAddress(
      seeds = listOf(
        "metadata".toByteArray(),
        MetaplexIds.TOKEN_METADATA_PROGRAM.bytes,
        collectionMint.bytes,
        "collection_authority".toByteArray(),
        authority.bytes
      ),
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM
    ).address
  }
}
