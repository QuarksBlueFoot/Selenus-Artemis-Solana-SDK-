/*
 * Drop-in source compatibility with org.sol4k.ProgramDerivedAddress.
 *
 * Upstream sol4k declares this as `data class ProgramDerivedAddress(
 * val address: PublicKey, val nonce: Int)`. Both the field name
 * `address` and `nonce` matter: apps destructure (`val (a, n) = pda`)
 * and read `pda.address.toBase58()` directly. The shim mirrors the
 * upstream field name.
 */
package org.sol4k

data class ProgramDerivedAddress(
    val address: PublicKey,
    val nonce: Int
) {
    /**
     * Backwards-compat alias for the field that previously lived
     * under the name `publicKey` on this shim. Prefer [address] in
     * new code.
     */
    @Deprecated("Use address.", ReplaceWith("address"))
    val publicKey: PublicKey get() = address

    companion object {
        /**
         * Convenience helper used by some upstream-shaped tests:
         * construct a [ProgramDerivedAddress] with the publicKey-named
         * argument list. Defers to the upstream-shaped data-class
         * constructor.
         */
        @JvmStatic
        @JvmName("ofPublicKey")
        fun of(publicKey: PublicKey, nonce: Int): ProgramDerivedAddress =
            ProgramDerivedAddress(address = publicKey, nonce = nonce)
    }
}
