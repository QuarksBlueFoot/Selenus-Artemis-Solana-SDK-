/*
 * Drop-in source compatibility with foundation.metaplex.solanapublickeys.PublicKey.
 *
 * solana-kmp splits its public key layer into its own artifact. This shim
 * re-publishes the upstream package with Artemis internals: every conversion
 * and PDA call delegates to com.selenus.artemis.runtime.Pubkey.
 */
package foundation.metaplex.solanapublickeys

import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey
import com.selenus.artemis.runtime.Pda as ArtemisPda

/**
 * solana-kmp compatible `PublicKey`. Provides the same constructors and
 * top-level companion helpers as foundation.metaplex:solanapublickeys 0.2.10.
 */
class PublicKey internal constructor(internal val inner: ArtemisPubkey) {

    /** Construct from the 32-byte compressed form. */
    constructor(bytes: ByteArray) : this(ArtemisPubkey(bytes))

    /** Construct from a base58 encoded string. */
    constructor(base58: String) : this(ArtemisPubkey(base58))

    /** The raw 32-byte form. */
    fun toByteArray(): ByteArray = inner.bytes

    /** Base58 representation; matches `toString` for parity with upstream. */
    fun toBase58(): String = inner.toBase58()

    /** The string form used by `toString`. */
    override fun toString(): String = inner.toBase58()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return inner == other.inner
    }

    override fun hashCode(): Int = inner.hashCode()

    companion object {
        /** Derive a PDA from [seeds] under [programId]. */
        @JvmStatic
        fun findProgramAddress(seeds: List<ByteArray>, programId: PublicKey): ProgramDerivedAddress {
            val result = ArtemisPda.findProgramAddress(seeds, programId.inner)
            return ProgramDerivedAddress(PublicKey(result.address.bytes), result.bump.toInt())
        }

        /** Create a PDA directly for a specific seed set (no bump search). */
        @JvmStatic
        fun createProgramAddress(seeds: List<ByteArray>, programId: PublicKey): PublicKey? {
            val artemis = ArtemisPda.createProgramAddress(seeds, programId.inner) ?: return null
            return PublicKey(artemis.bytes)
        }
    }
}

/** solana-kmp compatible `ProgramDerivedAddress`. */
data class ProgramDerivedAddress(
    val publicKey: PublicKey,
    val nonce: Int
)
