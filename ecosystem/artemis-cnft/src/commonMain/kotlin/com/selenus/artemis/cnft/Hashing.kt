package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Crypto

internal object Hashing {
  fun sha256(data: ByteArray): ByteArray = Crypto.sha256(data)
}
