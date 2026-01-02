package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-programs module.
 * Tests SystemProgram, TokenProgram, and related instruction builders.
 */
class ProgramsModuleTest {

    private val testPubkey1 = Pubkey(ByteArray(32) { 1 })
    private val testPubkey2 = Pubkey(ByteArray(32) { 2 })
    private val testPubkey3 = Pubkey(ByteArray(32) { 3 })

    // ===== ProgramIds Tests =====

    @Test
    fun testSystemProgramId() {
        assertNotNull(ProgramIds.SYSTEM_PROGRAM)
        assertEquals(32, ProgramIds.SYSTEM_PROGRAM.bytes.size)
    }

    @Test
    fun testTokenProgramId() {
        assertNotNull(ProgramIds.TOKEN_PROGRAM)
        assertEquals(32, ProgramIds.TOKEN_PROGRAM.bytes.size)
    }

    @Test
    fun testToken2022ProgramId() {
        assertNotNull(ProgramIds.TOKEN_2022_PROGRAM)
        assertEquals(32, ProgramIds.TOKEN_2022_PROGRAM.bytes.size)
    }

    @Test
    fun testAssociatedTokenProgramId() {
        assertNotNull(ProgramIds.ASSOCIATED_TOKEN_PROGRAM)
        assertEquals(32, ProgramIds.ASSOCIATED_TOKEN_PROGRAM.bytes.size)
    }

    @Test
    fun testMemoProgramId() {
        assertNotNull(ProgramIds.MEMO_PROGRAM)
        assertEquals(32, ProgramIds.MEMO_PROGRAM.bytes.size)
    }

    @Test
    fun testMetaplexTokenMetadataId() {
        assertNotNull(ProgramIds.METAPLEX_TOKEN_METADATA)
        assertEquals(32, ProgramIds.METAPLEX_TOKEN_METADATA.bytes.size)
    }

    @Test
    fun testRentSysvar() {
        assertNotNull(ProgramIds.RENT_SYSVAR)
        assertEquals(32, ProgramIds.RENT_SYSVAR.bytes.size)
    }

    // ===== SystemProgram Tests =====

    @Test
    fun testSystemProgramTransfer() {
        val ix = SystemProgram.transfer(
            from = testPubkey1,
            to = testPubkey2,
            lamports = 1_000_000L
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.SYSTEM_PROGRAM, ix.programId)
        assertEquals(2, ix.accounts.size)
        assertTrue(ix.data.isNotEmpty())
    }

    @Test
    fun testSystemProgramCreateAccount() {
        val ix = SystemProgram.createAccount(
            from = testPubkey1,
            newAccount = testPubkey2,
            lamports = 1_000_000L,
            space = 165L,
            owner = ProgramIds.TOKEN_PROGRAM
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.SYSTEM_PROGRAM, ix.programId)
        assertEquals(2, ix.accounts.size)
    }

    // ===== TokenProgram Tests =====

    @Test
    fun testTokenProgramTransfer() {
        val ix = TokenProgram.transfer(
            source = testPubkey1,
            destination = testPubkey2,
            owner = testPubkey3,
            amount = 100L
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ix.programId)
        assertEquals(3, ix.accounts.size)
    }

    @Test
    fun testTokenProgramInitializeMint() {
        val ix = TokenProgram.initializeMint(
            mint = testPubkey1,
            decimals = 9,
            mintAuthority = testPubkey2,
            freezeAuthority = testPubkey3
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ix.programId)
    }

    @Test
    fun testTokenProgramInitializeMintNoFreeze() {
        val ix = TokenProgram.initializeMint(
            mint = testPubkey1,
            decimals = 6,
            mintAuthority = testPubkey2,
            freezeAuthority = null
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.TOKEN_PROGRAM, ix.programId)
    }

    // ===== AssociatedTokenProgram Tests =====

    @Test
    fun testAssociatedTokenAddress() {
        val ata = AssociatedToken.address(
            owner = testPubkey1,
            mint = testPubkey2,
            tokenProgram = ProgramIds.TOKEN_PROGRAM
        )
        
        assertNotNull(ata)
        assertEquals(32, ata.bytes.size)
    }

    @Test
    fun testAssociatedTokenAddressDeterministic() {
        val ata1 = AssociatedToken.address(testPubkey1, testPubkey2)
        val ata2 = AssociatedToken.address(testPubkey1, testPubkey2)
        
        assertEquals(ata1, ata2)
    }

    @Test
    fun testAssociatedTokenAddressDifferentOwners() {
        val ata1 = AssociatedToken.address(testPubkey1, testPubkey2)
        val ata2 = AssociatedToken.address(testPubkey3, testPubkey2)
        
        assertTrue(!ata1.bytes.contentEquals(ata2.bytes))
    }

    @Test
    fun testAssociatedTokenProgramCreateAta() {
        val ix = AssociatedTokenProgram.createAssociatedTokenAccount(
            payer = testPubkey1,
            ata = testPubkey2,
            owner = testPubkey3,
            mint = Pubkey(ByteArray(32) { 4 })
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.ASSOCIATED_TOKEN_PROGRAM, ix.programId)
    }

    // ===== MemoProgram Tests =====

    @Test
    fun testMemoProgram() {
        val ix = MemoProgram.memo("Hello, Solana!")
        
        assertNotNull(ix)
        assertEquals(ProgramIds.MEMO_PROGRAM, ix.programId)
    }

    @Test
    fun testMemoProgramEmptyMessage() {
        val ix = MemoProgram.memo("")
        
        assertNotNull(ix)
    }

    // ===== Token2022Program Tests =====

    @Test
    fun testToken2022ProgramInitializeMint() {
        val ix = Token2022Program.initializeMint2(
            mint = testPubkey1,
            decimals = 9,
            mintAuthority = testPubkey2,
            freezeAuthority = testPubkey3
        )
        
        assertNotNull(ix)
        assertEquals(ProgramIds.TOKEN_2022_PROGRAM, ix.programId)
    }
}
