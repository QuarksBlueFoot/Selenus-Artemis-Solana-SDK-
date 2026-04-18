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
    // Declaration order matches upstream sol4k 0.7.0 so code that reads
    // `Commitment.values()[0]` picks FINALIZED (the safest default).
    FINALIZED("finalized"),
    CONFIRMED("confirmed"),
    PROCESSED("processed");

    override fun toString(): String = value
}
