package com.selenus.artemis.runtime

object Crypto {
  fun sha256(vararg parts: ByteArray): ByteArray = PlatformCrypto.sha256(*parts)
}
