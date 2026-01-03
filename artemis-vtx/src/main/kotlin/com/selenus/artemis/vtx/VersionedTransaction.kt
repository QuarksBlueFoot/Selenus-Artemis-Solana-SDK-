package com.selenus.artemis.vtx

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
}
