package com.selenus.artemis.batch

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionBatchEngineTest {

    private val alice = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val bob = Pubkey.fromBase58("11111111111111111111111111111111")
    private val carol = Pubkey.fromBase58("So11111111111111111111111111111111111111112")
    private val programId = Pubkey.fromBase58("11111111111111111111111111111111")

    private fun makeOp(id: String, writableAccounts: List<Pubkey>, computeUnits: Int = 50_000): BatchOperation {
        val ix = Instruction(
            programId = programId,
            accounts = writableAccounts.map { AccountMeta(it, isSigner = false, isWritable = true) },
            data = byteArrayOf(1)
        )
        return BatchOperation(
            id = id,
            instructions = listOf(ix),
            estimatedComputeUnits = computeUnits,
            description = "op-$id"
        )
    }

    @Test
    fun `plan puts non-conflicting operations in single batch`() {
        val engine = TransactionBatchEngine()
        val ops = listOf(
            makeOp("1", listOf(alice)),
            makeOp("2", listOf(bob)),
            makeOp("3", listOf(carol))
        )
        val plan = engine.plan(ops)
        assertEquals(1, plan.batches.size)
        assertEquals(3, plan.totalOperations)
    }

    @Test
    fun `plan splits operations with write-write conflicts`() {
        val engine = TransactionBatchEngine()
        val ops = listOf(
            makeOp("1", listOf(alice)),
            makeOp("2", listOf(alice)) // same writable account
        )
        val plan = engine.plan(ops)
        assertEquals(2, plan.batches.size)
        assertEquals(1, plan.batches[0].operations.size)
        assertEquals(1, plan.batches[1].operations.size)
    }

    @Test
    fun `plan splits on compute unit overflow`() {
        val engine = TransactionBatchEngine(config = BatchConfig(maxComputeUnitsPerTx = 100_000))
        val ops = listOf(
            makeOp("1", listOf(alice), computeUnits = 60_000),
            makeOp("2", listOf(bob), computeUnits = 60_000)
        )
        val plan = engine.plan(ops)
        assertEquals(2, plan.batches.size)
    }

    @Test
    fun `plan handles empty operations`() {
        val engine = TransactionBatchEngine()
        val plan = engine.plan(emptyList())
        assertEquals(0, plan.batches.size)
        assertEquals(0, plan.totalOperations)
    }

    @Test
    fun `analyzeConflicts detects write-write conflicts`() {
        val engine = TransactionBatchEngine()
        val ops = listOf(
            makeOp("1", listOf(alice, bob)),
            makeOp("2", listOf(bob, carol)),
            makeOp("3", listOf(carol))
        )
        val conflicts = engine.analyzeConflicts(ops)
        // op1 vs op2 share bob, op2 vs op3 share carol
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.any { it.operationA == "1" && it.operationB == "2" })
        assertTrue(conflicts.any { it.operationA == "2" && it.operationB == "3" })
    }

    @Test
    fun `analyzeConflicts returns empty for non-conflicting operations`() {
        val engine = TransactionBatchEngine()
        val ops = listOf(
            makeOp("1", listOf(alice)),
            makeOp("2", listOf(bob))
        )
        val conflicts = engine.analyzeConflicts(ops)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `estimateBatchCount returns at least 1`() {
        val engine = TransactionBatchEngine()
        val ops = listOf(makeOp("1", listOf(alice)))
        assertTrue(engine.estimateBatchCount(ops) >= 1)
    }
}
