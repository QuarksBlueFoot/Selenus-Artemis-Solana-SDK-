package com.selenus.artemis.programs

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for artemis-programs module.
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

    @Test
    fun testTokenProgramSetAuthority() {
        val ix = TokenProgram.setAuthority(
            account = testPubkey1,
            currentAuthority = testPubkey2,
            authorityType = TokenAuthorityType.FreezeAccount,
            newAuthority = testPubkey3
        )

        assertEquals(ProgramIds.TOKEN_PROGRAM, ix.programId)
        assertEquals(35, ix.data.size)
        assertEquals(6, ix.data[0].toInt() and 0xff)
        assertEquals(1, ix.data[1].toInt() and 0xff)
        assertEquals(1, ix.data[2].toInt() and 0xff)
        assertContentEquals(testPubkey3.bytes, ix.data.copyOfRange(3, 35))
        assertEquals(listOf(testPubkey1, testPubkey2), ix.accounts.map { it.pubkey })
        assertTrue(ix.accounts[0].isWritable)
        assertFalse(ix.accounts[0].isSigner)
        assertTrue(ix.accounts[1].isSigner)
    }

    @Test
    fun testTokenProgramSetAuthorityNone() {
        val ix = TokenProgram.setAuthority(
            account = testPubkey1,
            currentAuthority = testPubkey2,
            authorityType = TokenAuthorityType.CloseAccount,
            newAuthority = null
        )

        assertContentEquals(byteArrayOf(6, 3, 0), ix.data)
    }

    @Test
    fun testTokenProgramFreezeAndThawWithMultisig() {
        val signer1 = Pubkey(ByteArray(32) { 4 })
        val signer2 = Pubkey(ByteArray(32) { 5 })
        val freeze = TokenProgram.freezeAccount(
            account = testPubkey1,
            mint = testPubkey2,
            authority = testPubkey3,
            signers = listOf(signer1, signer2)
        )
        val thaw = TokenProgram.thawAccount(
            account = testPubkey1,
            mint = testPubkey2,
            authority = testPubkey3,
            signers = listOf(signer1)
        )

        assertEquals(10, freeze.data.single().toInt() and 0xff)
        assertEquals(11, thaw.data.single().toInt() and 0xff)
        assertEquals(listOf(testPubkey1, testPubkey2, testPubkey3, signer1, signer2), freeze.accounts.map { it.pubkey })
        assertTrue(freeze.accounts[0].isWritable)
        assertFalse(freeze.accounts[1].isWritable)
        assertFalse(freeze.accounts[2].isSigner)
        assertTrue(freeze.accounts[3].isSigner)
        assertTrue(freeze.accounts[4].isSigner)
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
        assertContentEquals(byteArrayOf(), ix.data)
    }

    @Test
    fun testAssociatedTokenProgramCreateAtaIdempotentWithToken2022() {
        val ata = AssociatedTokenProgram.address(testPubkey3, testPubkey2, ProgramIds.TOKEN_2022_PROGRAM)
        val ix = AssociatedTokenProgram.createAssociatedTokenAccountIdempotent(
            payer = testPubkey1,
            ata = ata,
            owner = testPubkey3,
            mint = testPubkey2,
            tokenProgram = ProgramIds.TOKEN_2022_PROGRAM
        )

        assertEquals(ProgramIds.ASSOCIATED_TOKEN_PROGRAM, ix.programId)
        assertContentEquals(byteArrayOf(1), ix.data)
        assertEquals(listOf(testPubkey1, ata, testPubkey3, testPubkey2), ix.accounts.take(4).map { it.pubkey })
        assertEquals(ProgramIds.TOKEN_2022_PROGRAM, ix.accounts[5].pubkey)
    }

    // ===== AddressLookupTableProgram Tests =====

    @Test
    fun testAddressLookupTableLifecycleInstructions() {
        val lookupTable = Pubkey(ByteArray(32) { 6 })
        val newAddress = Pubkey(ByteArray(32) { 7 })
        val (create, derivedTable) = AddressLookupTableProgram.createLookupTable(
            authority = testPubkey1,
            payer = testPubkey2,
            recentSlot = 123L
        )
        val extend = AddressLookupTableProgram.extendLookupTable(
            lookupTable = lookupTable,
            authority = testPubkey1,
            payer = testPubkey2,
            newAddresses = listOf(newAddress)
        )
        val freeze = AddressLookupTableProgram.freezeLookupTable(lookupTable, testPubkey1)
        val deactivate = AddressLookupTableProgram.deactivateLookupTable(lookupTable, testPubkey1)
        val close = AddressLookupTableProgram.closeLookupTable(lookupTable, testPubkey1, testPubkey3)

        assertEquals(AddressLookupTableProgram.PROGRAM_ID, create.programId)
        assertEquals(derivedTable, create.accounts[0].pubkey)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), create.data.copyOfRange(0, 4))
        assertTrue(create.accounts[1].isSigner)
        assertTrue(create.accounts[2].isSigner)
        assertTrue(create.accounts[2].isWritable)

        assertContentEquals(byteArrayOf(2, 0, 0, 0), extend.data.copyOfRange(0, 4))
        assertEquals(listOf(lookupTable, testPubkey1, testPubkey2), extend.accounts.take(3).map { it.pubkey })
        assertTrue(extend.accounts[0].isWritable)
        assertTrue(extend.accounts[1].isSigner)
        assertTrue(extend.accounts[2].isSigner)

        assertContentEquals(byteArrayOf(1, 0, 0, 0), freeze.data)
        assertContentEquals(byteArrayOf(3, 0, 0, 0), deactivate.data)
        assertContentEquals(byteArrayOf(4, 0, 0, 0), close.data)
        assertEquals(listOf(lookupTable, testPubkey1), freeze.accounts.map { it.pubkey })
        assertEquals(listOf(lookupTable, testPubkey1), deactivate.accounts.map { it.pubkey })
        assertEquals(listOf(lookupTable, testPubkey1, testPubkey3), close.accounts.map { it.pubkey })
        assertTrue(close.accounts[2].isWritable)
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

    @Test
    fun testToken2022AuthorityBuilders() {
        val setAuthority = Token2022Program.setAuthority(
            account = testPubkey1,
            currentAuthority = testPubkey2,
            authorityType = TokenAuthorityType.MintTokens,
            newAuthority = null
        )
        val freeze = Token2022Program.freezeAccount(testPubkey1, testPubkey2, testPubkey3)
        val thaw = Token2022Program.thawAccount(testPubkey1, testPubkey2, testPubkey3)

        assertEquals(ProgramIds.TOKEN_2022_PROGRAM, setAuthority.programId)
        assertContentEquals(byteArrayOf(6, 0, 0), setAuthority.data)
        assertEquals(10, freeze.data.single().toInt() and 0xff)
        assertEquals(11, thaw.data.single().toInt() and 0xff)
    }
}
