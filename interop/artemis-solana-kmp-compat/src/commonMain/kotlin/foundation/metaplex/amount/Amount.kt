/*
 * Drop-in source compatibility with foundation.metaplex.amount.
 *
 * solana-kmp ships a tiny amount module with `Amount`, `Lamports`, and `SOL`.
 * This shim re-publishes the same types with Artemis-native arithmetic and
 * adds a few convenience accessors upstream was missing.
 */
package foundation.metaplex.amount

/**
 * A Solana denominated amount.
 *
 * Matches the upstream solana-kmp shape: a long-backed value with an identity
 * (the token symbol) and a decimals count. The arithmetic operators are
 * intentionally sparse; upstream does not provide them either, so callers can
 * safely rely on this for source parity.
 */
data class Amount(
    val basisPoints: Long,
    val identifier: String,
    val decimals: Int
) {
    /** The decimal representation (e.g. 1.5 SOL for 1_500_000_000 lamports). */
    fun toDecimal(): Double {
        var divisor = 1.0
        repeat(decimals) { divisor *= 10.0 }
        return basisPoints / divisor
    }

    /** Human-readable form: "123.456789 SOL". */
    override fun toString(): String = "${toDecimal()} $identifier"
}

/**
 * Create a `Lamports` amount. Matches the upstream top-level helper.
 */
fun Lamports(value: Long): Amount = Amount(
    basisPoints = value,
    identifier = "SOL",
    decimals = 9
)

/**
 * Create a whole-SOL amount. Matches the upstream top-level helper.
 */
fun SOL(value: Long): Amount = Amount(
    basisPoints = value * 1_000_000_000L,
    identifier = "SOL",
    decimals = 9
)
