package com.selenus.artemis.token2022

import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferHookProgramTest {
    private val hookProgram = Pubkey(ByteArray(32) { 9 })
    private val source = Pubkey(ByteArray(32) { 1 })
    private val mint = Pubkey(ByteArray(32) { 2 })
    private val destination = Pubkey(ByteArray(32) { 3 })
    private val authority = Pubkey(ByteArray(32) { 4 })
    private val extraAccount = Pubkey(ByteArray(32) { 5 })
    private val signer = Pubkey(ByteArray(32) { 6 })

    @Test
    fun `Token-2022 transfer hook mint config uses canonical extension prefix`() {
        val initialize = AdvancedToken2022Extensions.initializeTransferHook(
            mint = mint,
            authority = authority,
            hookProgramId = hookProgram
        )
        val update = AdvancedToken2022Extensions.updateTransferHook(
            mint = mint,
            authority = authority,
            newProgramId = hookProgram,
            signers = listOf(signer)
        )

        assertEquals(Token2022Program.PROGRAM_ID, initialize.programId)
        assertEquals(66, initialize.data.size)
        assertEquals(36, initialize.data[0].toInt() and 0xFF)
        assertEquals(0, initialize.data[1].toInt() and 0xFF)
        assertContentEquals(authority.bytes, initialize.data.copyOfRange(2, 34))
        assertContentEquals(hookProgram.bytes, initialize.data.copyOfRange(34, 66))
        assertTrue(initialize.accounts.single().isWritable)

        assertEquals(34, update.data.size)
        assertEquals(36, update.data[0].toInt() and 0xFF)
        assertEquals(1, update.data[1].toInt() and 0xFF)
        assertContentEquals(hookProgram.bytes, update.data.copyOfRange(2, 34))
        assertEquals(listOf(mint, authority, signer), update.accounts.map { it.pubkey })
        assertFalse(update.accounts[1].isSigner)
        assertTrue(update.accounts[2].isSigner)
    }

    @Test
    fun `transfer hook execute instruction uses SPL discriminator and base account order`() {
        val ix = TransferHookProgram.execute(
            hookProgramId = hookProgram,
            source = source,
            mint = mint,
            destination = destination,
            authority = authority,
            amount = 1_234_567L
        )

        assertEquals(hookProgram, ix.programId)
        assertContentEquals(byteArrayOf(0x69, 0x25, 0x65, 0xC5.toByte(), 0x4B, 0xFB.toByte(), 0x66, 0x1A), ix.data.copyOfRange(0, 8))
        val amount = ix.data.copyOfRange(8, 16).foldIndexed(0L) { index, acc, byte ->
            acc or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        assertEquals(1_234_567L, amount)
        assertEquals(
            listOf(source, mint, destination, authority, TransferHookProgram.extraAccountMetasAddress(mint, hookProgram)),
            ix.accounts.map { it.pubkey }
        )
        assertTrue(ix.accounts.none { it.isSigner || it.isWritable })
    }

    @Test
    fun `validation PDA and transfer extra accounts follow transfer-hook interface order`() {
        val expected = Pda.findProgramAddress(
            listOf(TransferHookProgram.EXTRA_ACCOUNT_METAS_SEED.encodeToByteArray(), mint.bytes),
            hookProgram
        )
        val derived = TransferHookProgram.extraAccountMetasAddressWithBump(mint, hookProgram)
        val extra = AccountMeta(extraAccount, isSigner = true, isWritable = true)
        val execute = TransferHookProgram.executeWithExtraAccountMetas(
            hookProgramId = hookProgram,
            source = source,
            mint = mint,
            destination = destination,
            authority = authority,
            amount = 42L,
            additionalAccounts = listOf(extra)
        )

        assertEquals(expected.address, derived.address)
        assertEquals(expected.bump, derived.bump)
        assertEquals(listOf(source, mint, destination, authority, derived.address, extraAccount), execute.accounts.map { it.pubkey })

        val transferExtras = TransferHookProgram.extraAccountsForTransfer(
            hookProgramId = hookProgram,
            mint = mint,
            resolvedExtraAccounts = listOf(extra)
        )
        assertEquals(listOf(extraAccount, hookProgram, derived.address), transferExtras.map { it.pubkey })
    }

    @Test
    fun `initialize and update extra account metas pack list view bytes`() {
        val staticMeta = TransferHookProgram.ExtraAccountMeta.static(extraAccount, isSigner = false, isWritable = true)
        val pdaMeta = TransferHookProgram.ExtraAccountMeta.pda(
            seeds = listOf(
                TransferHookProgram.Seed.Literal("seed-prefix".encodeToByteArray()),
                TransferHookProgram.Seed.InstructionData(index = 8, length = 8),
                TransferHookProgram.Seed.AccountKey(index = 2)
            ),
            isWritable = true
        )
        val pubkeyDataMeta = TransferHookProgram.ExtraAccountMeta.pubkeyData(
            TransferHookProgram.PubkeyDataSource.AccountData(accountIndex = 4, dataIndex = 12)
        )
        val validation = TransferHookProgram.extraAccountMetasAddress(mint, hookProgram)
        val initialize = TransferHookProgram.initializeExtraAccountMetaList(
            hookProgramId = hookProgram,
            mint = mint,
            authority = authority,
            extraAccountMetas = listOf(staticMeta, pdaMeta, pubkeyDataMeta),
            validationAccount = validation
        )
        val update = TransferHookProgram.updateExtraAccountMetaList(
            hookProgramId = hookProgram,
            mint = mint,
            authority = authority,
            extraAccountMetas = listOf(staticMeta),
            validationAccount = validation
        )

        assertContentEquals(byteArrayOf(0x2B, 0x22, 0x0D, 0x31, 0xA7.toByte(), 0x58, 0xEB.toByte(), 0xEB.toByte()), initialize.data.copyOfRange(0, 8))
        assertEquals(3, initialize.data[8].toInt() and 0xFF)
        assertEquals(listOf(validation, mint, authority), initialize.accounts.take(3).map { it.pubkey })
        assertTrue(initialize.accounts[0].isWritable)
        assertTrue(initialize.accounts[2].isSigner)

        assertContentEquals(byteArrayOf(0x9D.toByte(), 0x69, 0x2A, 0x92.toByte(), 0x66, 0x55, 0xF1.toByte(), 0xAE.toByte()), update.data.copyOfRange(0, 8))
        assertEquals(1, update.data[8].toInt() and 0xFF)
        assertEquals(3, pdaMeta.toBytes()[1 + 2 + "seed-prefix".length + 3].toInt() and 0xFF)
        assertEquals(2, pubkeyDataMeta.discriminator)
        assertContentEquals(byteArrayOf(2, 4, 12), pubkeyDataMeta.addressConfig.copyOfRange(0, 3))
    }

    @Test
    fun `extra account meta list view is backed by original bytes`() {
        val meta = TransferHookProgram.ExtraAccountMeta.static(extraAccount, isSigner = true, isWritable = false)
        val listData = TransferHookProgram.packExtraAccountMetaList(listOf(meta))
        val view = TransferHookProgram.decodeExtraAccountMetaListView(listData)

        assertEquals(1, view.count)
        assertEquals(0, view[0].discriminator)
        assertTrue(view[0].isSigner)
        assertFalse(view[0].isWritable)
        assertEquals(extraAccount.bytes[0], view[0].addressConfigByteAt(0))

        listData[5] = 0x7F
        assertEquals(0x7F, view[0].addressConfigByteAt(0).toInt() and 0xFF)
        assertEquals(extraAccount.bytes[0], meta.addressConfig[0])
    }
}