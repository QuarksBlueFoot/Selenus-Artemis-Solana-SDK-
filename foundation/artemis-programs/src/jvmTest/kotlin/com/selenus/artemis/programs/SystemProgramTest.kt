package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for SystemProgram instruction builders.
 */
class SystemProgramTest {

    private val payer = Pubkey.fromBase58("11111111111111111111111111111111")
    private val account = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val base = Pubkey.fromBase58("Vote111111111111111111111111111111111111111")
    private val dest = Pubkey.fromBase58("Sysvar1111111111111111111111111111111111111")

    @Test
    fun testCreateAccount() {
        val ix = SystemProgram.createAccount(
            from = payer, newAccount = account,
            lamports = 1_000_000L, space = 165L, owner = base
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(2, ix.accounts.size)
        assertTrue(ix.accounts[0].isSigner)
        assertTrue(ix.accounts[1].isSigner)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, bb.getInt()) // CreateAccount opcode
        assertEquals(1_000_000L, bb.getLong())
        assertEquals(165L, bb.getLong())
    }

    @Test
    fun testTransfer() {
        val ix = SystemProgram.transfer(from = payer, to = dest, lamports = 500_000L)
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(2, ix.accounts.size)
        assertTrue(ix.accounts[0].isSigner)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, bb.getInt()) // Transfer opcode
        assertEquals(500_000L, bb.getLong())
    }

    @Test
    fun testAssign() {
        val ix = SystemProgram.assign(account = payer, programId = account)
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(1, ix.accounts.size)
        assertTrue(ix.accounts[0].isSigner)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, bb.getInt()) // Assign opcode
    }

    @Test
    fun testAllocate() {
        val ix = SystemProgram.allocate(account = payer, space = 200L)
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(1, ix.accounts.size)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(8, bb.getInt()) // Allocate opcode
        assertEquals(200L, bb.getLong())
    }

    @Test
    fun testTransferWithSeed() {
        val ix = SystemProgram.transferWithSeed(
            from = account, base = payer, seed = "test",
            owner = base, to = dest, lamports = 100_000L
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(3, ix.accounts.size)
        // from account (writable, not signer)
        assertTrue(ix.accounts[0].isWritable)
        // base (signer)
        assertTrue(ix.accounts[1].isSigner)
        // destination (writable)
        assertTrue(ix.accounts[2].isWritable)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(11, bb.getInt()) // TransferWithSeed opcode
        assertEquals(100_000L, bb.getLong())
    }

    @Test
    fun testAllocateWithSeed() {
        val ix = SystemProgram.allocateWithSeed(
            account = account, base = payer, seed = "alloc",
            space = 128L, owner = base
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(9, bb.getInt()) // AllocateWithSeed opcode
    }

    @Test
    fun testAssignWithSeed() {
        val ix = SystemProgram.assignWithSeed(
            account = account, base = payer, seed = "assign", owner = base
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(10, bb.getInt()) // AssignWithSeed opcode
    }

    @Test
    fun testCreateAccountWithSeed() {
        val ix = SystemProgram.createAccountWithSeed(
            from = payer, newAccount = account, base = payer,
            seed = "test", lamports = 2_000_000L, space = 80L, owner = base
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        // from == base, so only 2 accounts
        assertEquals(2, ix.accounts.size)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(3, bb.getInt()) // CreateAccountWithSeed opcode
    }

    // ════════════════════════════════════════════════════════════════════════
    // NONCE INSTRUCTIONS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testAdvanceNonceAccount() {
        val ix = SystemProgram.advanceNonceAccount(
            nonceAccount = account, nonceAuthority = payer
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(3, ix.accounts.size) // nonce, sysvar, authority
        assertTrue(ix.accounts[2].isSigner) // authority signs
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, bb.getInt()) // AdvanceNonce opcode
    }

    @Test
    fun testWithdrawNonceAccount() {
        val ix = SystemProgram.withdrawNonceAccount(
            nonceAccount = account, nonceAuthority = payer,
            toPubkey = dest, lamports = 1_000_000L
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(5, ix.accounts.size) // nonce, to, sysvar, rent, authority
        assertTrue(ix.accounts[4].isSigner) // authority signs
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(5, bb.getInt()) // WithdrawNonce opcode
        assertEquals(1_000_000L, bb.getLong())
    }

    @Test
    fun testInitializeNonceAccount() {
        val ix = SystemProgram.initializeNonceAccount(
            nonceAccount = account, authority = payer
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(3, ix.accounts.size) // nonce, sysvar, rent
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(6, bb.getInt()) // InitializeNonce opcode
    }

    @Test
    fun testAuthorizeNonceAccount() {
        val ix = SystemProgram.authorizeNonceAccount(
            nonceAccount = account, currentAuthority = payer, newAuthority = dest
        )
        assertEquals(SystemProgram.PROGRAM_ID, ix.programId)
        assertEquals(2, ix.accounts.size) // nonce, current authority
        assertTrue(ix.accounts[1].isSigner)
        val bb = ByteBuffer.wrap(ix.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(7, bb.getInt()) // AuthorizeNonce opcode
    }
}
