package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.findProgramAddress

/**
 * PDA helpers for Bubblegum + Compression.
 */
object BubblegumPdas {

  /**
   * TreeConfig PDA.
   *
   * The Bubblegum TreeConfig PDA is derived from the merkle tree address.
   */
  fun treeConfig(merkleTree: Pubkey): Pubkey {
    return findProgramAddress(
      seeds = listOf(merkleTree.toByteArray()),
      programId = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID
    ).address
  }

  /**
   * Leaf asset ID PDA.
   *
   * Common seed scheme used by mpl-bubblegum clients:
   * ["asset", merkleTree, leafIndexLE(u64)]
   */
  fun leafAssetId(merkleTree: Pubkey, leafIndex: Long): Pubkey {
    val idx = ByteArray(8)
    var x = leafIndex
    for (i in 0 until 8) { idx[i] = (x and 0xff).toByte(); x = x ushr 8 }
    return findProgramAddress(
      seeds = listOf(
        "asset".encodeToByteArray(),
        merkleTree.toByteArray(),
        idx
      ),
      programId = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID
    ).address
  }

  /**
   * Voucher PDA used by redeem/decompress flows.
   *
   * Seed scheme in mpl-bubblegum clients: ["voucher", merkleTree, nonceLE(u64)].
   */
  fun voucher(merkleTree: Pubkey, nonce: Long): Pubkey {
    val n = ByteArray(8)
    var x = nonce
    for (i in 0 until 8) { n[i] = (x and 0xff).toByte(); x = x ushr 8 }
    return findProgramAddress(
      seeds = listOf(
        "voucher".encodeToByteArray(),
        merkleTree.toByteArray(),
        n
      ),
      programId = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID
    ).address
  }

  /**
   * Collection authority record PDA used by Token Metadata.
   * Seeds: ["metadata", tokenMetadataProgram, collectionMint, "collection_authority", collectionAuthority]
   */
  fun collectionAuthorityRecordPda(
    tokenMetadataProgram: Pubkey,
    collectionMint: Pubkey,
    collectionAuthority: Pubkey
  ): Pubkey {
    return findProgramAddress(
      seeds = listOf(
        "metadata".encodeToByteArray(),
        tokenMetadataProgram.toByteArray(),
        collectionMint.toByteArray(),
        "collection_authority".encodeToByteArray(),
        collectionAuthority.toByteArray(),
      ),
      programId = tokenMetadataProgram
    ).address
  }
}
