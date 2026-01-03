package com.selenus.artemis.wallet.mwa.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object HkdfSha256 {
  fun derive(ikm: ByteArray, salt: ByteArray, length: Int): ByteArray {
    val prk = hmac(salt, ikm)
    var t = ByteArray(0)
    val okm = ByteArray(length)
    var offset = 0
    var counter = 1
    while (offset < length) {
      val input = t + byteArrayOf(counter.toByte())
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
