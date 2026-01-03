package com.selenus.artemis.wallet.mwa.protocol

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * P-256 helpers for MWA 2.x session establishment.
 *
 * Spec details:
 * - Ephemeral ECDH keypair is P-256.
 * - HELLO_REQ uses an ECDSA-SHA256 signature over the X9.62 public key bytes.
 * - Signatures are P1363 encoded (r||s), 32 bytes each.
 */
internal object EcP256 {
  private val curve = ECGenParameterSpec("secp256r1")

  fun generateKeypair(): KeyPair {
    val gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(curve)
    return gen.generateKeyPair()
  }

  /**
   * X9.62 uncompressed form: 0x04 || X(32) || Y(32)
   */
  fun x962Uncompressed(pub: PublicKey): ByteArray {
    val p = pub as ECPublicKey
    val x = p.w.affineX.toByteArray().pad32()
    val y = p.w.affineY.toByteArray().pad32()
    return byteArrayOf(0x04) + x + y
  }

  fun publicKeyFromX962(x962: ByteArray): PublicKey {
    // Reconstruct as X509 SubjectPublicKeyInfo for P-256.
    // We build an ASN.1 wrapper using the JCA encoded format by round-tripping via KeyFactory.
    // To keep deps minimal, we rely on the platform provider's ability to parse a synthetic SPKI.
    // If this ever fails on a specific device, we can add a tiny ASN.1 writer.
    val spki = SpkiP256.wrapUncompressedPoint(x962)
    val kf = KeyFactory.getInstance("EC")
    return kf.generatePublic(X509EncodedKeySpec(spki))
  }

  fun ecdhSecret(privateKey: PrivateKey, otherPublic: PublicKey): ByteArray {
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(privateKey)
    ka.doPhase(otherPublic, true)
    return ka.generateSecret()
  }

  fun signP1363(privateKey: PrivateKey, message: ByteArray): ByteArray {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initSign(privateKey)
    sig.update(message)
    val der = sig.sign()
    return EcdsaDer.toP1363(der)
  }

  fun verifyP1363(publicKey: PublicKey, message: ByteArray, p1363Sig: ByteArray): Boolean {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initVerify(publicKey)
    sig.update(message)
    val der = EcdsaDer.fromP1363(p1363Sig)
    return sig.verify(der)
  }
}

private fun ByteArray.pad32(): ByteArray {
  // BigInteger.toByteArray is signed; may produce 33 bytes with leading 0x00.
  val src = this
  val out = ByteArray(32)
  val trimmed = if (src.size > 32) src.copyOfRange(src.size - 32, src.size) else src
  val start = 32 - trimmed.size
  System.arraycopy(trimmed, 0, out, start, trimmed.size)
  return out
}

/** Minimal DER<->P1363 conversion for ECDSA signatures. */
private object EcdsaDer {
  fun toP1363(der: ByteArray): ByteArray {
    val (r, s) = parseDer(der)
    return r.pad32() + s.pad32()
  }

  fun fromP1363(p: ByteArray): ByteArray {
    require(p.size == 64) { "P1363 signature must be 64 bytes" }
    val r = p.copyOfRange(0, 32).trimUnsigned()
    val s = p.copyOfRange(32, 64).trimUnsigned()
    return encodeDer(r, s)
  }

  private fun ByteArray.trimUnsigned(): ByteArray {
    var i = 0
    while (i < size - 1 && this[i] == 0.toByte()) i++
    val raw = copyOfRange(i, size)
    // If high bit is set, prefix 0x00 to keep INTEGER positive.
    return if ((raw[0].toInt() and 0x80) != 0) byteArrayOf(0) + raw else raw
  }

  private fun parseDer(der: ByteArray): Pair<ByteArray, ByteArray> {
    // Very small DER parser for ECDSA-Sig-Value:
    // SEQUENCE { INTEGER r, INTEGER s }
    var idx = 0
    fun readByte(): Int = der[idx++].toInt() and 0xFF
    fun readLen(): Int {
      val b = readByte()
      if (b < 0x80) return b
      val n = b and 0x7F
      var len = 0
      repeat(n) { len = (len shl 8) or readByte() }
      return len
    }
    require(readByte() == 0x30) { "Not a DER SEQUENCE" }
    readLen() // sequence length
    require(readByte() == 0x02) { "Expected INTEGER r" }
    val rLen = readLen()
    val r = der.copyOfRange(idx, idx + rLen)
    idx += rLen
    require(readByte() == 0x02) { "Expected INTEGER s" }
    val sLen = readLen()
    val s = der.copyOfRange(idx, idx + sLen)
    return r to s
  }

  private fun encodeDer(r: ByteArray, s: ByteArray): ByteArray {
    fun lenBytes(n: Int): ByteArray {
      return if (n < 0x80) byteArrayOf(n.toByte()) else {
        val tmp = mutableListOf<Byte>()
        var v = n
        while (v > 0) {
          tmp.add(0, (v and 0xFF).toByte())
          v = v ushr 8
        }
        byteArrayOf((0x80 or tmp.size).toByte()) + tmp.toByteArray()
      }
    }
    fun integer(x: ByteArray): ByteArray = byteArrayOf(0x02) + lenBytes(x.size) + x
    val rInt = integer(r)
    val sInt = integer(s)
    val seqLen = rInt.size + sInt.size
    return byteArrayOf(0x30) + lenBytes(seqLen) + rInt + sInt
  }
}

/**
 * Tiny SPKI wrapper for an uncompressed P-256 public key point.
 * This produces an X509 SubjectPublicKeyInfo with:
 * - algorithm: id-ecPublicKey + secp256r1
 * - subjectPublicKey: BIT STRING of the uncompressed point
 */
private object SpkiP256 {
  // Prebuilt DER prefix for P-256 SPKI up to the BIT STRING header.
  // This avoids pulling an ASN.1 library.
  //
  // SEQUENCE(
  //   SEQUENCE( OID id-ecPublicKey, OID secp256r1 ),
  //   BIT STRING( 0x00 || <65-byte uncompressed point> )
  // )
  private val prefix = byteArrayOf(
    0x30, 0x59, // SEQUENCE (89)
    0x30, 0x13, // SEQUENCE (19)
    0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01, // OID 1.2.840.10045.2.1
    0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, // OID 1.2.840.10045.3.1.7
    0x03, 0x42, 0x00 // BIT STRING (66), unused bits = 0
  )

  fun wrapUncompressedPoint(x962Uncompressed: ByteArray): ByteArray {
    require(x962Uncompressed.size == 65 && x962Uncompressed[0] == 0x04.toByte()) {
      "Expected 65-byte uncompressed point"
    }
    return prefix + x962Uncompressed
  }
}
