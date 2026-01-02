package com.selenus.artemis.disc

import com.selenus.artemis.runtime.Crypto

/**
 * Anchor discriminator helper.
 *
 * Discriminator is the first 8 bytes of sha256("global:<method>").
 */
object AnchorDiscriminators {
  fun global(method: String): ByteArray {
    val pre = "global:$method".encodeToByteArray()
    val hash = Crypto.sha256(pre)
    return hash.copyOfRange(0, 8)
  }

  /** Alias for [global] — instruction discriminator is sha256("global:<name>")[0..8]. */
  fun instruction(name: String): ByteArray = global(name)

  /** Account discriminator: first 8 bytes of sha256("account:<TypeName>"). */
  fun account(typeName: String): ByteArray {
    val pre = "account:$typeName".encodeToByteArray()
    val hash = Crypto.sha256(pre)
    return hash.copyOfRange(0, 8)
  }
}
