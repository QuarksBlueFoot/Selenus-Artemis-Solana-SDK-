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

// ---------------------------------------------------------------------------
// Lower-case factory helpers (upstream added these alongside the original
// `Lamports()` / `SOL()` names). The shim keeps both spellings live.
// ---------------------------------------------------------------------------

/** Lower-case alias matching upstream `lamports(Long)`. */
fun lamports(value: Long): Amount = Lamports(value)

/** Lower-case alias matching upstream `lamports(Int)`. */
fun lamports(value: Int): Amount = Lamports(value.toLong())

/** Lower-case alias matching upstream `sol(Int)`. */
fun sol(value: Int): Amount = SOL(value.toLong())

/** Lower-case alias matching upstream `sol(Double)`. */
fun sol(value: Double): Amount = Amount(
    basisPoints = (value * 1_000_000_000.0).toLong(),
    identifier = "SOL",
    decimals = 9
)

/**
 * Create an amount by specifying basisPoints, identifier, and decimals directly.
 * Matches upstream `createAmount(Long, String, Int)`.
 */
fun createAmount(basisPoints: Long, identifier: String, decimals: Int): Amount =
    Amount(basisPoints, identifier, decimals)

/** Int overload of [createAmount]. */
fun createAmount(basisPoints: Int, identifier: String, decimals: Int): Amount =
    Amount(basisPoints.toLong(), identifier, decimals)

/**
 * Build an amount from its decimal form (e.g. `1.5 SOL` as `1.5`). Matches
 * upstream `createAmountFromDecimals`.
 */
fun createAmountFromDecimals(decimalAmount: Double, identifier: String, decimals: Int): Amount {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return Amount((decimalAmount * multiplier).toLong(), identifier, decimals)
}

/**
 * Create a percentage amount. `percent = 50.0` becomes `Amount(5000, "%", 2)`.
 * Matches upstream `percentAmount(Double, Int)`.
 */
fun percentAmount(percent: Double, decimals: Int = 2): Amount {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return Amount((percent * multiplier).toLong(), "%", decimals)
}

/**
 * Create a token amount by specifying the token count in its decimal form.
 * Defaults match upstream.
 */
fun tokenAmount(tokens: Double, identifier: String = "Token", decimals: Int = 0): Amount =
    createAmountFromDecimals(tokens, identifier, decimals)

/** Int overload of [tokenAmount]. */
fun tokenAmount(tokens: Int, identifier: String = "Token", decimals: Int = 0): Amount =
    Amount(tokens.toLong() * pow10Long(decimals), identifier, decimals)

// ---------------------------------------------------------------------------
// Arithmetic
// ---------------------------------------------------------------------------

private fun pow10Long(n: Int): Long {
    var v = 1L
    repeat(n) { v *= 10L }
    return v
}

private fun requireSame(left: Amount, right: Amount, op: String) {
    check(left.identifier == right.identifier && left.decimals == right.decimals) {
        "cannot $op amounts of different types (${left.identifier}/${left.decimals} vs ${right.identifier}/${right.decimals})"
    }
}

/** Add two amounts of the same identifier/decimals. */
fun addAmounts(left: Amount, right: Amount): Amount {
    requireSame(left, right, "add")
    return Amount(left.basisPoints + right.basisPoints, left.identifier, left.decimals)
}

/** Subtract `right` from `left`. */
fun subtractAmounts(left: Amount, right: Amount): Amount {
    requireSame(left, right, "subtract")
    return Amount(left.basisPoints - right.basisPoints, left.identifier, left.decimals)
}

/** Multiply `left` by a scalar. */
fun multiplyAmount(left: Amount, multiplier: Number): Amount =
    Amount((left.basisPoints * multiplier.toDouble()).toLong(), left.identifier, left.decimals)

/** Divide `left` by a scalar. */
fun divideAmount(left: Amount, divisor: Number): Amount {
    require(divisor.toDouble() != 0.0) { "cannot divide by zero" }
    return Amount((left.basisPoints / divisor.toDouble()).toLong(), left.identifier, left.decimals)
}

/** Absolute value of an amount. */
fun absoluteAmount(value: Amount): Amount =
    if (value.basisPoints >= 0) value
    else Amount(-value.basisPoints, value.identifier, value.decimals)

// ---------------------------------------------------------------------------
// Comparison and predicates
// ---------------------------------------------------------------------------

/** `-1`, `0`, or `1` ordering between two same-kind amounts. */
fun compareAmounts(left: Amount, right: Amount): Int {
    requireSame(left, right, "compare")
    return left.basisPoints.compareTo(right.basisPoints)
}

/** Equality with optional tolerance. `tolerance == null` means exact. */
fun isEqualToAmount(left: Amount, right: Amount, tolerance: Amount? = null): Boolean {
    requireSame(left, right, "compare")
    val delta = left.basisPoints - right.basisPoints
    return if (tolerance == null) delta == 0L
    else {
        requireSame(left, tolerance, "compare")
        kotlin.math.abs(delta) <= tolerance.basisPoints
    }
}

fun isLessThanAmount(left: Amount, right: Amount): Boolean = compareAmounts(left, right) < 0
fun isLessThanOrEqualToAmount(left: Amount, right: Amount): Boolean = compareAmounts(left, right) <= 0
fun isGreaterThanAmount(left: Amount, right: Amount): Boolean = compareAmounts(left, right) > 0
fun isGreaterThanOrEqualToAmount(left: Amount, right: Amount): Boolean = compareAmounts(left, right) >= 0

fun isZeroAmount(value: Amount): Boolean = value.basisPoints == 0L
fun isPositiveAmount(value: Amount): Boolean = value.basisPoints > 0L
fun isNegativeAmount(value: Amount): Boolean = value.basisPoints < 0L

/** Two amounts are the same kind (identifier + decimals) but not necessarily equal in value. */
fun sameAmounts(left: Amount, right: Amount): Boolean =
    left.identifier == right.identifier && left.decimals == right.decimals

/** Shape predicate: does [amount] match the given [identifier] and [decimals]? */
fun isAmount(amount: Amount, identifier: String, decimals: Int): Boolean =
    amount.identifier == identifier && amount.decimals == decimals

// ---------------------------------------------------------------------------
// Assertions (upstream throws a dedicated error type; we reuse IllegalStateException
// so callers don't need to import an extra class).
// ---------------------------------------------------------------------------

fun assertAmount(amount: Amount, identifier: String, decimals: Int) {
    check(isAmount(amount, identifier, decimals)) {
        "expected $identifier/$decimals amount but got ${amount.identifier}/${amount.decimals}"
    }
}

fun assertSolAmount(amount: Amount) = assertAmount(amount, "SOL", 9)

fun assertSameAmounts(left: Amount, right: Amount, operation: String? = null) {
    check(sameAmounts(left, right)) {
        val op = operation?.let { " during $it" } ?: ""
        "expected matching amount types${op} (${left.identifier}/${left.decimals} vs ${right.identifier}/${right.decimals})"
    }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

fun amountToString(value: Amount, maxDecimals: Int? = null): String {
    val d = value.toDecimal()
    if (maxDecimals == null) return d.toString()
    // KMP-safe truncating formatter: scale, round half-up, then restore decimal
    // point. Avoids pulling in `String.format` which is JVM-only.
    var scale = 1.0
    repeat(maxDecimals) { scale *= 10.0 }
    val rounded = kotlin.math.round(d * scale) / scale
    val s = rounded.toString()
    val dot = s.indexOf('.')
    return when {
        dot < 0 -> if (maxDecimals == 0) s else s + "." + "0".repeat(maxDecimals)
        else -> {
            val fractional = s.substring(dot + 1)
            val padded = if (fractional.length < maxDecimals) fractional + "0".repeat(maxDecimals - fractional.length)
                         else fractional.take(maxDecimals)
            if (maxDecimals == 0) s.substring(0, dot) else s.substring(0, dot) + "." + padded
        }
    }
}

fun amountToNumber(value: Amount): Double = value.toDecimal()

fun displayAmount(value: Amount, maxDecimals: Int? = null): String =
    "${amountToString(value, maxDecimals)} ${value.identifier}"
