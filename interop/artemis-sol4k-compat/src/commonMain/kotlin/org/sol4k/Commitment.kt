/*
 * Drop-in source compatibility for org.sol4k.Commitment.
 */
package org.sol4k

/**
 * sol4k commitment level enum.
 *
 * sol4k 0.7.0 uses a plain enum with the three standard Solana commitment levels.
 * The JSON-RPC parameter values match what Artemis sends on the wire.
 */
enum class Commitment(val value: String) {
    PROCESSED("processed"),
    CONFIRMED("confirmed"),
    FINALIZED("finalized");

    override fun toString(): String = value
}
