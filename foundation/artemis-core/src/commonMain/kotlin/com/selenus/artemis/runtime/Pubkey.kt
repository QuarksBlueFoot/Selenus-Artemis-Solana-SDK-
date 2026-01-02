package com.selenus.artemis.runtime

data class Pubkey(val bytes: ByteArray) {
  init { require(bytes.size == 32) { "Pubkey must be 32 bytes" } }

  constructor(base58: String) : this(
    try {
      Base58.decode(base58)
    } catch (e: Exception) {
      throw IllegalArgumentException("Invalid Base58 string", e)
    }
  )

  /**
   * Verify an Ed25519 signature against a message.
   *
   * Matches sol4k PublicKey.verify(signature, message) for parity.
   *
   * @param signature The 64-byte Ed25519 signature
   * @param message The original message bytes that were signed
   * @return true if the signature is valid for this public key and message
   */
  fun verify(signature: ByteArray, message: ByteArray): Boolean {
    require(signature.size == 64) { "Signature must be 64 bytes" }
    return PlatformEd25519.verify(bytes, signature, message)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Pubkey) return false
    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun toString(): String = Base58.encode(bytes)
  fun toBase58(): String = toString()

  companion object {
    @JvmStatic fun fromBase58(s: String): Pubkey {
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
    @JvmStatic fun createWithSeed(base: Pubkey, seed: String, owner: Pubkey): Pubkey {
      val seedBytes = seed.encodeToByteArray()
      require(seedBytes.size <= 32) { "seed must be <= 32 bytes" }
      val data = base.bytes + seedBytes + owner.bytes
      val hash = Crypto.sha256(data)
      return Pubkey(hash)
    }

    /**
     * Compatibility alias: solana-kt users look for findProgramAddress on Pubkey/PublicKey.
     */
    @JvmStatic fun findProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pda.Result =
        Pda.findProgramAddress(seeds, programId)

    @JvmStatic fun createProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pubkey? =
        Pda.createProgramAddress(seeds, programId)
  }
}
