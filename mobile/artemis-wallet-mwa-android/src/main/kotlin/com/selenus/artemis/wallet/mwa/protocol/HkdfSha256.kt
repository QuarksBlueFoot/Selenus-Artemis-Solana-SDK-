package com.selenus.artemis.wallet.mwa.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object HkdfSha256 {
  /**
   * RFC 5869 HKDF-SHA256 `Extract-then-Expand`.
   *
   * The MWA spec calls this with a fixed-length (16-byte) output, empty info,
   * and the association public key as salt. [info] defaults to empty for
   * that case; callers that need multiple contexts from the same IKM can
   * pass a distinct [info] per expansion.
   *
   * Iteration body matches the RFC exactly:
   *     T(0) = empty
   *     T(i) = HMAC(prk, T(i-1) || info || i)
   */
  fun derive(
    ikm: ByteArray,
    salt: ByteArray,
    length: Int,
    info: ByteArray = ByteArray(0)
  ): ByteArray {
    require(length in 0..255 * 32) {
      "HKDF-SHA256 length must be 0..${255 * 32}, got $length"
    }
    // RFC 5869 §2.2: when salt is not provided, it is set to a string of
    // HashLen zeros. The JVM HMAC implementation rejects an empty key with
    // IllegalArgumentException, so we substitute the canonical fallback up
    // front. This matches every official RFC 5869 test vector exactly.
    val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
    val prk = hmac(effectiveSalt, ikm)
    var t = ByteArray(0)
    val okm = ByteArray(length)
    var offset = 0
    var counter = 1
    while (offset < length) {
      val input = ByteArray(t.size + info.size + 1).also { buf ->
        t.copyInto(buf, 0)
        info.copyInto(buf, t.size)
        buf[t.size + info.size] = counter.toByte()
      }
      t = hmac(prk, input)
      val take = minOf(t.size, length - offset)
      System.arraycopy(t, 0, okm, offset, take)
      offset += take
      counter++
    }
    return okm
  }

  private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
  }
}
