package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for StakeProgram instruction builders.
 */
class StakeProgramTest {

    private val staker = Pubkey.fromBase58("11111111111111111111111111111111")
    private val withdrawer = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val voteAccount = Pubkey.fromBase58("Vote111111111111111111111111111111111111111")
    private val recipient = Pubkey.fromBase58("Sysvar1111111111111111111111111111111111111")

    @Test
    fun testWithdrawInstruction() {
        val ix = StakeProgram.withdraw(
            stakeAccount = staker,
            withdrawAuthority = withdrawer,
            to = recipient,
            lamports = 5_000_000L
        )
        assertEquals(StakeProgram.PROGRAM_ID, ix.programId)
        assertEquals(5, ix.accounts.size)
        // First account = stake account (writable)
        assertTrue(ix.accounts[0].isWritable)
        // Second account = destination (writable)
        assertTrue(ix.accounts[1].isWritable)
        assertEquals(recipient, ix.accounts[1].pubkey)
        // Last account = withdraw authority (signer)
        assertTrue(ix.accounts[4].isSigner)
        // Data: instruction index 4 (withdraw) + lamports
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, bb.getInt())
        assertEquals(5_000_000L, bb.getLong())
    }

    @Test
    fun testMergeInstruction() {
        val source = Pubkey.fromBase58("Sysvar1111111111111111111111111111111111111")
        val ix = StakeProgram.merge(
            destination = staker,
            source = source,
            authorizedStaker = withdrawer
        )
        assertEquals(StakeProgram.PROGRAM_ID, ix.programId)
        assertEquals(5, ix.accounts.size)
        // Data: instruction index 7 (merge)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(7, bb.getInt())
    }

    @Test
    fun testInitializeInstruction() {
        val ix = StakeProgram.initialize(
            stakeAccount = staker,
            authorized = StakeProgram.Authorized(staker, withdrawer)
        )
        assertEquals(StakeProgram.PROGRAM_ID, ix.programId)
        assertEquals(2, ix.accounts.size)
        // Data: instruction index 0 (initialize) + authorized + lockup
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, bb.getInt())
    }

    @Test
    fun testDelegateInstruction() {
        val ix = StakeProgram.delegate(
            stakeAccount = staker,
            voteAccount = voteAccount,
            authorizedStaker = withdrawer
        )
        assertEquals(StakeProgram.PROGRAM_ID, ix.programId)
        assertEquals(6, ix.accounts.size)
        // Data: instruction index 2 (delegate)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, bb.getInt())
    }

    @Test
    fun testDeactivateInstruction() {
        val ix = StakeProgram.deactivate(
            stakeAccount = staker,
            authorizedStaker = withdrawer
        )
        assertEquals(StakeProgram.PROGRAM_ID, ix.programId)
        assertEquals(3, ix.accounts.size)
        // Data: instruction index 5 (deactivate)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(5, bb.getInt())
    }
}
