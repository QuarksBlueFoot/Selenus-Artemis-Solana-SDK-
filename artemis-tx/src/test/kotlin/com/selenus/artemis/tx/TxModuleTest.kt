package com.selenus.artemis.tx

import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pubkey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-tx module.
 * Tests Message, Transaction, AccountMeta, and related types.
 */
class TxModuleTest {

    // ===== MessageHeader Tests =====

    @Test
    fun testMessageHeaderCreation() {
        val header = MessageHeader(
            numRequiredSignatures = 2,
            numReadonlySigned = 1,
            numReadonlyUnsigned = 3
        )
        
        assertEquals(2, header.numRequiredSignatures)
        assertEquals(1, header.numReadonlySigned)
        assertEquals(3, header.numReadonlyUnsigned)
    }

    @Test
    fun testMessageHeaderEquality() {
        val header1 = MessageHeader(1, 0, 2)
        val header2 = MessageHeader(1, 0, 2)
        
        assertEquals(header1, header2)
    }

    // ===== AccountMeta Tests =====

    @Test
    fun testAccountMetaSignerWritable() {
        val pubkey = Pubkey(ByteArray(32) { 1 })
        val meta = AccountMeta(pubkey, isSigner = true, isWritable = true)
        
        assertTrue(meta.isSigner)
        assertTrue(meta.isWritable)
        assertEquals(pubkey, meta.pubkey)
    }

    @Test
    fun testAccountMetaReadonly() {
        val pubkey = Pubkey(ByteArray(32) { 2 })
        val meta = AccountMeta(pubkey, isSigner = false, isWritable = false)
        
        assertEquals(false, meta.isSigner)
        assertEquals(false, meta.isWritable)
    }

    // ===== Instruction Tests =====

    @Test
    fun testInstructionCreation() {
        val programId = Pubkey(ByteArray(32) { 0 })
        val account = Pubkey(ByteArray(32) { 1 })
        val data = byteArrayOf(0, 1, 2, 3)
        
        val instruction = Instruction(
            programId = programId,
            accounts = listOf(AccountMeta(account, isSigner = true, isWritable = true)),
            data = data
        )
        
        assertEquals(programId, instruction.programId)
        assertEquals(1, instruction.accounts.size)
        assertEquals(4, instruction.data.size)
    }

    @Test
    fun testInstructionWithMultipleAccounts() {
        val programId = Pubkey(ByteArray(32) { 0 })
        val accounts = (1..5).map { 
            AccountMeta(Pubkey(ByteArray(32) { it.toByte() }), isSigner = false, isWritable = it % 2 == 0)
        }
        
        val instruction = Instruction(programId, accounts, byteArrayOf())
        
        assertEquals(5, instruction.accounts.size)
        assertEquals(false, instruction.accounts[0].isWritable)
        assertTrue(instruction.accounts[1].isWritable)
    }

    // ===== CompiledInstruction Tests =====

    @Test
    fun testCompiledInstructionSerialization() {
        val compiled = CompiledInstruction(
            programIdIndex = 2,
            accountIndexes = byteArrayOf(0, 1),
            data = byteArrayOf(1, 2, 3)
        )
        
        val serialized = compiled.serialize()
        
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())
        assertEquals(2, serialized[0].toInt()) // program index first
    }

    @Test
    fun testCompiledInstructionEmptyData() {
        val compiled = CompiledInstruction(
            programIdIndex = 0,
            accountIndexes = byteArrayOf(0),
            data = byteArrayOf()
        )
        
        val serialized = compiled.serialize()
        assertNotNull(serialized)
    }

    // ===== Message Tests =====

    @Test
    fun testMessageSerialization() {
        val header = MessageHeader(1, 0, 1)
        val accountKeys = listOf(
            Pubkey(ByteArray(32) { 1 }),
            Pubkey(ByteArray(32) { 2 })
        )
        // Use a valid base58 blockhash (32 bytes encoded)
        val blockhash = "11111111111111111111111111111111"
        
        val message = Message(
            header = header,
            accountKeys = accountKeys,
            recentBlockhash = blockhash,
            instructions = emptyList()
        )
        
        val serialized = message.serialize()
        
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())
        // Header bytes first
        assertEquals(1, serialized[0].toInt())
        assertEquals(0, serialized[1].toInt())
        assertEquals(1, serialized[2].toInt())
    }

    @Test
    fun testMessageWithInstructions() {
        val header = MessageHeader(1, 0, 0)
        val accountKeys = listOf(
            Pubkey(ByteArray(32) { 1 }),
            Pubkey(ByteArray(32) { 0 }) // program
        )
        val blockhash = "11111111111111111111111111111111"
        
        val instruction = CompiledInstruction(
            programIdIndex = 1,
            accountIndexes = byteArrayOf(0),
            data = byteArrayOf(2)
        )
        
        val message = Message(header, accountKeys, blockhash, listOf(instruction))
        val serialized = message.serialize()
        
        assertNotNull(serialized)
        assertTrue(serialized.size > 64) // At least header + 2 keys
    }

    // ===== Transaction Tests =====

    @Test
    fun testTransactionCreation() {
        val tx = Transaction()
        
        assertEquals(null, tx.feePayer)
        assertEquals(null, tx.recentBlockhash)
        assertTrue(tx.instructions.isEmpty())
    }

    @Test
    fun testTransactionAddInstruction() {
        val tx = Transaction()
        val programId = Pubkey(ByteArray(32) { 0 })
        val instruction = Instruction(programId, emptyList(), byteArrayOf())
        
        tx.addInstruction(instruction)
        
        assertEquals(1, tx.instructions.size)
    }

    @Test
    fun testTransactionCompileMessage() {
        val feePayer = Pubkey(ByteArray(32) { 1 })
        val programId = Pubkey(ByteArray(32) { 0 })
        
        val tx = Transaction(
            feePayer = feePayer,
            recentBlockhash = "11111111111111111111111111111111"
        )
        
        val instruction = Instruction(
            programId = programId,
            accounts = listOf(AccountMeta(feePayer, isSigner = true, isWritable = true)),
            data = byteArrayOf(1, 2, 3)
        )
        tx.addInstruction(instruction)
        
        val message = tx.compileMessage()
        
        assertNotNull(message)
        assertEquals(1, message.header.numRequiredSignatures)
        assertTrue(message.accountKeys.isNotEmpty())
        assertEquals(1, message.instructions.size)
    }

    @Test
    fun testTransactionFeePayerFirst() {
        val feePayer = Pubkey(ByteArray(32) { 1 })
        val otherAccount = Pubkey(ByteArray(32) { 2 })
        val programId = Pubkey(ByteArray(32) { 0 })
        
        val tx = Transaction(
            feePayer = feePayer,
            recentBlockhash = "11111111111111111111111111111111"
        )
        
        val instruction = Instruction(
            programId = programId,
            accounts = listOf(
                AccountMeta(otherAccount, isSigner = false, isWritable = true),
                AccountMeta(feePayer, isSigner = true, isWritable = true)
            ),
            data = byteArrayOf()
        )
        tx.addInstruction(instruction)
        
        val message = tx.compileMessage()
        
        // Fee payer should always be first
        assertEquals(feePayer, message.accountKeys[0])
    }

    // ===== ShortVec Tests =====

    @Test
    fun testShortVecEncodeSingleByte() {
        val encoded = ShortVec.encodeLen(127)
        assertEquals(1, encoded.size)
        assertEquals(127, encoded[0].toInt() and 0xFF)
    }

    @Test
    fun testShortVecEncodeMultiByte() {
        val encoded = ShortVec.encodeLen(128)
        assertTrue(encoded.size > 1)
    }

    @Test
    fun testShortVecEncodeZero() {
        val encoded = ShortVec.encodeLen(0)
        assertEquals(1, encoded.size)
        assertEquals(0, encoded[0].toInt())
    }

    // ===== SignedTransaction Tests =====

    @Test
    fun testSignedTransactionToBase64() {
        val signatures = listOf(ByteArray(64) { 0 })
        val message = ByteArray(100) { 1 }
        
        val signed = SignedTransaction(signatures, message)
        val base64 = signed.toBase64()
        
        assertNotNull(base64)
        assertTrue(base64.isNotEmpty())
    }

    @Test
    fun testSignedTransactionSerialize() {
        val signatures = listOf(ByteArray(64) { 0 })
        val message = ByteArray(50) { 1 }
        
        val signed = SignedTransaction(signatures, message)
        val bytes = signed.serialize()
        
        assertNotNull(bytes)
        // 1 byte for sig count + 64 bytes signature + 50 bytes message
        assertTrue(bytes.size >= 115)
    }
}
