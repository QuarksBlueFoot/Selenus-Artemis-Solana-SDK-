package com.selenus.artemis.runtime

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

class Keypair private constructor(
  private val priv: Ed25519PrivateKeyParameters,
  private val pub: Ed25519PublicKeyParameters
) : Signer {

  override val publicKey: Pubkey = Pubkey(pub.encoded)

  override fun sign(message: ByteArray): ByteArray {
    val signer = Ed25519Signer()
    signer.init(true, priv)
    signer.update(message, 0, message.size)
    return signer.generateSignature()
  }

  fun secretKeyBytes(): ByteArray = priv.encoded

  companion object {
    fun generate(random: SecureRandom = SecureRandom()): Keypair {
      val seed = ByteArray(32)
      random.nextBytes(seed)
      val priv = Ed25519PrivateKeyParameters(seed, 0)
      val pub = priv.generatePublicKey()
      return Keypair(priv, pub)
    }

    fun fromSeed(seed32: ByteArray): Keypair {
      require(seed32.size == 32) { "Seed must be 32 bytes" }
      val priv = Ed25519PrivateKeyParameters(seed32, 0)
      val pub = priv.generatePublicKey()
      return Keypair(priv, pub)
    }
  }
}
