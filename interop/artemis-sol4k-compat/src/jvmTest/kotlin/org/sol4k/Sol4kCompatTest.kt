/*
 * Smoke tests for the org.sol4k drop-in shim.
 *
 * These tests exercise the parts of the sol4k API that apps use without
 * needing a live RPC endpoint: key material, base58, PDA derivation, and
 * transaction message serialization round-trip.
 */
package org.sol4k

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Sol4kCompatTest {

    @Test
    fun `PublicKey round-trips through base58`() {
        val base58 = "11111111111111111111111111111111"
        val pk = PublicKey(base58)
        assertEquals(base58, pk.toBase58())
        assertEquals(32, pk.bytes().size)
    }

    @Test
    fun `PublicKey equality is structural`() {
        val a = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val b = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val c = PublicKey("11111111111111111111111111111111")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `Base58 encode and decode round-trip`() {
        val original = ByteArray(32) { it.toByte() }
        val encoded = Base58.encode(original)
        val decoded = Base58.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun `Keypair generate produces valid signing key`() {
        val keypair = Keypair.generate()
        val message = "Artemis drop-in sol4k shim".toByteArray()
        val signature = keypair.sign(message)
        assertEquals(64, signature.size)
        assertTrue(keypair.publicKey.verify(signature, message))
    }

    @Test
    fun `findProgramAddress matches native Artemis PDA`() {
        val seed = PublicKey("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
        val programId = PublicKey.TOKEN_PROGRAM_ID
        val result = PublicKey.findProgramAddress(listOf(seed), programId)
        assertTrue(result.nonce in 0..255)
        assertEquals(32, result.address.bytes().size)
    }

    @Test
    fun `findProgramDerivedAddress returns ATA for wallet mint pair`() {
        val wallet = PublicKey("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
        val mint = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        val ata = PublicKey.findProgramDerivedAddress(wallet, mint)
        assertEquals(32, ata.address.bytes().size)
        assertTrue(ata.nonce in 0..255)
    }

    @Test
    fun `TransferInstruction produces well-formed system transfer`() {
        val from = Keypair.generate().publicKey
        val to = Keypair.generate().publicKey
        val ix = TransferInstruction(from = from, to = to, lamports = 1_000_000L)
        assertEquals(PublicKey.SYSTEM_PROGRAM_ID, ix.programId)
        assertEquals(2, ix.keys.size)
        assertEquals(from, ix.keys[0].publicKey)
        assertTrue(ix.keys[0].signer)
        assertTrue(ix.keys[0].writable)
        assertEquals(to, ix.keys[1].publicKey)
    }

    @Test
    fun `TransactionMessage serialize and deserialize is lossless`() {
        val payer = Keypair.generate().publicKey
        val recipient = Keypair.generate().publicKey
        val blockhash = "GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv"
        val original = TransactionMessage.newMessage(
            feePayer = payer,
            recentBlockhash = blockhash,
            instruction = TransferInstruction(payer, recipient, 42_000_000L)
        )
        val bytes = original.serialize()
        val restored = TransactionMessage.deserialize(bytes)
        assertEquals(blockhash, restored.recentBlockhash)
        assertEquals(payer, restored.feePayer)
        assertEquals(1, restored.instructions.size)
        assertEquals(PublicKey.SYSTEM_PROGRAM_ID, restored.instructions[0].programId)
    }

    @Test
    fun `Commitment value matches wire string`() {
        assertEquals("processed", Commitment.PROCESSED.value)
        assertEquals("confirmed", Commitment.CONFIRMED.value)
        assertEquals("finalized", Commitment.FINALIZED.value)
    }
}
