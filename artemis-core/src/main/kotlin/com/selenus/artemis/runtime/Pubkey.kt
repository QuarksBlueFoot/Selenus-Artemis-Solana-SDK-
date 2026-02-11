package com.selenus.artemis.runtime

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

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
    return try {
      val pubKeyParams = Ed25519PublicKeyParameters(bytes, 0)
      val signer = Ed25519Signer()
      signer.init(false, pubKeyParams)
      signer.update(message, 0, message.size)
      signer.verifySignature(signature)
    } catch (e: Exception) {
      false
    }
  }

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

    /**
     * Compatibility alias: solana-kt users look for findProgramAddress on Pubkey/PublicKey.
     */
    fun findProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pda.Result =
        Pda.findProgramAddress(seeds, programId)

    /**
     * Compatibility alias: solana-kt users look for createProgramAddress on Pubkey/PublicKey.
     * Throws exception if derivation fails, matching solana-kt legacy behavior.
     */
    fun createProgramAddress(seeds: List<ByteArray>, programId: Pubkey): Pubkey =
        Pda.createProgramAddress(seeds, programId)
            ?: throw IllegalArgumentException("Invalid seeds, address must fall off the curve")
  }
}
