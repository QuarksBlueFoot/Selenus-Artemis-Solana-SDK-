package com.selenus.artemis.cnft

import java.security.MessageDigest

internal object Hashing {
  fun sha256(data: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(data)
  }
}
