package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey

/**
 * Merkle leaf proof data for a compressed NFT.
 *
 * NOTE: In Bubblegum, the *proof nodes* are passed as remaining accounts, not embedded
 * inside instruction data. We keep [proof] here only as a convenience container.
 */
data class ProofArgs(
  val root: ByteArray,
  val dataHash: ByteArray,
  val creatorHash: ByteArray,
  val nonce: Long,
  val index: Int,
  /** Remaining accounts (proof nodes) to append to the instruction. */
  val proof: List<Pubkey> = emptyList()
) {
  fun serialize(): ByteArray {
    require(root.size == 32) { "root must be 32 bytes" }
    require(dataHash.size == 32) { "dataHash must be 32 bytes" }
    require(creatorHash.size == 32) { "creatorHash must be 32 bytes" }

    // Bubblegum instruction layouts expect: root(32) + dataHash(32) + creatorHash(32) + nonce(u64) + index(u32)
    // Proof nodes are passed as remaining accounts.
    val w = LEWriter()
      .writeBytes(root)
      .writeBytes(dataHash)
      .writeBytes(creatorHash)
      .writeU64LE(nonce)
      .writeU32LE(index.toLong())
    return w.toByteArray()
  }
}
