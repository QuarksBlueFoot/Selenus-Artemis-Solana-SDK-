/*
 * Drop-in source compatibility with foundation.metaplex.base58.Base58.
 *
 * Forwards to the Artemis core Base58 implementation so upstream callers that
 * imported from this package re-compile unchanged and inherit the Artemis
 * hardened encoder.
 */
package foundation.metaplex.base58

import com.selenus.artemis.runtime.Base58 as ArtemisBase58

/** solana-kmp compatible `Base58` facade. */
object Base58 {
    /** Encode [bytes] as base58. */
    @JvmStatic
    fun encode(bytes: ByteArray): String = ArtemisBase58.encode(bytes)

    /** Decode [string] as base58 to raw bytes. */
    @JvmStatic
    fun decode(string: String): ByteArray = ArtemisBase58.decode(string)
}
