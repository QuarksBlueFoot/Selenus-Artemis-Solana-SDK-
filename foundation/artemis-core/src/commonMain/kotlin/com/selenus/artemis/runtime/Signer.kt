package com.selenus.artemis.runtime

interface Signer {
  val publicKey: Pubkey
  fun sign(message: ByteArray): ByteArray
}
