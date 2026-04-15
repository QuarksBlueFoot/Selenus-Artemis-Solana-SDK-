/*
 * Drop-in source compatibility with foundation.metaplex.solanaeddsa.
 *
 * solana-kmp bundles its Ed25519 primitives in this module. This shim exposes
 * the same function names and delegates to the Artemis platform Ed25519 seam.
 */
package foundation.metaplex.solanaeddsa

import com.selenus.artemis.runtime.Keypair as ArtemisKeypair
import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * solana-kmp compatible `Keypair` wrapper.
 *
 * The upstream class exposes `secretKey`, `publicKey`, and a constructor that
 * takes a 32-byte seed. Artemis keypairs hold both the seed and the derived
 * public key internally so both shapes are available in O(1).
 */
class Keypair internal constructor(private val artemis: ArtemisKeypair) {

    /** Construct from a 32-byte seed. */
    constructor(seed: ByteArray) : this(ArtemisKeypair.fromSeed(seed))

    /** The 32-byte seed. */
    val secretKey: ByteArray get() = artemis.secretKeyBytes()

    /** The derived public key. */
    val publicKey: PublicKey get() = PublicKey(artemis.publicKey.bytes)

    /** Sign [message] with Ed25519 and return the 64-byte signature. */
    fun sign(message: ByteArray): ByteArray = artemis.sign(message)

    companion object {
        /** Generate a random keypair. */
        @JvmStatic
        fun generate(): Keypair = Keypair(ArtemisKeypair.generate())
    }
}

/**
 * solana-kmp compatible `SolanaEddsa` facade.
 *
 * Upstream exposes standalone `sign` and `verify` helpers. The implementation
 * forwards through the public Artemis Keypair + Pubkey surface, which is itself
 * backed by the internal platform Ed25519 seam.
 */
object SolanaEddsa {
    /** Sign [message] with the 32-byte Ed25519 [seed]. */
    @JvmStatic
    fun sign(seed: ByteArray, message: ByteArray): ByteArray =
        ArtemisKeypair.fromSeed(seed).sign(message)

    /** Verify that [signature] is a valid Ed25519 signature for [message] under [publicKey]. */
    @JvmStatic
    fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean =
        ArtemisPubkey(publicKey).verify(signature, message)

    /** Derive an Ed25519 public key from a 32-byte seed. */
    @JvmStatic
    fun publicKeyFromSeed(seed: ByteArray): ByteArray =
        ArtemisKeypair.fromSeed(seed).publicKey.bytes
}
