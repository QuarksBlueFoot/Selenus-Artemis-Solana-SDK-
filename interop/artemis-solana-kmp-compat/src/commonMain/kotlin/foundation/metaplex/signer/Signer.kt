/*
 * Drop-in source compatibility with foundation.metaplex.signer.
 *
 * solana-kmp exposes a small `Signer` interface plus a `HotSigner` wrapper for
 * keypairs. This shim mirrors the shape with Artemis native types.
 */
package foundation.metaplex.signer

import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * solana-kmp compatible `Signer` interface.
 */
interface Signer {
    val publicKey: PublicKey
    suspend fun sign(message: ByteArray): ByteArray
}

/**
 * In-memory signer backed by a [Keypair]. Matches upstream `HotSigner`.
 */
class HotSigner(private val keypair: Keypair) : Signer {
    override val publicKey: PublicKey = keypair.publicKey
    override suspend fun sign(message: ByteArray): ByteArray = keypair.sign(message)
}
