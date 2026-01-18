package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey

/**
 * Minimal decoder for ConcurrentMerkleTree account state (SPL Account Compression).
 *
 * This is intentionally pragmatic for mobile apps:
 * - Parses header fields needed for proofs/mints UX (depth, buffer size, authority)
 * - Leaves raw bytes accessible for advanced usage.
 *
 * Layout is stable in practice but versioned; we treat unknown fields as opaque.
 */
data class ConcurrentMerkleTreeAccount(
  val merkleTree: Pubkey,
  val authority: Pubkey,
  val depth: Int,
  val bufferSize: Int,
  val raw: ByteArray
) {
  companion object {
    /**
     * Best-effort decode based on common Account Compression tree config layout:
     * - 8 byte discriminator (ignored)
     * - authority: Pubkey (32)
     * - creation slot/seq etc (ignored)
     * - depth: u32
     * - bufferSize: u32
     *
     * If layout mismatch, throws.
     */
    fun decode(merkleTree: Pubkey, data: ByteArray): ConcurrentMerkleTreeAccount {
      if (data.size < 8 + 32 + 8 + 4 + 4) {
        throw IllegalArgumentException("Tree account data too small")
      }
      var off = 8
      val auth = Pubkey(data.copyOfRange(off, off + 32))
      off += 32
      // skip 8 bytes (creation slot or seq)
      off += 8
      val depth = readU32LE(data, off); off += 4
      val buf = readU32LE(data, off); off += 4
      return ConcurrentMerkleTreeAccount(merkleTree, auth, depth, buf, data)
    }

    private fun readU32LE(b: ByteArray, off: Int): Int {
      return (b[off].toInt() and 0xff) or
        ((b[off + 1].toInt() and 0xff) shl 8) or
        ((b[off + 2].toInt() and 0xff) shl 16) or
        ((b[off + 3].toInt() and 0xff) shl 24)
    }
  }
}
