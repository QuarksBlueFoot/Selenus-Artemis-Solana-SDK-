/*
 * Drop-in source compatibility with org.sol4k.instruction compute-budget
 * helpers.
 *
 * Two common instructions every production dapp emits: set compute unit
 * limit and set compute unit price. Both are top-level in sol4k so the
 * shim keeps them there.
 */
package org.sol4k

/**
 * `ComputeBudgetProgram.SetComputeUnitLimit` instruction. Index 2 in the
 * compute budget program, payload is a little-endian u32.
 */
class SetComputeUnitLimitInstruction(val units: Int) : Instruction {
    override val programId: PublicKey = Constants.COMPUTE_BUDGET_PROGRAM_ID
    override val keys: List<AccountMeta> = emptyList()
    override val data: ByteArray = ByteArray(5).apply {
        this[0] = 2
        this[1] = (units and 0xFF).toByte()
        this[2] = ((units shr 8) and 0xFF).toByte()
        this[3] = ((units shr 16) and 0xFF).toByte()
        this[4] = ((units shr 24) and 0xFF).toByte()
    }
    override fun toArtemis(): com.selenus.artemis.tx.Instruction = com.selenus.artemis.tx.Instruction(
        programId = com.selenus.artemis.runtime.Pubkey(programId.bytes),
        accounts = emptyList(),
        data = data
    )
}

/**
 * `ComputeBudgetProgram.SetComputeUnitPrice` instruction. Index 3, payload
 * is a little-endian u64 representing the priority fee in micro-lamports
 * per compute unit.
 */
class SetComputeUnitPriceInstruction(val microLamports: Long) : Instruction {
    override val programId: PublicKey = Constants.COMPUTE_BUDGET_PROGRAM_ID
    override val keys: List<AccountMeta> = emptyList()
    override val data: ByteArray = ByteArray(9).apply {
        this[0] = 3
        for (i in 0..7) this[i + 1] = ((microLamports shr (i * 8)) and 0xFF).toByte()
    }
    override fun toArtemis(): com.selenus.artemis.tx.Instruction = com.selenus.artemis.tx.Instruction(
        programId = com.selenus.artemis.runtime.Pubkey(programId.bytes),
        accounts = emptyList(),
        data = data
    )
}
