/*
 * Drop-in source compatibility with org.sol4k.Keypair.
 *
 * All signing, seed derivation, and key generation is delegated to the Artemis
 * core `Keypair` implementation so sol4k users automatically benefit from the
 * Artemis hardened Ed25519 path and the BIP-39 / SLIP-0010 support Artemis ships.
 */
package org.sol4k

import com.selenus.artemis.runtime.Keypair as ArtemisKeypair

/**
 * sol4k compatible `Keypair` wrapper.
 *
 * Exposes the sol4k 0.7.0 shape: an immutable pair of secret and public key
 * backed by an Artemis [ArtemisKeypair]. Use [asArtemis] to escape into the
 * Artemis typed API when you need things sol4k does not offer.
 */
class Keypair private constructor(
    private val artemis: ArtemisKeypair
) {

    /** The 32-byte Ed25519 seed (sol4k calls this "secret"). */
    val secret: ByteArray
        get() = artemis.secretKeyBytes()

    /** The matching [PublicKey]. */
    val publicKey: PublicKey
        get() = PublicKey(artemis.publicKey.bytes)

    /** Sign [message] with the Ed25519 secret. Returns a 64-byte signature. */
    fun sign(message: ByteArray): ByteArray = artemis.sign(message)

    /** Escape hatch for callers that want the native Artemis keypair. */
    fun asArtemis(): ArtemisKeypair = artemis

    companion object {
        /** Generate a cryptographically random keypair. */
        @JvmStatic
        fun generate(): Keypair = Keypair(ArtemisKeypair.generate())

        /**
         * Construct a keypair from the 64-byte legacy secret key format
         * (seed || public key). Matches `sol4k.Keypair.fromSecretKey`.
         */
        @JvmStatic
        fun fromSecretKey(secretKey: ByteArray): Keypair =
            Keypair(ArtemisKeypair.fromSecretKey(secretKey))

        /** Construct a keypair from the 32-byte Ed25519 seed. */
        @JvmStatic
        fun fromSeed(seed: ByteArray): Keypair =
            Keypair(ArtemisKeypair.fromSeed(seed))
    }
}
