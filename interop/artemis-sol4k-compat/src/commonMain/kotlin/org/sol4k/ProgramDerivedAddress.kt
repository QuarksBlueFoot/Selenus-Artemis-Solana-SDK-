/*
 * Drop-in source compatibility with org.sol4k.ProgramDerivedAddress.
 *
 * Upstream declares this as a two-field data class `(publicKey, nonce)`. Apps
 * commonly destructure the result or read `pda.publicKey`; the field name
 * must match upstream exactly.
 */
package org.sol4k

data class ProgramDerivedAddress(
    val publicKey: PublicKey,
    val nonce: Int
)
