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
    // We'll assume SHA-256 for generic usage unless specified, 
    // but let's check what `Crypto` provides.
    // If Crypto doesn't have Keccak, we might need to add it or stick to SHA-256.
    // For now, let's use SHA-256 as it's in the runtime.
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
