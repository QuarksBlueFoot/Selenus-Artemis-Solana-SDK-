package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import java.security.SecureRandom
import java.time.Instant

/**
 * Session keys for games.
 *
 * Pattern:
 * - player has a real wallet
 * - game uses an ephemeral keypair for rapid signing
 * - the wallet authorizes the session key once (app specific)
 */
class SessionKeys(
  val sessionKeypair: Keypair,
  val createdAt: Long = Instant.now().epochSecond
) {

  companion object {
    fun new(): SessionKeys {
      val rnd = SecureRandom()
      return SessionKeys(Keypair.generate(rnd))
    }
  }

  val pubkey: Pubkey get() = sessionKeypair.publicKey
}
