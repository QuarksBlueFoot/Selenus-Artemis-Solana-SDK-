package com.selenus.artemis.cnft

/**
 * Anchor discriminators are the first 8 bytes of sha256("global:<method>").
 *
 * This removes the need to hardcode byte arrays for each instruction discriminator.
 */
object AnchorDiscriminator {
  fun global(method: String): ByteArray {
    val preimage = "global:$method".encodeToByteArray()
    val hash = Hashing.sha256(preimage)
    return hash.copyOfRange(0, 8)
  }
}
