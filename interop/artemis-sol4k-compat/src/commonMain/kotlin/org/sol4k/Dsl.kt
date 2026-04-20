/*
 * Artemis innovation on top of sol4k: Kotlin DSL builders.
 *
 * Upstream sol4k constructs transactions positionally:
 *     Transaction("bh", listOf(ix1, ix2), feePayer)
 *
 * Long call sites with many instructions become hard to read. The DSL makes
 * the same tx read top-to-bottom, matches how most modern Kotlin libs feel
 * (compose, ktor, exposed), and still produces the exact same wire bytes.
 *
 * This file is purely additive over upstream - no existing sol4k call site
 * has to change to use it.
 */
package org.sol4k

/**
 * DSL receiver for [transaction]. Instructions are appended in declaration
 * order; calling [instruction] multiple times composes them into one tx.
 */
@DslMarker
annotation class Sol4kDsl

@Sol4kDsl
class TransactionBuilder {
    private var feePayer: PublicKey? = null
    private var blockhash: String? = null
    private val instructions: MutableList<Instruction> = mutableListOf()

    /** Set the fee payer. Required. */
    fun feePayer(pubkey: PublicKey) {
        feePayer = pubkey
    }

    /** Set the recent blockhash. Required. */
    fun blockhash(value: String) {
        blockhash = value
    }

    /** Append an instruction. */
    fun instruction(ix: Instruction) {
        instructions.add(ix)
    }

    /** Append multiple instructions at once. */
    fun instructions(vararg ixs: Instruction) {
        instructions.addAll(ixs)
    }

    /** Compute-budget convenience: set the compute unit limit. */
    fun computeUnits(units: Int) {
        instructions.add(SetComputeUnitLimitInstruction(units))
    }

    /** Compute-budget convenience: set the priority fee in micro-lamports. */
    fun priority(microLamports: Long) {
        instructions.add(SetComputeUnitPriceInstruction(microLamports))
    }

    fun build(): Transaction {
        val fp = requireNotNull(feePayer) { "feePayer is required" }
        val bh = requireNotNull(blockhash) { "blockhash is required" }
        return Transaction(recentBlockhash = bh, instructions = instructions.toList(), feePayer = fp)
    }
}

/**
 * Build a [Transaction] with the builder DSL.
 *
 * ```kotlin
 * val tx = transaction {
 *     feePayer(wallet.publicKey)
 *     blockhash(connection.getLatestBlockhash())
 *     computeUnits(400_000)
 *     priority(microLamports = 1_000)
 *     instruction(TransferInstruction(from, to, lamports))
 * }
 * ```
 */
inline fun transaction(block: TransactionBuilder.() -> Unit): Transaction =
    TransactionBuilder().apply(block).build()
