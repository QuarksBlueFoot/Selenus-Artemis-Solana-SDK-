package com.selenus.artemis.vtx

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

/**
 * Tests for v0 signature ordering correctness.
 *
 * The critical invariant: signatures MUST be placed at the index matching
 * the signer's position in the message's staticAccountKeys, regardless of
 * the order signers are passed to sign().
 */
class SignatureOrderingTest {

    private val testBlockhash = "11111111111111111111111111111111"

    @Test
    fun `sign maps signatures to correct signer slots`() {
        val payer = Keypair.generate()
        val recipient = Keypair.generate().publicKey

        val ix = SystemProgram.transfer(payer.publicKey, recipient, 1000L)
        val result = V0MessageCompiler.compile(payer, testBlockhash, listOf(ix))
        val vtx = VersionedTransaction(result.message)

        vtx.sign(listOf(payer))

        // Signature count must equal numRequiredSignatures
        assertEquals(result.message.header.numRequiredSignatures, vtx.signatures.size)
        // Each signature must be 64 bytes
        assertTrue(vtx.signatures.all { it.size == 64 })
        // The single signature must not be all zeros (it was actually signed)
        assertTrue(vtx.signatures[0].any { it.toInt() != 0 })
    }

    @Test
    fun `sign with shuffled signers still places signatures correctly`() {
        val payer = Keypair.generate()
        val cosigner = Keypair.generate()

        // Instruction requiring both payer and cosigner as signers
        val ix = Instruction(
            SystemProgram.PROGRAM_ID,
            listOf(
                AccountMeta.signerAndWritable(payer.publicKey),
                AccountMeta.signerAndWritable(cosigner.publicKey)
            ),
            ByteArray(0)
        )

        val result = V0MessageCompiler.compile(payer, testBlockhash, listOf(ix))
        val msg = result.message

        // Get the required signer order from the message
        val requiredKeys = msg.staticAccountKeys.take(msg.header.numRequiredSignatures)

        // Sign in forward order
        val vtx1 = VersionedTransaction(msg)
        vtx1.sign(listOf(payer, cosigner))

        // Sign in REVERSE order - must produce identical signature placement
        val vtx2 = VersionedTransaction(msg)
        vtx2.sign(listOf(cosigner, payer))

        // Both transactions must have the same signature at each slot
        assertEquals(vtx1.signatures.size, vtx2.signatures.size)
        for (i in vtx1.signatures.indices) {
            assertTrue(
                vtx1.signatures[i].contentEquals(vtx2.signatures[i]),
                "Signature at index $i differs - signer order affected placement"
            )
        }
    }

    @Test
    fun `sign rejects signer not in message`() {
        val payer = Keypair.generate()
        val outsider = Keypair.generate()
        val recipient = Keypair.generate().publicKey

        val ix = SystemProgram.transfer(payer.publicKey, recipient, 1000L)
        val result = V0MessageCompiler.compile(payer, testBlockhash, listOf(ix))
        val vtx = VersionedTransaction(result.message)

        assertFailsWith<IllegalArgumentException> {
            vtx.sign(listOf(outsider))
        }
    }

    @Test
    fun `compileAndSign produces valid signed transaction`() {
        val payer = Keypair.generate()
        val recipient = Keypair.generate().publicKey

        val ix = SystemProgram.transfer(payer.publicKey, recipient, 1000L)
        val vtx = V0MessageCompiler.compileAndSign(
            payer, emptyList(), testBlockhash, listOf(ix)
        )

        assertNotNull(vtx)
        assertEquals(vtx.message.header.numRequiredSignatures, vtx.signatures.size)
        assertTrue(vtx.signatures[0].any { it.toInt() != 0 })
    }

    @Test
    fun `compileAndSign with additional signers`() {
        val payer = Keypair.generate()
        val cosigner = Keypair.generate()

        val ix = Instruction(
            SystemProgram.PROGRAM_ID,
            listOf(
                AccountMeta.signerAndWritable(payer.publicKey),
                AccountMeta.signerAndWritable(cosigner.publicKey)
            ),
            ByteArray(0)
        )

        val vtx = V0MessageCompiler.compileAndSign(
            payer, listOf(cosigner), testBlockhash, listOf(ix)
        )

        assertEquals(2, vtx.signatures.size)
        // Both signatures must be non-zero
        assertTrue(vtx.signatures.all { sig -> sig.any { it.toInt() != 0 } })
    }

    @Test
    fun `serialize and deserialize roundtrip preserves signatures`() {
        val payer = Keypair.generate()
        val recipient = Keypair.generate().publicKey

        val ix = SystemProgram.transfer(payer.publicKey, recipient, 1000L)
        val vtx = V0MessageCompiler.compileAndSign(
            payer, emptyList(), testBlockhash, listOf(ix)
        )

        val bytes = vtx.serialize()
        val restored = VersionedTransaction.deserialize(bytes)

        assertEquals(vtx.signatures.size, restored.signatures.size)
        for (i in vtx.signatures.indices) {
            assertTrue(vtx.signatures[i].contentEquals(restored.signatures[i]))
        }
    }

    @Test
    fun `addSignature places signature at correct index`() {
        val payer = Keypair.generate()
        val recipient = Keypair.generate().publicKey

        val ix = SystemProgram.transfer(payer.publicKey, recipient, 1000L)
        val result = V0MessageCompiler.compile(payer, testBlockhash, listOf(ix))
        val vtx = VersionedTransaction(result.message)

        // Manually sign and add via addSignature
        val msgBytes = result.message.serialize()
        val sig = payer.sign(msgBytes)
        vtx.addSignature(payer.publicKey, sig)

        assertEquals(result.message.header.numRequiredSignatures, vtx.signatures.size)
        assertTrue(vtx.signatures[0].contentEquals(sig))
    }
}
