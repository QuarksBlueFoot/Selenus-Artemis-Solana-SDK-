package com.selenus.artemis.wallet.mwa.protocol

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class Aes128Gcm(private val key16: ByteArray) {
  private val rng = SecureRandom()

  fun encrypt(seq: Int, plaintext: ByteArray): ByteArray {
    val seqBytes = intToBe4(seq)
    val iv = ByteArray(12)
    rng.nextBytes(iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
      Cipher.ENCRYPT_MODE,
      SecretKeySpec(key16, "AES"),
      GCMParameterSpec(128, iv)
    )
    cipher.updateAAD(seqBytes)
    val ctWithTag = cipher.doFinal(plaintext)
    // Spec format: <seq(4)><iv(12)><ciphertext><tag(16)>; JCA returns ciphertext||tag.
    return seqBytes + iv + ctWithTag
  }

  fun decrypt(expectedSeq: Int, packet: ByteArray): ByteArray {
    require(packet.size >= 4 + 12 + 16) { "Packet too short" }
    val seq = be4ToInt(packet, 0)
    require(seq == expectedSeq) { "Bad sequence number. Expected $expectedSeq got $seq" }
    val iv = packet.copyOfRange(4, 16)
    val ctWithTag = packet.copyOfRange(16, packet.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
      Cipher.DECRYPT_MODE,
      SecretKeySpec(key16, "AES"),
      GCMParameterSpec(128, iv)
    )
    cipher.updateAAD(intToBe4(seq))
    return cipher.doFinal(ctWithTag)
  }

  private fun intToBe4(v: Int): ByteArray = byteArrayOf(
    ((v ushr 24) and 0xFF).toByte(),
    ((v ushr 16) and 0xFF).toByte(),
    ((v ushr 8) and 0xFF).toByte(),
    (v and 0xFF).toByte()
  )

  private fun be4ToInt(b: ByteArray, off: Int): Int {
    return ((b[off].toInt() and 0xFF) shl 24) or
      ((b[off + 1].toInt() and 0xFF) shl 16) or
      ((b[off + 2].toInt() and 0xFF) shl 8) or
      (b[off + 3].toInt() and 0xFF)
  }
}
