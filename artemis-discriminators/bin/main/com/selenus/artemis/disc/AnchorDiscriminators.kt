package com.selenus.artemis.disc

import java.security.MessageDigest

/**
 * Anchor discriminator helper.
 *
 * Discriminator is the first 8 bytes of sha256("global:<method>").
 */
object AnchorDiscriminators {
  fun global(method: String): ByteArray {
    val pre = "global:$method".encodeToByteArray()
    val hash = MessageDigest.getInstance("SHA-256").digest(pre)
    return hash.copyOfRange(0, 8)
  }
}
