package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Crypto
import java.security.MessageDigest

/**
 * MerkleDistributor
 *
 * Helper for verifying Merkle proofs, commonly used in gaming airdrops and rewards.
 */
object MerkleDistributor {

  /**
   * Verify a Merkle proof.
   *
   * @param proof List of sibling hashes (32 bytes each)
   * @param root The Merkle root (32 bytes)
   * @param leaf The leaf hash to verify (32 bytes)
   */
  fun verify(proof: List<ByteArray>, root: ByteArray, leaf: ByteArray): Boolean {
    var computedHash = leaf
    for (proofElement in proof) {
      computedHash = if (compare(computedHash, proofElement) <= 0) {
        hash(computedHash + proofElement)
      } else {
        hash(proofElement + computedHash)
      }
    }
    return computedHash.contentEquals(root)
  }

  private fun hash(data: ByteArray): ByteArray {
    // Solana Merkle trees typically use Keccak-256 or SHA-256.
    // Standard SPL Merkle Distributor uses Keccak-256.
    // SHA-256 is used here for generic usage since it is the hash provided by `Crypto` in the
    // runtime; if Keccak support is needed, add it to Crypto.
    return Crypto.sha256(data)
  }

  private fun compare(a: ByteArray, b: ByteArray): Int {
    for (i in a.indices) {
      val av = a[i].toInt() and 0xFF
      val bv = b[i].toInt() and 0xFF
      if (av != bv) return av - bv
    }
    return 0
  }
}
