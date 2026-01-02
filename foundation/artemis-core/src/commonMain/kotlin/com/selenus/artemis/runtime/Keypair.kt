package com.selenus.artemis.runtime

class Keypair private constructor(
  private val seed: ByteArray,
  private val pub: ByteArray
) : Signer {

  override val publicKey: Pubkey = Pubkey(pub)

  override fun sign(message: ByteArray): ByteArray =
    PlatformEd25519.sign(seed, message)

  fun secretKeyBytes(): ByteArray = seed.copyOf()

  companion object {
    @JvmStatic fun generate(): Keypair {
      val seed = PlatformCrypto.secureRandomBytes(32)
      val pub = PlatformEd25519.publicKeyFromSeed(seed)
      return Keypair(seed, pub)
    }

    @JvmStatic fun fromSeed(seed32: ByteArray): Keypair {
      require(seed32.size == 32) { "Seed must be 32 bytes" }
      val pub = PlatformEd25519.publicKeyFromSeed(seed32)
      return Keypair(seed32.copyOf(), pub)
    }

    /**
     * Creates a Keypair from a 64-byte secret key (legacy format).
     * This matches solana-kt's Account.fromSecretKey().
     */
    @JvmStatic fun fromSecretKey(secretKey: ByteArray): Keypair {
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
    @JvmStatic @JvmOverloads fun fromMnemonic(mnemonic: String, passphrase: String = ""): Keypair {
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
    @JvmStatic @JvmOverloads fun fromMnemonicWithPath(
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
    @JvmStatic @JvmOverloads fun fromMnemonicSolana(
        mnemonic: String,
        account: Int = 0,
        passphrase: String = ""
    ): Keypair {
        val seed = Bip39.toSeed(mnemonic, passphrase)
        return Bip32.deriveSolanaKeypair(seed, account)
    }
  }
}
