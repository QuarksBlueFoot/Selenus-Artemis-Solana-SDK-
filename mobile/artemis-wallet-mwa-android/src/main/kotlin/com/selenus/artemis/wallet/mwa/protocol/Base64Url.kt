package com.selenus.artemis.wallet.mwa.protocol

import java.util.Base64

internal object Base64Url {
  private val enc = Base64.getUrlEncoder().withoutPadding()
  private val dec = Base64.getUrlDecoder()

  fun encode(bytes: ByteArray): String = enc.encodeToString(bytes)
  fun decode(s: String): ByteArray = dec.decode(s)
}
