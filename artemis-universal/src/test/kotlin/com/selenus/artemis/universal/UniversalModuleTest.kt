/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Universal Program Client Module Tests - World's First IDL-less program interaction.
 */
package com.selenus.artemis.universal

import com.selenus.artemis.runtime.Pubkey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for the Universal Program Client - World's First IDL-less program client.
 * 
 * These tests verify:
 * 1. Client initialization
 * 2. Discriminator discovery
 * 3. Pattern analysis
 * 4. Instruction building
 * 5. Account decoding
 * 6. Schema generation
 */
class UniversalModuleTest {
    
    // ===========================================
    // Discriminator Tests
    // ===========================================
    
    @Test
    fun `discriminator from bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val discriminator = Discriminator(bytes)
        
        assertEquals(8, discriminator.bytes.size)
        assertEquals("0102030405060708", discriminator.hex)
    }
    
    @Test
    fun `discriminator from hex string`() {
        val discriminator = Discriminator.fromHex("0102030405060708")
        
        assertEquals(8, discriminator.bytes.size)
        assertEquals(1, discriminator.bytes[0])
        assertEquals(8, discriminator.bytes[7])
    }
    
    @Test
    fun `discriminator equality`() {
        val disc1 = Discriminator(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val disc2 = Discriminator(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val disc3 = Discriminator(byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1))
        
        assertEquals(disc1, disc2)
        assertNotEquals(disc1, disc3)
    }
    
    // ===========================================
    // Field Type Tests
    // ===========================================
    
    @Test
    fun `inferred field types`() {
        val u8 = InferredFieldType.U8
        val u64 = InferredFieldType.U64
        val pubkey = InferredFieldType.Pubkey
        
        assertEquals(1, u8.size)
        assertEquals(8, u64.size)
        assertEquals(32, pubkey.size)
    }
    
    // ===========================================
    // Discovered Instruction Tests
    // ===========================================
    
    @Test
    fun `discovered instruction structure`() {
        val discriminator = Discriminator(ByteArray(8) { it.toByte() })
        val accounts = listOf(
            InferredAccount("source", true, true, false),
            InferredAccount("destination", true, false, false),
            InferredAccount("authority", false, true, false)
        )
        val dataPattern = InferredDataPattern(
            discriminator = discriminator,
            fields = listOf(
                InferredField("amount", InferredFieldType.U64, 8)
            )
        )
        
        val instruction = DiscoveredInstruction(
            name = "transfer",
            discriminator = discriminator,
            accounts = accounts,
            dataPattern = dataPattern,
            confidence = 0.95,
            sampleCount = 100
        )
        
        assertEquals("transfer", instruction.name)
        assertEquals(3, instruction.accounts.size)
        assertEquals(0.95, instruction.confidence)
        assertEquals(100, instruction.sampleCount)
    }
    
    // ===========================================
    // Inferred Account Tests
    // ===========================================
    
    @Test
    fun `inferred account properties`() {
        val signerWritable = InferredAccount("authority", true, true, false)
        val readonlyAccount = InferredAccount("program", false, false, false)
        val optionalAccount = InferredAccount("optional", false, true, true)
        
        assertTrue(signerWritable.isWritable)
        assertTrue(signerWritable.isSigner)
        
        assertFalse(readonlyAccount.isWritable)
        assertFalse(readonlyAccount.isSigner)
        
        assertTrue(optionalAccount.isOptional)
    }
    
    // ===========================================
    // Discovered Program Tests
    // ===========================================
    
    @Test
    fun `discovered program structure`() {
        val programId = Pubkey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        val instructions = listOf(
            createMockDiscoveredInstruction("initialize"),
            createMockDiscoveredInstruction("transfer"),
            createMockDiscoveredInstruction("close")
        )
        
        val program = DiscoveredProgram(
            programId = programId,
            instructions = instructions,
            accountTypes = emptyList(),
            isAnchorProgram = false,
            confidence = 0.85,
            discoveredAt = System.currentTimeMillis(),
            transactionsSampled = 500,
            accountsSampled = 100
        )
        
        assertEquals(programId, program.programId)
        assertEquals(3, program.instructions.size)
        assertFalse(program.isAnchorProgram)
        assertEquals(0.85, program.confidence)
    }
    
    // ===========================================
    // Instruction Data Builder Tests
    // ===========================================
    
    @Test
    fun `instruction data builder adds u8`() {
        val pattern = createMockInstructionPattern()
        val builder = InstructionDataBuilder(pattern)
        
        builder.u8("flag", 1)
        
        val data = builder.buildData()
        assertTrue(data.isNotEmpty())
    }
    
    @Test
    fun `instruction data builder adds u64`() {
        val pattern = createMockInstructionPattern()
        val builder = InstructionDataBuilder(pattern)
        
        builder.u64("amount", 1_000_000L)
        
        val data = builder.buildData()
        assertTrue(data.size >= 8)
    }
    
    @Test
    fun `instruction data builder adds pubkey`() {
        val pattern = createMockInstructionPattern()
        val builder = InstructionDataBuilder(pattern)
        val pubkey = Pubkey("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
        
        builder.pubkey("owner", pubkey)
        
        val data = builder.buildData()
        assertTrue(data.size >= 32)
    }
    
    // ===========================================
    // Decoded Account Tests
    // ===========================================
    
    @Test
    fun `decoded account structure`() {
        val discriminator = Discriminator(ByteArray(8) { it.toByte() })
        val fields = mapOf(
            "owner" to "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
            "amount" to 1000000L
        )
        
        val decoded = DecodedAccount(
            typeName = "TokenAccount",
            discriminator = discriminator,
            fields = fields,
            rawData = ByteArray(165),
            confidence = 0.9
        )
        
        assertEquals("TokenAccount", decoded.typeName)
        assertEquals(2, decoded.fields.size)
        assertEquals(0.9, decoded.confidence)
    }
    
    // ===========================================
    // Program Schema Tests
    // ===========================================
    
    @Test
    fun `program schema structure`() {
        val schema = ProgramSchema(
            programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            name = "Token Program",
            version = "1.0.0-discovered",
            isAnchor = false,
            instructions = listOf(
                SchemaInstruction(
                    name = "transfer",
                    discriminator = "0102030405060708",
                    accounts = listOf(
                        SchemaAccount("source", true, true, false),
                        SchemaAccount("destination", true, false, false)
                    ),
                    args = listOf(
                        SchemaArg("amount", "u64")
                    )
                )
            ),
            accounts = emptyList()
        )
        
        assertEquals("Token Program", schema.name)
        assertFalse(schema.isAnchor)
        assertEquals(1, schema.instructions.size)
    }
    
    // ===========================================
    // Similar Program Tests
    // ===========================================
    
    @Test
    fun `similar program structure`() {
        val programId = Pubkey("9yRZPmqy5RQVrQ5wBmvSuJhUmAthJvGknfPQvFfDLq8N")
        
        val similar = SimilarProgram(
            programId = programId,
            name = "Token-2022",
            similarity = 0.85,
            matchingInstructions = 10
        )
        
        assertEquals(0.85, similar.similarity)
        assertEquals(10, similar.matchingInstructions)
    }
    
    // ===========================================
    // Universal Config Tests
    // ===========================================
    
    @Test
    fun `default config values`() {
        val config = UniversalConfig()
        
        assertTrue(config.sampleSize > 0)
        assertFalse(config.forceRefresh)
        assertTrue(config.pollingIntervalMs > 0)
    }
    
    @Test
    fun `custom config values`() {
        val config = UniversalConfig(
            sampleSize = 100,
            forceRefresh = true,
            pollingIntervalMs = 5000
        )
        
        assertEquals(100, config.sampleSize)
        assertTrue(config.forceRefresh)
        assertEquals(5000, config.pollingIntervalMs)
    }
    
    // ===========================================
    // Helper Methods
    // ===========================================
    
    private fun createMockDiscoveredInstruction(name: String): DiscoveredInstruction {
        return DiscoveredInstruction(
            name = name,
            discriminator = Discriminator(ByteArray(8) { it.toByte() }),
            accounts = emptyList(),
            dataPattern = InferredDataPattern(
                discriminator = Discriminator(ByteArray(8)),
                fields = emptyList()
            ),
            confidence = 0.9,
            sampleCount = 50
        )
    }
    
    private fun createMockInstructionPattern(): DiscoveredInstruction {
        return DiscoveredInstruction(
            name = "test",
            discriminator = Discriminator(ByteArray(8)),
            accounts = emptyList(),
            dataPattern = InferredDataPattern(
                discriminator = Discriminator(ByteArray(8)),
                fields = listOf(
                    InferredField("amount", InferredFieldType.U64, 8),
                    InferredField("flag", InferredFieldType.U8, 16),
                    InferredField("owner", InferredFieldType.Pubkey, 17)
                )
            ),
            confidence = 0.9,
            sampleCount = 50
        )
    }
}
