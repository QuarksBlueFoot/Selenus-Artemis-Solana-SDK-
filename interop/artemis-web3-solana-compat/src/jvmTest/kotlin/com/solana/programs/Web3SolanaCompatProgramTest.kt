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
