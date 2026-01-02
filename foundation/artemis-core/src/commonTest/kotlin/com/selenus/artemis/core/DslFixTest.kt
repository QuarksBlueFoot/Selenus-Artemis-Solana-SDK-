package com.selenus.artemis.core

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for core DSL fixes: writePubkey implementation and Pubkey-typed overloads.
 */
class DslFixTest {

    private val testPubkeyBase58 = "11111111111111111111111111111111"

    @Test
    fun `writePubkey from base58 string works`() {
        val builder = InstructionDataBuilder()
        builder.writePubkey(testPubkeyBase58)
        val bytes = builder.build()

        assertEquals(32, bytes.size)
        val expected = Base58.decode(testPubkeyBase58)
        assertTrue(bytes.contentEquals(expected))
    }

    @Test
    fun `writePubkey from Pubkey object works`() {
        val pubkey = Pubkey.fromBase58(testPubkeyBase58)
        val builder = InstructionDataBuilder()
        builder.writePubkey(pubkey)
        val bytes = builder.build()

        assertEquals(32, bytes.size)
        assertTrue(bytes.contentEquals(pubkey.bytes))
    }

    @Test
    fun `writePubkey rejects invalid base58`() {
        val builder = InstructionDataBuilder()
        assertFailsWith<IllegalArgumentException> {
            builder.writePubkey("short")
        }
    }

    @Test
    fun `AccountMeta Pubkey overloads work`() {
        val pubkey = Pubkey.fromBase58(testPubkeyBase58)

        val sw = AccountMeta.signerWritable(pubkey)
        assertEquals(testPubkeyBase58, sw.pubkey)
        assertTrue(sw.isSigner)
        assertTrue(sw.isWritable)

        val s = AccountMeta.signer(pubkey)
        assertTrue(s.isSigner)
        assertTrue(!s.isWritable)

        val w = AccountMeta.writable(pubkey)
        assertTrue(!w.isSigner)
        assertTrue(w.isWritable)

        val r = AccountMeta.readonly(pubkey)
        assertTrue(!r.isSigner)
        assertTrue(!r.isWritable)
    }

    @Test
    fun `AccountListBuilder Pubkey overloads work`() {
        val pubkey = Pubkey.fromBase58(testPubkeyBase58)

        val accts = accounts {
            signerWritable(pubkey)
            writable(pubkey)
            readonly(pubkey)
            signer(pubkey)
            program(pubkey)
        }

        assertEquals(5, accts.size)
        assertTrue(accts[0].isSigner && accts[0].isWritable)
        assertTrue(!accts[1].isSigner && accts[1].isWritable)
        assertTrue(!accts[2].isSigner && !accts[2].isWritable)
        assertTrue(accts[3].isSigner && !accts[3].isWritable)
        assertTrue(!accts[4].isSigner && !accts[4].isWritable)
    }

    @Test
    fun `instruction DSL with data builder`() {
        val programId = testPubkeyBase58

        val ix = instruction(programId) {
            accounts {
                signerWritable(testPubkeyBase58)
            }
            data {
                writeU8(1)
                writeU64(1000L)
                writePubkey(testPubkeyBase58)
            }
        }

        assertEquals(programId, ix.programId)
        assertEquals(1, ix.accounts.size)
        // 1 byte (u8) + 8 bytes (u64) + 32 bytes (pubkey) = 41
        assertEquals(41, ix.data.size)
    }
}
