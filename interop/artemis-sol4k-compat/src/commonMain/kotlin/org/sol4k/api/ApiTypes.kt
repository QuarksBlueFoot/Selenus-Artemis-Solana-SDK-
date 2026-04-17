/*
 * Drop-in source compatibility with org.sol4k.api.
 *
 * sol4k keeps its RPC response DTOs in the `org.sol4k.api` sub-package so
 * users can import types without pulling the top-level `Connection` class.
 * This file re-publishes each type at the same fully qualified name.
 *
 * Where a type already exists at the top-level `org.sol4k` package (for
 * example `TokenAmount`), the `api` alias points at it instead of forking
 * the data class, so equality and pattern matching behave identically.
 */
package org.sol4k.api

import org.sol4k.Blockhash as TopBlockhash
import org.sol4k.EpochInfo as TopEpochInfo
import org.sol4k.Health as TopHealth
import org.sol4k.PrioritizationFee as TopPrioritizationFee
import org.sol4k.TokenAccountBalance as TopTokenAccountBalance
import org.sol4k.TokenAmount as TopTokenAmount
import org.sol4k.TransactionSignature as TopTransactionSignature
import org.sol4k.TransactionSimulation as TopTransactionSimulation
import org.sol4k.Version as TopVersion

/** sol4k-compatible commitment level. Forwards to the top-level enum. */
typealias Commitment = org.sol4k.Commitment

/** sol4k-compatible `api.AccountInfo` (distinct from the nullable top-level one). */
data class AccountInfo(
    val lamports: Long,
    val owner: org.sol4k.PublicKey,
    val data: ByteArray,
    val executable: Boolean,
    val rentEpoch: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountInfo) return false
        return lamports == other.lamports &&
            owner == other.owner &&
            data.contentEquals(other.data) &&
            executable == other.executable &&
            rentEpoch == other.rentEpoch
    }

    override fun hashCode(): Int {
        var result = lamports.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + executable.hashCode()
        result = 31 * result + rentEpoch.hashCode()
        return result
    }
}

/** Matches upstream `api.IsBlockhashValidResult`. */
data class IsBlockhashValidResult(val valid: Boolean, val slot: Long)

typealias Blockhash = TopBlockhash
typealias EpochInfo = TopEpochInfo
typealias Health = TopHealth
typealias PrioritizationFee = TopPrioritizationFee
typealias TokenAmount = TopTokenAmount
typealias TokenAccountBalance = TopTokenAccountBalance
typealias TransactionSignature = TopTransactionSignature
typealias TransactionSimulation = TopTransactionSimulation
typealias Version = TopVersion
