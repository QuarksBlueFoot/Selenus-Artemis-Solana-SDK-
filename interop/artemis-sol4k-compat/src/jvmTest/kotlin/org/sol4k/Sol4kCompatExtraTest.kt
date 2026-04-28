package org.sol4k

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sol4k.exception.RpcException
import org.sol4k.exception.SerializationException
import org.sol4k.instruction.Token2022TransferInstruction as AliasedT22
import org.sol4k.instruction.CreateAssociatedToken2022AccountInstruction as AliasedAta22
import org.sol4k.instruction.TokenTransferInstruction as AliasedTokenTransfer

/**
 * Coverage for the sol4k-compat parity gaps closed in this pass:
 *  - Token-2022 instruction classes (Token2022TransferInstruction,
 *    CreateAssociatedToken2022AccountInstruction, open
 *    TokenTransferInstruction base)
 *  - AccountMeta factory companions (signerAndWritable, writable, etc.)
 *  - PublicKey.readPubkey + valueOf
 *  - ProgramDerivedAddress.address field name (matches upstream)
 *  - RpcException + SerializationException data-class shape
 */
class Sol4kCompatExtraTest {

    private val owner = PublicKey("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
    private val mint = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val recipient = PublicKey("8wXqWbqXXxYV2pKLvqkpWLngRbkqGSRgvgr5q5d1n6Bq")

    // ─── Token-2022 instructions ──────────────────────────────────

    @Test
    fun `Token2022TransferInstruction targets TOKEN_2022 program`() {
        val ix = Token2022TransferInstruction(
            from = owner,
            to = recipient,
            mint = mint,
            owner = owner,
            amount = 1_500L,
            decimals = 6
        )
        assertEquals(Constants.TOKEN_2022_PROGRAM_ID, ix.programId)
        // transferChecked layout: source (writable), mint (readonly),
        // destination (writable), owner (signer).
        assertEquals(4, ix.keys.size)
        assertTrue(ix.keys[0].writable)
        assertTrue(ix.keys[3].signer)
        // Op code for transferChecked is 12.
        assertEquals(12, ix.data[0].toInt())
    }

    @Test
    fun `Token2022TransferInstruction is reachable through the open base class`() {
        val ix: TokenTransferInstruction = Token2022TransferInstruction(
            from = owner,
            to = recipient,
            mint = mint,
            owner = owner,
            amount = 100L,
            decimals = 0
        )
        // Inherited fields from the base.
        assertEquals(owner, ix.from)
        assertEquals(recipient, ix.to)
        assertEquals(100L, ix.amount)
    }

    @Test
    fun `Token2022TransferInstruction includes multisig co-signers`() {
        val coSigner = PublicKey("4Nd1mWdWaTKuKWqJzBpzRxqi3qg8e8szqnGqkqGgYs5N")
        val ix = Token2022TransferInstruction(
            from = owner,
            to = recipient,
            mint = mint,
            owner = owner,
            amount = 1L,
            decimals = 0,
            signers = listOf(coSigner)
        )
        // Base 4 accounts + 1 co-signer.
        assertEquals(5, ix.keys.size)
        assertEquals(coSigner, ix.keys[4].publicKey)
        assertTrue(ix.keys[4].signer)
    }

    @Test
    fun `CreateAssociatedToken2022AccountInstruction wires the Token-2022 program`() {
        val ata = CreateAssociatedToken2022AccountInstruction.deriveAddress(owner, mint)
        val ix = CreateAssociatedToken2022AccountInstruction(
            payer = owner,
            associatedToken = ata,
            owner = owner,
            mint = mint
        )
        assertEquals(Constants.ASSOCIATED_TOKEN_PROGRAM_ID, ix.programId)
        assertEquals(0, ix.data.size)
        assertEquals(6, ix.keys.size)
        // Last account in the create flow is the token program; for
        // Token-2022 ATAs that must be TOKEN_2022, not the standard
        // SPL token program.
        assertEquals(Constants.TOKEN_2022_PROGRAM_ID, ix.keys[5].publicKey)
    }

    @Test
    fun `Token-2022 ATA address differs from standard SPL ATA`() {
        val splAta = PublicKey.findProgramDerivedAddress(owner, mint).address
        val t22Ata = CreateAssociatedToken2022AccountInstruction.deriveAddress(owner, mint)
        // ATA derivation uses the token program id as a seed, so the
        // two addresses MUST differ for the same (owner, mint) pair.
        assertNotEquals(splAta, t22Ata)
    }

    @Test
    fun `findProgramDerivedAddress accepts an explicit token program id`() {
        val splAta = PublicKey.findProgramDerivedAddress(owner, mint).address
        val t22Ata = PublicKey.findProgramDerivedAddress(
            holderAddress = owner,
            tokenMintAddress = mint,
            programId = Constants.TOKEN_2022_PROGRAM_ID
        ).address
        assertNotEquals(splAta, t22Ata)
        // Cross-check with the dedicated Token-2022 helper.
        assertEquals(t22Ata, CreateAssociatedToken2022AccountInstruction.deriveAddress(owner, mint))
    }

    // ─── instruction package aliases ──────────────────────────────

    @Test
    fun `org sol4k instruction aliases resolve`() {
        val viaAlias: AliasedTokenTransfer = AliasedT22(owner, recipient, mint, owner, 1L, 0)
        val viaCanonical: TokenTransferInstruction = viaAlias
        assertEquals(1L, viaCanonical.amount)

        val ataIx: AliasedAta22 = AliasedAta22(
            payer = owner,
            associatedToken = CreateAssociatedToken2022AccountInstruction.deriveAddress(owner, mint),
            owner = owner,
            mint = mint
        )
        assertEquals(Constants.ASSOCIATED_TOKEN_PROGRAM_ID, ataIx.programId)
    }

    // ─── AccountMeta companion factories ──────────────────────────

    @Test
    fun `AccountMeta companion factories match upstream shape`() {
        val sw = AccountMeta.signerAndWritable(owner)
        assertTrue(sw.signer); assertTrue(sw.writable)
        val w = AccountMeta.writable(owner)
        assertTrue(!w.signer); assertTrue(w.writable)
        val s = AccountMeta.signer(owner)
        assertTrue(s.signer); assertTrue(!s.writable)
        val r = AccountMeta.readonly(owner)
        assertTrue(!r.signer); assertTrue(!r.writable)
    }

    // ─── PublicKey companion helpers ──────────────────────────────

    @Test
    fun `PublicKey readPubkey decodes a 32-byte slice`() {
        val source = ByteArray(40) { it.toByte() }
        val pk = PublicKey.readPubkey(source, offset = 4)
        assertEquals(32, pk.bytes().size)
        // First byte at offset 4 == 4.
        assertEquals(4, pk.bytes()[0].toInt())
    }

    @Test
    fun `PublicKey valueOf is equivalent to the constructor`() {
        val a = PublicKey.valueOf("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
        val b = PublicKey("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
        assertEquals(a, b)
    }

    // ─── ProgramDerivedAddress.address field name ─────────────────

    @Test
    fun `ProgramDerivedAddress exposes the address field`() {
        val pda = PublicKey.findProgramAddress(listOf(owner), Constants.TOKEN_PROGRAM_ID)
        // Upstream-shape access path; this is the parity-relevant field.
        assertEquals(32, pda.address.bytes().size)
        assertTrue(pda.nonce in 0..255)
        // Deprecated alias still resolves.
        @Suppress("DEPRECATION")
        assertEquals(pda.address, pda.publicKey)
    }

    // ─── Exception data-class shape ───────────────────────────────

    @Test
    fun `RpcException is a data class with code message rawResponse`() {
        val raw = """{"error":{"code":-32603,"message":"oops"}}"""
        val e = RpcException(code = -32603, message = "oops", rawResponse = raw)
        assertEquals(-32603, e.code)
        assertEquals("oops", e.message)
        assertEquals(raw, e.rawResponse)
        // Data class affordances: copy + componentN destructuring.
        val (c, m, r) = e
        assertEquals(-32603, c); assertEquals("oops", m); assertEquals(raw, r)
        val mutated = e.copy(message = "different")
        assertEquals("different", mutated.message)
    }

    @Test
    fun `RpcException backwards-compat ctor still chains a cause`() {
        val cause = IllegalStateException("downstream")
        val e = RpcException(code = null, message = "wrap", cause = cause)
        assertEquals(0, e.code)
        assertSame(cause, e.cause)
    }

    @Test
    fun `SerializationException is a data class with message`() {
        val e = SerializationException("bad bytes")
        assertEquals("bad bytes", e.message)
        val (m) = e
        assertEquals("bad bytes", m)
        val mutated = e.copy(message = "different")
        assertEquals("different", mutated.message)
    }
}
