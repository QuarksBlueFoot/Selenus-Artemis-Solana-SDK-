package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.ShortVec
import java.io.ByteArrayOutputStream

/**
 * Versioned transaction container: signatures + versioned message bytes.
 */
data class VersionedTransaction(
  val message: MessageV0,
  val signatures: MutableList<ByteArray> = mutableListOf()
) {

  fun sign(signers: List<Signer>) {
    val msgBytes = message.serialize()
    signatures.clear()
    for (signer in signers) {
      signatures.add(signer.sign(msgBytes))
    }
  }

  /**
   * Add an externally-produced signature for a specific public key.
   *
   * Matches sol4k VersionedTransaction.addSignature() for parity.
   *
   * @param publicKey The signer's public key
   * @param signature The 64-byte Ed25519 signature
   */
  fun addSignature(publicKey: Pubkey, signature: ByteArray) {
    require(signature.size == 64) { "Signature must be 64 bytes" }
    // Find the index of this public key in the message account keys
    val index = message.staticAccountKeys.indexOfFirst { it.bytes.contentEquals(publicKey.bytes) }
    if (index == -1) {
      // Append if not found (external signer scenario)
      signatures.add(signature)
    } else {
      // Ensure signatures list is large enough
      while (signatures.size <= index) signatures.add(ByteArray(64))
      signatures[index] = signature
    }
  }

  fun serialize(): ByteArray {
    require(signatures.isNotEmpty()) { "No signatures present. Call sign() first." }
    val out = ByteArrayOutputStream()
    out.write(ShortVec.encodeLen(signatures.size))
    signatures.forEach { sig ->
      require(sig.size == 64) { "Signature must be 64 bytes" }
      out.write(sig)
    }
    out.write(message.serialize())
    return out.toByteArray()
  }

  fun toBase64(): String = java.util.Base64.getEncoder().encodeToString(serialize())

  companion object {
    /**
     * Deserialize a versioned transaction from wire bytes.
     *
     * Matches sol4k VersionedTransaction.from() for parity.
     */
    fun deserialize(bytes: ByteArray): VersionedTransaction {
      var offset = 0

      // Signatures
      val (numSigs, sigLenBytes) = ShortVec.decodeLen(bytes)
      offset += sigLenBytes
      val signatures = ArrayList<ByteArray>()
      for (i in 0 until numSigs) {
        signatures.add(bytes.copyOfRange(offset, offset + 64))
        offset += 64
      }

      // Message
      val messageBytes = bytes.copyOfRange(offset, bytes.size)
      val message = MessageV0.deserialize(messageBytes)

      return VersionedTransaction(message, signatures)
    }

    /**
     * Alias for deserialize, matching sol4k naming convention.
     */
    fun from(bytes: ByteArray): VersionedTransaction = deserialize(bytes)

    /**
     * Deserialize from a Base64-encoded string.
     */
    fun fromBase64(base64: String): VersionedTransaction {
      val bytes = java.util.Base64.getDecoder().decode(base64)
      return deserialize(bytes)
    }
  }
}
