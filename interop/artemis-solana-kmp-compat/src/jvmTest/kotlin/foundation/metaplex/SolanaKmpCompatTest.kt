/*
 * Smoke tests for the foundation.metaplex.* drop-in shim.
 */
package foundation.metaplex

import foundation.metaplex.amount.Lamports
import foundation.metaplex.amount.SOL
import foundation.metaplex.base58.Base58
import foundation.metaplex.rpc.Commitment
import foundation.metaplex.rpc.RpcRequestConfiguration
import foundation.metaplex.signer.HotSigner
import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanaeddsa.SolanaEddsa
import foundation.metaplex.solana.AccountMeta
import foundation.metaplex.solana.SolanaTransactionBuilder
import foundation.metaplex.solana.TransactionInstruction
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SolanaKmpCompatTest {

    @Test
    fun `PublicKey round-trips through base58`() {
        val base58 = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val pk = PublicKey(base58)
        assertEquals(base58, pk.toBase58())
        assertEquals(32, pk.toByteArray().size)
    }

    @Test
    fun `findProgramAddress returns valid PDA`() {
        val programId = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val seed = "test-seed".toByteArray()
        val pda = PublicKey.findProgramAddress(listOf(seed), programId)
        assertEquals(32, pda.publicKey.toByteArray().size)
        assertTrue(pda.nonce in 0..255)
    }

    @Test
    fun `Base58 round-trip`() {
        val bytes = ByteArray(32) { (it * 3).toByte() }
        assertContentEquals(bytes, Base58.decode(Base58.encode(bytes)))
    }

    @Test
    fun `Keypair generate, sign and verify`() {
        val keypair = Keypair.generate()
        assertEquals(32, keypair.secretKey.size)
        val message = "hello artemis".toByteArray()
        val signature = keypair.sign(message)
        assertEquals(64, signature.size)
        assertTrue(SolanaEddsa.verify(keypair.publicKey.toByteArray(), signature, message))
    }

    @Test
    fun `SolanaEddsa sign-verify matches Keypair sign-verify`() {
        val keypair = Keypair.generate()
        val message = "artemis eddsa".toByteArray()
        val sigA = keypair.sign(message)
        val sigB = SolanaEddsa.sign(keypair.secretKey, message)
        assertContentEquals(sigA, sigB)
    }

    @Test
    fun `Amount helpers produce canonical Solana amounts`() {
        val lamports = Lamports(1_500_000_000L)
        assertEquals(1_500_000_000L, lamports.basisPoints)
        assertEquals("SOL", lamports.identifier)
        assertEquals(9, lamports.decimals)
        assertEquals(1.5, lamports.toDecimal(), 1e-9)

        val sol = SOL(2)
        assertEquals(2_000_000_000L, sol.basisPoints)
    }

    @Test
    fun `RpcRequestConfiguration defaults`() {
        val cfg = RpcRequestConfiguration()
        assertEquals(Commitment.CONFIRMED, cfg.commitment)
        assertEquals("base64", cfg.encoding)
    }

    @Test
    fun `HotSigner delegates to keypair`() = runBlocking {
        val keypair = Keypair.generate()
        val signer = HotSigner(keypair)
        assertEquals(keypair.publicKey, signer.publicKey)
        val msg = "x".toByteArray()
        assertContentEquals(keypair.sign(msg), signer.sign(msg))
    }

    @Test
    fun `SolanaTransactionBuilder assembles and signs`() = runBlocking {
        val feePayer = Keypair.generate()
        val signer = HotSigner(feePayer)
        val recipient = Keypair.generate().publicKey

        val instruction = TransactionInstruction(
            programId = PublicKey("11111111111111111111111111111111"),
            keys = listOf(
                AccountMeta(signer.publicKey, isSigner = true, isWritable = true),
                AccountMeta(recipient, isSigner = false, isWritable = true)
            ),
            data = ByteArray(12).also { it[0] = 2 }
        )

        val tx = SolanaTransactionBuilder()
            .addInstruction(instruction)
            .setRecentBlockHash("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
            .setSigners(listOf(signer))
            .build()

        val serialized = tx.serialize()
        assertNotNull(serialized.bytes)
        assertTrue(serialized.bytes.isNotEmpty())
    }

    @Test
    fun `PublicKey equality is structural`() {
        val a = PublicKey("11111111111111111111111111111111")
        val b = PublicKey("11111111111111111111111111111111")
        val c = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
