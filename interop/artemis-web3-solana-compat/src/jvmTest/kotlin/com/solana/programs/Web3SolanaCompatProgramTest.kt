package com.solana.programs

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Web3SolanaCompatProgramTest {

    private val source = key(1)
    private val destination = key(2)
    private val owner = key(3)
    private val mint = key(4)
    private val delegate = key(5)

    @Test
    fun `AssociatedTokenProgram exposes idempotent and Token-2022-aware helpers`() {
        val ata = AssociatedTokenProgram.deriveAddress(owner, mint)
        val token2022Ata = AssociatedTokenProgram.deriveAddress(owner, mint, Token2022Program.PROGRAM_ID)

        val create = AssociatedTokenProgram.createAssociatedTokenAccount(
            payer = source,
            owner = owner,
            mint = mint,
            associatedToken = ata,
            tokenProgram = TokenProgram.PROGRAM_ID
        )
        val idempotent = AssociatedTokenProgram.createAssociatedTokenAccountIdempotent(
            payer = source,
            owner = owner,
            mint = mint,
            associatedToken = token2022Ata,
            tokenProgram = Token2022Program.PROGRAM_ID
        )

        assertEquals(AssociatedTokenProgram.PROGRAM_ID, create.programId)
        assertContentEquals(byteArrayOf(), create.data)
        assertContentEquals(byteArrayOf(1), idempotent.data)
        assertEquals(listOf(source, ata, owner, mint), create.accounts.take(4).map { it.publicKey })
        assertEquals(Token2022Program.PROGRAM_ID, idempotent.accounts[5].publicKey)
        assertFalse(ata.bytes.contentEquals(token2022Ata.bytes))
    }

    @Test
    fun `TokenProgram exposes native Artemis SPL Token builders through compat FQNs`() {
        assertEquals(TokenProgram.PROGRAM_ID, TokenProgram.TOKEN_PROGRAM_ID)

        val initializeMint = TokenProgram.initializeMint2(
            mint = mint,
            decimals = 6,
            mintAuthority = owner,
            freezeAuthority = null
        )
        assertEquals(TokenProgram.PROGRAM_ID, initializeMint.programId)
        assertEquals(20, initializeMint.data.first().toInt() and 0xFF)
        assertEquals(mint, initializeMint.accounts.single().publicKey)
        assertTrue(initializeMint.accounts.single().isWritable)

        val approve = TokenProgram.approve(source, delegate, owner, amount = 77L)
        assertEquals(4, approve.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, delegate, owner), approve.accounts.map { it.publicKey })
        assertTrue(approve.accounts[0].isWritable)
        assertFalse(approve.accounts[1].isWritable)
        assertTrue(approve.accounts[2].isSigner)

        val revoke = TokenProgram.revoke(source, owner)
        assertEquals(5, revoke.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, owner), revoke.accounts.map { it.publicKey })

        val setAuthority = TokenProgram.setAuthority(source, owner, TokenAuthorityType.FreezeAccount, delegate)
        assertEquals(6, setAuthority.data.first().toInt() and 0xFF)
        assertEquals(1, setAuthority.data[1].toInt() and 0xFF)
        assertEquals(1, setAuthority.data[2].toInt() and 0xFF)
        assertContentEquals(delegate.bytes, setAuthority.data.copyOfRange(3, 35))
        assertEquals(listOf(source, owner), setAuthority.accounts.map { it.publicKey })

        val clearAuthority = TokenProgram.setAuthority(source, owner, TokenAuthorityType.CloseAccount, null)
        assertContentEquals(byteArrayOf(6, 3, 0), clearAuthority.data)

        val freeze = TokenProgram.freezeAccount(source, mint, owner, signers = listOf(delegate))
        assertEquals(10, freeze.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, mint, owner, delegate), freeze.accounts.map { it.publicKey })
        assertFalse(freeze.accounts[2].isSigner)
        assertTrue(freeze.accounts[3].isSigner)

        val thaw = TokenProgram.thawAccount(source, mint, owner)
        assertEquals(11, thaw.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, mint, owner), thaw.accounts.map { it.publicKey })
        assertTrue(thaw.accounts[2].isSigner)

        val burn = TokenProgram.burn(source, mint, owner, amount = 9L)
        assertEquals(8, burn.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, mint, owner), burn.accounts.map { it.publicKey })

        val close = TokenProgram.closeAccount(source, destination, owner)
        assertEquals(9, close.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, destination, owner), close.accounts.map { it.publicKey })

        val checked = TokenProgram.transferChecked(source, mint, destination, owner, amount = 1_234L, decimals = 6)
        assertEquals(12, checked.data.first().toInt() and 0xFF)
        assertEquals(6, checked.data.last().toInt() and 0xFF)
        assertEquals(listOf(source, mint, destination, owner), checked.accounts.map { it.publicKey })

        val syncNative = TokenProgram.syncNative(source)
        assertEquals(17, syncNative.data.first().toInt() and 0xFF)
        assertEquals(listOf(source), syncNative.accounts.map { it.publicKey })
    }

    @Test
    fun `Token2022Program exposes checked transfers authorities and idempotent ATA helpers`() {
        assertEquals(Token2022Program.PROGRAM_ID, Token2022Program.TOKEN_2022_PROGRAM_ID)

        val initialize = Token2022Program.initializeMint2(mint, 6, owner, freezeAuthority = delegate)
        assertEquals(Token2022Program.PROGRAM_ID, initialize.programId)
        assertEquals(20, initialize.data.first().toInt() and 0xFF)

        val checked = Token2022Program.transferChecked(source, mint, destination, owner, 123L, 6)
        assertEquals(Token2022Program.PROGRAM_ID, checked.programId)
        assertEquals(12, checked.data.first().toInt() and 0xFF)
        assertEquals(listOf(source, mint, destination, owner), checked.accounts.map { it.publicKey })

        val mintToChecked = Token2022Program.mintToChecked(mint, destination, owner, 456L, 6)
        assertEquals(14, mintToChecked.data.first().toInt() and 0xFF)

        val immutableOwner = Token2022Program.initializeImmutableOwner(destination)
        assertEquals(22, immutableOwner.data.first().toInt() and 0xFF)

        val token2022Ata = Token2022Program.associatedTokenAddress(owner, mint)
        val createIdempotent = Token2022Program.createAssociatedTokenAccountIdempotent(source, owner, mint)
        assertEquals(token2022Ata, createIdempotent.accounts[1].publicKey)
        assertEquals(Token2022Program.PROGRAM_ID, createIdempotent.accounts[5].publicKey)
        assertContentEquals(byteArrayOf(1), createIdempotent.data)
    }

    @Test
    fun `Message Builder compiles program helpers into a legacy message`() {
        val blockhash = key(9)
        val message = Message.Builder()
            .addFeePayer(owner)
            .setRecentBlockhash(blockhash)
            .addInstruction(TokenProgram.transfer(source, destination, owner, amount = 42L))
            .build()

        assertEquals(1, message.signatureCount.toInt())
        assertEquals(blockhash.base58(), message.blockhash.base58())
        assertTrue(message.accounts.contains(TokenProgram.PROGRAM_ID))
        assertEquals(1, message.instructions.size)
        assertContentEquals(byteArrayOf(3, 42, 0, 0, 0, 0, 0, 0, 0), message.instructions.single().data)
    }

    private fun key(value: Int): SolanaPublicKey = SolanaPublicKey(ByteArray(32) { value.toByte() })
}
