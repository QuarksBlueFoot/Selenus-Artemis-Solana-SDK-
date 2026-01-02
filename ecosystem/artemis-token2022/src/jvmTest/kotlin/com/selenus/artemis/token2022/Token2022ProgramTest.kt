package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Token2022ProgramTest {

    private val mint = Pubkey.fromBase58("So11111111111111111111111111111111111111112")
    private val source = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val dest = Pubkey.fromBase58("11111111111111111111111111111111")
    private val owner = Pubkey.fromBase58("7nYpDiwWkSM1bAbJhk2mXPUH1KU1U6rZxkrGi3YD2J1S")
    private val authority = owner

    @Test
    fun `transferChecked encodes discriminator 12 with amount and decimals`() {
        val ix = Token2022Program.transferChecked(
            source = source,
            mint = mint,
            destination = dest,
            owner = owner,
            amount = 1_000_000L,
            decimals = 6
        )
        assertEquals(Token2022Program.PROGRAM_ID, ix.programId)
        assertEquals(4, ix.accounts.size)
        // discriminator 12
        assertEquals(12, ix.data[0].toInt())
        // amount as u64 LE
        val amount = ix.data.sliceArray(1..8).foldIndexed(0L) { i, acc, b ->
            acc or ((b.toLong() and 0xFF) shl (i * 8))
        }
        assertEquals(1_000_000L, amount)
        // decimals
        assertEquals(6, ix.data[9].toInt())
    }

    @Test
    fun `burn encodes discriminator 8`() {
        val ix = Token2022Program.burn(
            account = source,
            mint = mint,
            owner = owner,
            amount = 500L
        )
        assertEquals(8, ix.data[0].toInt())
        assertEquals(3, ix.accounts.size)
        assertTrue(ix.accounts[2].isSigner) // owner must be signer
    }

    @Test
    fun `closeAccount encodes discriminator 9 with no data payload`() {
        val ix = Token2022Program.closeAccount(
            account = source,
            destination = dest,
            owner = owner
        )
        assertEquals(9, ix.data[0].toInt())
        assertEquals(1, ix.data.size) // no extra data
        assertEquals(3, ix.accounts.size)
    }

    @Test
    fun `withdrawWithheldTokensFromMint encodes discriminator 26`() {
        val ix = Token2022Program.withdrawWithheldTokensFromMint(
            mint = mint,
            destination = dest,
            withdrawWithheldAuthority = authority
        )
        assertEquals(26, ix.data[0].toInt())
        assertEquals(3, ix.accounts.size)
    }

    @Test
    fun `withdrawWithheldTokensFromAccounts includes source accounts`() {
        val sources = listOf(source, dest)
        val ix = Token2022Program.withdrawWithheldTokensFromAccounts(
            mint = mint,
            destination = dest,
            withdrawWithheldAuthority = authority,
            sourceAccounts = sources
        )
        assertEquals(27, ix.data[0].toInt())
        // 3 base accounts + 2 source accounts
        assertEquals(5, ix.accounts.size)
    }

    @Test
    fun `extensions decode method works with StateWithExtensions`() {
        val baseLen = Token2022StateLayout.MINT_BASE_LENGTH
        val paddingLen = Token2022StateLayout.BASE_ACCOUNT_LENGTH - baseLen
        // Build account data: base + padding + accountType(1=Mint) + TLV(type=1, len=0)
        val tlv = byteArrayOf(0x01, 0x00, 0x00, 0x00)
        val data = ByteArray(baseLen + paddingLen + 1 + tlv.size)
        data[Token2022StateLayout.BASE_ACCOUNT_LENGTH] = 0x01
        tlv.copyInto(data, Token2022StateLayout.BASE_ACCOUNT_LENGTH + 1)

        val decoded = Token2022Extensions.decode(data, baseLen)
        assertTrue(decoded != null)
        assertEquals(Token2022StateLayout.AccountType.Mint, decoded.accountType)
        assertEquals(1, decoded.entries.size)
    }
}
