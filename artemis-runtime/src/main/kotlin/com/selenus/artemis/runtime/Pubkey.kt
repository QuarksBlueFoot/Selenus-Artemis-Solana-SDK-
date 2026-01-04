package com.selenus.artemis.runtime

data class Pubkey(val bytes: ByteArray) {
  init { require(bytes.size == 32) { "Pubkey must be 32 bytes" } }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as Pubkey
    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun toString(): String = Base58.encode(bytes)
  fun toBase58(): String = toString()

  companion object {
    fun fromBase58(s: String): Pubkey {
      val b = Base58.decode(s)
      require(b.size == 32) { "Pubkey must decode to 32 bytes" }
      return Pubkey(b)
    }

    /**
     * Derive a pubkey with `createWithSeed` semantics.
     *
     * Matches Solana's Pubkey::create_with_seed(base, seed, owner).
     *
     * Constraints:
     * - seed must be <= 32 bytes when UTF-8 encoded.
     */
    fun createWithSeed(base: Pubkey, seed: String, owner: Pubkey): Pubkey {
      val seedBytes = seed.encodeToByteArray()
      require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }
      val data = base.bytes + seedBytes + owner.bytes
      val hash = Crypto.sha256(data)
      return Pubkey(hash)
    }
  }
}
