/*
 * Drop-in source compatibility with org.sol4k.Base58.
 *
 * sol4k exposes Base58 as a static object. Artemis already has a hardened
 * Base58 impl under `com.selenus.artemis.runtime.Base58`; this shim forwards.
 */
package org.sol4k

import com.selenus.artemis.runtime.Base58 as ArtemisBase58

/** sol4k compatible `Base58` facade. Forwards to the Artemis implementation. */
object Base58 {
    /** Encode [bytes] as a base58 string. */
    @JvmStatic
    fun encode(bytes: ByteArray): String = ArtemisBase58.encode(bytes)

    /** Decode [string] as base58 and return the raw bytes. */
    @JvmStatic
    fun decode(string: String): ByteArray = ArtemisBase58.decode(string)
}
