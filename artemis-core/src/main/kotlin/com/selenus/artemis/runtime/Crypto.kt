package com.selenus.artemis.runtime

import java.security.MessageDigest

object Crypto {
  fun sha256(vararg parts: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    for (p in parts) md.update(p)
    return md.digest()
  }
}
