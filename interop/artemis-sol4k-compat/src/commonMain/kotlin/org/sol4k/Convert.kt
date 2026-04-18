/*
 * Drop-in source compatibility with org.sol4k.Convert.
 *
 * Apps call `Convert.lamportToSol` and `Convert.solToLamport` when formatting
 * balances for the UI. Upstream uses `BigDecimal` throughout for exact
 * integer-cent accounting; same type here so downstream math doesn't drift.
 */
package org.sol4k

import java.math.BigDecimal
import java.math.RoundingMode

object Convert {
    private val LAMPORTS_PER_SOL: BigDecimal = BigDecimal("1000000000")
    private val LAMPORTS_PER_MICRO: BigDecimal = BigDecimal("1000000")

    /** Convert a lamport string to SOL, rounded up to preserve fairness. */
    @JvmStatic
    fun lamportToSol(v: String): BigDecimal = lamportToSol(BigDecimal(v))

    @JvmStatic
    fun lamportToSol(v: BigDecimal): BigDecimal =
        v.divide(LAMPORTS_PER_SOL, 9, RoundingMode.CEILING)

    /** Convert a SOL string to lamports. */
    @JvmStatic
    fun solToLamport(v: String): BigDecimal = solToLamport(BigDecimal(v))

    @JvmStatic
    fun solToLamport(v: BigDecimal): BigDecimal = v.multiply(LAMPORTS_PER_SOL)

    /** micro-lamports → lamports. */
    @JvmStatic
    fun microToLamport(v: BigDecimal): BigDecimal =
        v.divide(LAMPORTS_PER_MICRO, 6, RoundingMode.CEILING)

    /** lamports → micro-lamports. */
    @JvmStatic
    fun lamportToMicro(v: BigDecimal): BigDecimal = v.multiply(LAMPORTS_PER_MICRO)
}
