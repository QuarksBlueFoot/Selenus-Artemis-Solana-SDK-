package com.selenus.artemis.cnft

/**
 * Merkle proof helpers (SHA-256).
 *
 * This is a utility for verifying proofs returned by an indexer service.
 * On-chain programs may use different hashers; this is intended for client-side validation
 * with common Bubblegum proof providers.
 */
object MerkleProof {

  /**
   * Verify a leaf against a root given a proof and leaf index.
   *
   * @param leafHash 32-byte leaf hash
   * @param root 32-byte root
   * @param proof list of 32-byte sibling hashes
   * @param index leaf index in tree
   */
  fun verifySha256(leafHash: ByteArray, root: ByteArray, proof: List<ByteArray>, index: Int): Boolean {
    require(leafHash.size == 32) { "leafHash must be 32 bytes" }
    require(root.size == 32) { "root must be 32 bytes" }
    proof.forEach { require(it.size == 32) { "proof node must be 32 bytes" } }

    var hash = leafHash
    var idx = index
    for (sib in proof) {
      hash = if ((idx and 1) == 0) {
        Hashing.sha256(hash + sib)
      } else {
        Hashing.sha256(sib + hash)
      }
      idx = idx ushr 1
    }
    return hash.contentEquals(root)
  }
}
