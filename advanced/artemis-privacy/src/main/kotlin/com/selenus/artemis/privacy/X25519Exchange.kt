package com.selenus.artemis.privacy

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.math.BigInteger
import java.security.SecureRandom

/**
 * X25519 Elliptic Curve Diffie-Hellman for key exchange.
 * 
 * Used internally by privacy features for establishing shared secrets
 * without exposing long-term keys.
 * 
 * Mobile-optimized with:
 * - Minimal memory allocation
 * - Constant-time operations via BouncyCastle
 * - Secure random generation
 */
object X25519Exchange {
  
  private val secureRandom = SecureRandom()
  private const val KEY_SIZE = 32
  
  /**
   * X25519 keypair for key exchange.
   */
  data class X25519Keypair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is X25519Keypair) return false
      return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    
    override fun hashCode(): Int {
      var result = publicKey.contentHashCode()
      result = 31 * result + privateKey.contentHashCode()
      return result
    }
    
    /**
     * Securely wipe this keypair from memory.
     */
    fun wipe() {
      SecureCrypto.wipe(privateKey)
      SecureCrypto.wipe(publicKey)
    }
  }
  
  /**
   * Generate a new X25519 keypair.
   */
  fun generateKeypair(): X25519Keypair {
    val generator = X25519KeyPairGenerator()
    generator.init(X25519KeyGenerationParameters(secureRandom))
    val keyPair = generator.generateKeyPair()
    
    val privateKey = (keyPair.private as X25519PrivateKeyParameters).encoded
    val publicKey = (keyPair.public as X25519PublicKeyParameters).encoded
    
    return X25519Keypair(publicKey, privateKey)
  }
  
  /**
   * Compute shared secret using X25519.
   * 
   * @param ourPrivateKey Our X25519 private key (32 bytes)
   * @param theirPublicKey Their X25519 public key (32 bytes)
   * @return 32-byte shared secret
   */
  fun computeSharedSecret(ourPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
    require(ourPrivateKey.size == KEY_SIZE) { "Private key must be 32 bytes" }
    require(theirPublicKey.size == KEY_SIZE) { "Public key must be 32 bytes" }
    
    val agreement = X25519Agreement()
    val privateParams = X25519PrivateKeyParameters(ourPrivateKey, 0)
    val publicParams = X25519PublicKeyParameters(theirPublicKey, 0)
    
    agreement.init(privateParams)
    val secret = ByteArray(agreement.agreementSize)
    agreement.calculateAgreement(publicParams, secret, 0)
    
    return secret
  }
  
  /**
   * Derive encryption key from shared secret using HKDF.
   * 
   * @param sharedSecret The X25519 shared secret
   * @param salt Optional salt (use ephemeral pubkey for forward secrecy)
   * @param info Context info for key derivation
   * @param length Output key length in bytes
   * @return Derived key material
   */
  fun deriveKey(
    sharedSecret: ByteArray,
    salt: ByteArray? = null,
    info: ByteArray = "artemis-privacy".toByteArray(Charsets.UTF_8),
    length: Int = 32
  ): ByteArray {
    val hkdf = HKDFBytesGenerator(SHA256Digest())
    val params = if (salt != null) {
      HKDFParameters(sharedSecret, salt, info)
    } else {
      HKDFParameters(sharedSecret, null, info)
    }
    hkdf.init(params)
    
    val output = ByteArray(length)
    hkdf.generateBytes(output, 0, length)
    return output
  }
  
  /**
   * Perform full key agreement: ECDH + HKDF in one step.
   * 
   * @param ourPrivateKey Our X25519 private key
   * @param theirPublicKey Their X25519 public key  
   * @param ephemeralPubkey Ephemeral public key used as HKDF salt
   * @return Derived encryption key
   */
  fun agreeAndDerive(
    ourPrivateKey: ByteArray,
    theirPublicKey: ByteArray,
    ephemeralPubkey: ByteArray
  ): ByteArray {
    val sharedSecret = computeSharedSecret(ourPrivateKey, theirPublicKey)
    val key = deriveKey(sharedSecret, ephemeralPubkey)
    SecureCrypto.wipe(sharedSecret)
    return key
  }
  
  /**
   * Convert an Ed25519 public key to an X25519 public key.
   *
   * Ed25519 is defined over the twisted Edwards curve and X25519 is defined
   * over the birationally equivalent Montgomery curve Curve25519. The map
   * from Edwards y-coordinate to Montgomery u-coordinate is:
   *
   * ```
   * u = (1 + y) / (1 - y)  mod p,   p = 2^255 - 19
   * ```
   *
   * An Ed25519 public key encodes a point as the 32-byte little-endian y-coordinate
   * with the high bit of the last byte holding the sign of x. The sign bit is
   * discarded here because the Montgomery u-coordinate is independent of x's sign.
   *
   * This is the standard conversion used by Signal's X3DH and libsodium's
   * `crypto_sign_ed25519_pk_to_curve25519`, implemented directly in Kotlin against
   * a BigInteger field so it is verifiable and portable across any JVM target.
   *
   * The conversion is one-way: Ed25519 to X25519. Receiving encrypted messages to
   * an Ed25519 identity key works by running this on the recipient's Ed25519 public
   * key and then an X25519 ECDH handshake.
   */
  fun ed25519PublicKeyToX25519(ed25519PublicKey: ByteArray): ByteArray {
    require(ed25519PublicKey.size == 32) { "Ed25519 public key must be 32 bytes" }
    return edwardsYToMontgomeryU(ed25519PublicKey)
  }

  /**
   * Convert an Ed25519 private key seed (32 bytes) to an X25519 private key.
   *
   * The Ed25519 private scalar is derived by hashing the seed with SHA-512 and
   * taking the first 32 bytes, then clamping per RFC 8032:
   *
   * - clear the three least significant bits of the first byte
   * - clear the highest bit of the last byte
   * - set the second highest bit of the last byte
   *
   * The clamped scalar is already a valid X25519 private key, so it can be used
   * directly with [computeSharedSecret].
   */
  fun ed25519PrivateKeyToX25519(ed25519Seed: ByteArray): ByteArray {
    require(ed25519Seed.size == 32) { "Ed25519 seed must be 32 bytes" }
    val md = java.security.MessageDigest.getInstance("SHA-512")
    val hash = md.digest(ed25519Seed)
    val scalar = hash.copyOfRange(0, 32)
    // Clamp per RFC 8032 section 5.1.5.
    scalar[0] = (scalar[0].toInt() and 0xF8).toByte()
    scalar[31] = (scalar[31].toInt() and 0x7F).toByte()
    scalar[31] = (scalar[31].toInt() or 0x40).toByte()
    return scalar
  }

  // ─── Internal field arithmetic for the Edwards -> Montgomery birational map ──

  /** The Curve25519 prime field modulus: 2^255 - 19. */
  private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))

  /**
   * Decode a compressed Ed25519 public key (32 bytes little-endian, high bit of last
   * byte holds the sign of x) into the Montgomery u-coordinate serialization used by
   * X25519. Never returns a zero-filled result unless the input actually encodes the
   * additive identity (which is not a valid Ed25519 public key in practice).
   */
  private fun edwardsYToMontgomeryU(compressed: ByteArray): ByteArray {
    // Strip the sign-of-x bit from the last byte to recover y.
    val yBytes = compressed.copyOf(32)
    yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()

    val y = decodeLittleEndian(yBytes)
    val one = BigInteger.ONE
    // numerator = (1 + y) mod p
    val numerator = one.add(y).mod(P)
    // denominator = (1 - y) mod p. Reject y == 1 which would be a point at infinity.
    val denominator = one.subtract(y).mod(P)
    require(denominator.signum() != 0) {
      "Invalid Ed25519 public key: y == 1 has no Montgomery u"
    }
    val u = numerator.multiply(denominator.modInverse(P)).mod(P)
    return encodeLittleEndian(u, 32)
  }

  private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
    // BigInteger is big-endian, so reverse to get the numerical value.
    val be = bytes.reversedArray()
    return BigInteger(1, be)
  }

  private fun encodeLittleEndian(value: BigInteger, length: Int): ByteArray {
    val be = value.toByteArray()
    // Drop an optional leading zero sign byte.
    val src = if (be.size > length && be[0] == 0.toByte()) be.copyOfRange(1, be.size) else be
    val out = ByteArray(length)
    // Copy big-endian bytes into the little-endian output, right-aligned.
    for (i in src.indices) {
      out[i] = src[src.size - 1 - i]
    }
    return out
  }
}
