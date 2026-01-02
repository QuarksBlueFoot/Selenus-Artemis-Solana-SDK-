package com.selenus.artemis.txpresets

import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the Artemis high-level DX helpers.
 */
class ArtemisHelperTest {

    private val alice = Pubkey(ByteArray(32) { 1 })
    private val bob = Pubkey(ByteArray(32) { 2 })
    private val mint = Pubkey(ByteArray(32) { 3 })
    private val stakeAccount = Pubkey(ByteArray(32) { 4 })
    private val voteAccount = Pubkey(ByteArray(32) { 5 })

    // ═══════════════════════════════════════════════════════════════════════
    // SOL Transfers
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `transferSol returns one SystemProgram transfer instruction`() {
        val ixs = Artemis.transferSol(alice, bob, 1_000_000_000L)
        assertEquals(1, ixs.size)
        assertEquals(ProgramIds.SYSTEM_PROGRAM, ixs[0].programId)
    }

    @Test
    fun `transferSol instruction references from and to accounts`() {
        val ixs = Artemis.transferSol(alice, bob, 500L)
        val accounts = ixs[0].accounts
        assertEquals(2, accounts.size)
        assertEquals(alice, accounts[0].pubkey)
        assertEquals(bob, accounts[1].pubkey)
        assertTrue(accounts[0].isSigner, "from must be signer")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SPL Token Transfers
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `transferToken with createAta returns two instructions`() {
        val ixs = Artemis.transferToken(alice, bob, mint, 100L, createAta = true)
        assertEquals(2, ixs.size)
    }

    @Test
    fun `transferToken without createAta returns one instruction`() {
        val ixs = Artemis.transferToken(alice, bob, mint, 100L, createAta = false)
        assertEquals(1, ixs.size)
    }

    @Test
    fun `transferToken uses token program by default`() {
        val ixs = Artemis.transferToken(alice, bob, mint, 100L, createAta = false)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[0].programId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mint Creation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `createMint returns two instructions`() {
        val ixs = Artemis.createMint(alice, mint, alice, 9)
        assertEquals(2, ixs.size)
        // First is system createAccount, second is token initializeMint
        assertEquals(ProgramIds.SYSTEM_PROGRAM, ixs[0].programId)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[1].programId)
    }

    @Test
    fun `createMint uses correct space and rent`() {
        val ixs = Artemis.createMint(alice, mint, alice, 6, space = 82, lamports = 1_461_600)
        assertEquals(2, ixs.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Minting Tokens
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mintTokens with ATA creation returns two instructions`() {
        val ixs = Artemis.mintTokens(mint, bob, alice, 1_000L, createAta = true)
        assertEquals(2, ixs.size)
    }

    @Test
    fun `mintTokens without ATA creation returns one instruction`() {
        val ixs = Artemis.mintTokens(mint, bob, alice, 1_000L, createAta = false)
        assertEquals(1, ixs.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Token Burns
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `burnTokens returns one instruction`() {
        val ixs = Artemis.burnTokens(mint, alice, 500L)
        assertEquals(1, ixs.size)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[0].programId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ATA Management
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `getAssociatedTokenAddress is deterministic`() {
        val addr1 = Artemis.getAssociatedTokenAddress(alice, mint)
        val addr2 = Artemis.getAssociatedTokenAddress(alice, mint)
        assertEquals(addr1, addr2)
    }

    @Test
    fun `getAssociatedTokenAddress differs for different owners`() {
        val addr1 = Artemis.getAssociatedTokenAddress(alice, mint)
        val addr2 = Artemis.getAssociatedTokenAddress(bob, mint)
        assertTrue(!addr1.bytes.contentEquals(addr2.bytes))
    }

    @Test
    fun `createAssociatedTokenAccount returns one instruction`() {
        val ixs = Artemis.createAssociatedTokenAccount(alice, bob, mint)
        assertEquals(1, ixs.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Staking
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `stakeSol returns create account instruction`() {
        val ixs = Artemis.stakeSol(alice, stakeAccount, voteAccount, 2_000_000_000L)
        assertTrue(ixs.isNotEmpty())
        assertEquals(ProgramIds.SYSTEM_PROGRAM, ixs[0].programId)
    }

    @Test
    fun `stakeSol creates account with 200 bytes space`() {
        val ixs = Artemis.stakeSol(alice, stakeAccount, voteAccount, 2_000_000_000L)
        assertNotNull(ixs[0])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Revoke Delegate
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `revokeDelegate returns one instruction`() {
        val ixs = Artemis.revokeDelegate(alice, mint)
        assertEquals(1, ixs.size)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[0].programId)
    }

    @Test
    fun `revokeDelegate instruction references derived ATA and owner`() {
        val ixs = Artemis.revokeDelegate(alice, mint)
        val accounts = ixs[0].accounts
        // First account is the derived ATA, second is owner
        assertEquals(alice, accounts[1].pubkey)
        assertTrue(accounts[1].isSigner, "owner must be signer")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Close Token Account
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `closeTokenAccount returns one instruction`() {
        val ixs = Artemis.closeTokenAccount(alice, mint)
        assertEquals(1, ixs.size)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[0].programId)
    }

    @Test
    fun `closeTokenAccount with explicit rent destination`() {
        val ixs = Artemis.closeTokenAccount(alice, mint, bob)
        assertEquals(1, ixs.size)
        val accounts = ixs[0].accounts
        // destination should be bob
        assertEquals(bob, accounts[1].pubkey)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Approve Delegate
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `approveDelegate returns one instruction`() {
        val delegate = Pubkey(ByteArray(32) { 7 })
        val ixs = Artemis.approveDelegate(alice, delegate, mint, 1000L)
        assertEquals(1, ixs.size)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ixs[0].programId)
    }

    @Test
    fun `approveDelegate instruction references delegate and owner`() {
        val delegate = Pubkey(ByteArray(32) { 7 })
        val ixs = Artemis.approveDelegate(alice, delegate, mint, 500L)
        val accounts = ixs[0].accounts
        // approve: [source ATA, delegate, owner]
        assertEquals(delegate, accounts[1].pubkey)
        assertEquals(alice, accounts[2].pubkey)
        assertTrue(accounts[2].isSigner, "owner must be signer")
    }
}
