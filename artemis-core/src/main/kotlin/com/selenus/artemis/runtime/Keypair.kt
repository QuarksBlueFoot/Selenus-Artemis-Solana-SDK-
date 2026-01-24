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

    /**
     * Creates a Keypair from a 64-byte secret key (legacy format).
     * This matches solana-kt's Account.fromSecretKey().
     */
    fun fromSecretKey(secretKey: ByteArray): Keypair {
        require(secretKey.size == 64) { "Secret key must be 64 bytes" }
        // The first 32 bytes are the seed/private key
        val seed = secretKey.copyOfRange(0, 32)
        return fromSeed(seed)
    }
    
    /**
     * Creates a Keypair from a BIP-39 mnemonic phrase.
     * Uses the first 32 bytes of the derived seed (direct derivation, no HD path).
     * 
     * @param mnemonic The 12/15/18/21/24 word mnemonic phrase
     * @param passphrase Optional BIP-39 passphrase (default empty)
     * @return The derived Keypair
     */
    fun fromMnemonic(mnemonic: String, passphrase: String = ""): Keypair {
        return Bip39.toKeypair(mnemonic, passphrase)
    }
    
    /**
     * Creates a Keypair from a BIP-39 mnemonic using HD derivation.
     * Uses SLIP-0010 Ed25519 derivation with the specified path.
     * 
     * @param mnemonic The 12/15/18/21/24 word mnemonic phrase
     * @param derivationPath The derivation path (e.g., "m/44'/501'/0'/0'")
     * @param passphrase Optional BIP-39 passphrase (default empty)
     * @return The derived Keypair
     */
    fun fromMnemonicWithPath(
        mnemonic: String,
        derivationPath: String,
        passphrase: String = ""
    ): Keypair {
        return Bip32.fromMnemonic(mnemonic, derivationPath, passphrase)
    }
    
    /**
     * Creates a Keypair from a BIP-39 mnemonic using the standard Solana path.
     * Uses path: m/44'/501'/{account}'/0'
     * 
     * @param mnemonic The mnemonic phrase
     * @param account The account index (default 0)
     * @param passphrase Optional BIP-39 passphrase
     * @return The derived Keypair
     */
    fun fromMnemonicSolana(
        mnemonic: String,
        account: Int = 0,
        passphrase: String = ""
    ): Keypair {
        val seed = Bip39.toSeed(mnemonic, passphrase)
        return Bip32.deriveSolanaKeypair(seed, account)
    }
  }
}
