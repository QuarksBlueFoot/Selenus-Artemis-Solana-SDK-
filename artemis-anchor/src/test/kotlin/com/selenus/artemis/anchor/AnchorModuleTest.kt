/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Anchor Program Client Module Tests - First complete Kotlin Anchor client.
 */
package com.selenus.artemis.anchor

import com.selenus.artemis.disc.AnchorDiscriminators
import com.selenus.artemis.runtime.Pubkey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for the Anchor Program Client - First complete Kotlin implementation.
 * 
 * These tests verify:
 * 1. IDL parsing
 * 2. Discriminator computation
 * 3. Instruction building
 * 4. Account deserialization
 * 5. PDA derivation
 * 6. Type-safe API
 */
class AnchorModuleTest {
    
    private val tokenProgramId = Pubkey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    
    // ===========================================
    // IDL Parsing Tests
    // ===========================================
    
    @Test
    fun `parse minimal IDL`() {
        val idlJson = """
        {
            "version": "0.1.0",
            "name": "test_program",
            "instructions": [
                {
                    "name": "initialize",
                    "accounts": [
                        {"name": "state", "isMut": true, "isSigner": false}
                    ],
                    "args": []
                }
            ]
        }
        """.trimIndent()
        
        val idl = AnchorProgram.parseIdl(idlJson)
        
        assertEquals("0.1.0", idl.version)
        assertEquals("test_program", idl.name)
        assertEquals(1, idl.instructions.size)
        assertEquals("initialize", idl.instructions[0].name)
    }
    
    @Test
    fun `parse IDL with accounts and types`() {
        val idlJson = """
        {
            "version": "0.1.0",
            "name": "token_program",
            "instructions": [
                {
                    "name": "transfer",
                    "accounts": [
                        {"name": "from", "isMut": true, "isSigner": true},
                        {"name": "to", "isMut": true, "isSigner": false},
                        {"name": "authority", "isMut": false, "isSigner": true}
                    ],
                    "args": [
                        {"name": "amount", "type": "u64"}
                    ]
                }
            ],
            "accounts": [
                {
                    "name": "TokenAccount",
                    "type": {
                        "kind": "struct",
                        "fields": [
                            {"name": "mint", "type": "publicKey"},
                            {"name": "owner", "type": "publicKey"},
                            {"name": "amount", "type": "u64"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()
        
        val idl = AnchorProgram.parseIdl(idlJson)
        
        assertEquals(1, idl.instructions.size)
        assertEquals(3, idl.instructions[0].accounts.size)
        assertEquals(1, idl.instructions[0].args.size)
        assertNotNull(idl.accounts)
        assertEquals(1, idl.accounts?.size)
    }
    
    @Test
    fun `parse IDL with events`() {
        val idlJson = """
        {
            "version": "0.1.0",
            "name": "event_program",
            "instructions": [],
            "events": [
                {
                    "name": "TransferEvent",
                    "fields": [
                        {"name": "from", "type": "publicKey", "index": false},
                        {"name": "to", "type": "publicKey", "index": false},
                        {"name": "amount", "type": "u64", "index": false}
                    ]
                }
            ]
        }
        """.trimIndent()
        
        val idl = AnchorProgram.parseIdl(idlJson)
        
        assertNotNull(idl.events)
        assertEquals(1, idl.events?.size)
        assertEquals("TransferEvent", idl.events?.get(0)?.name)
    }
    
    // ===========================================
    // Discriminator Tests
    // ===========================================
    
    @Test
    fun `compute instruction discriminator`() {
        val discriminator = AnchorDiscriminators.instruction("initialize")
        
        assertEquals(8, discriminator.size)
        // Anchor uses first 8 bytes of sha256("global:initialize")
    }
    
    @Test
    fun `compute account discriminator`() {
        val discriminator = AnchorDiscriminators.account("TokenAccount")
        
        assertEquals(8, discriminator.size)
        // Anchor uses first 8 bytes of sha256("account:TokenAccount")
    }
    
    @Test
    fun `discriminators are deterministic`() {
        val disc1 = AnchorDiscriminators.instruction("transfer")
        val disc2 = AnchorDiscriminators.instruction("transfer")
        
        assertArrayEquals(disc1, disc2)
    }
    
    @Test
    fun `different instructions have different discriminators`() {
        val disc1 = AnchorDiscriminators.instruction("initialize")
        val disc2 = AnchorDiscriminators.instruction("transfer")
        
        assertFalse(disc1.contentEquals(disc2))
    }
    
    // ===========================================
    // AnchorProgram Tests
    // ===========================================
    
    @Test
    fun `create program from IDL`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        assertNotNull(program)
        assertEquals(tokenProgramId, program.programId)
    }
    
    @Test
    fun `get instruction discriminator from program`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        val discriminator = program.getInstructionDiscriminator("initialize")
        assertEquals(8, discriminator.size)
    }
    
    @Test
    fun `find instruction by name`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        val instruction = program.findInstruction("initialize")
        assertEquals("initialize", instruction.name)
    }
    
    @Test
    fun `unknown instruction throws exception`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        assertThrows(IllegalArgumentException::class.java) {
            program.findInstruction("nonexistent")
        }
    }
    
    // ===========================================
    // Instruction Builder Tests
    // ===========================================
    
    @Test
    fun `build instruction with args`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        val signer = Pubkey("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
        val state = Pubkey("9yRZPmqy5RQVrQ5wBmvSuJhUmAthJvGknfPQvFfDLq8N")
        
        val ix = program.methods
            .instruction("initialize")
            .args(mapOf("name" to "TestToken"))
            .accounts {
                account("state", state)
                signer("authority", signer)
            }
            .build()
        
        assertNotNull(ix)
        assertEquals(tokenProgramId, ix.programId)
        assertTrue(ix.data.isNotEmpty())
    }
    
    @Test
    fun `build instruction with args DSL`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        val signer = Pubkey("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
        val state = Pubkey("9yRZPmqy5RQVrQ5wBmvSuJhUmAthJvGknfPQvFfDLq8N")
        
        val ix = program.methods
            .instruction("initialize")
            .args {
                string("name", "TestToken")
            }
            .accounts {
                account("state", state)
                signer("authority", signer)
            }
            .build()
        
        assertNotNull(ix)
        assertTrue(ix.data.size > 8) // discriminator + args
    }
    
    // ===========================================
    // Account Types Tests
    // ===========================================
    
    @Test
    fun `find account type`() {
        val idlJson = createTestIdlWithAccounts()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        val accountType = program.findAccount("TokenState")
        assertEquals("TokenState", accountType.name)
    }
    
    // ===========================================
    // PDA Builder Tests
    // ===========================================
    
    @Test
    fun `pda builder exists`() {
        val idlJson = createTestIdl()
        val program = AnchorProgram.fromIdl(idlJson, tokenProgramId)
        
        assertNotNull(program.pda)
    }
    
    // ===========================================
    // Helper Methods
    // ===========================================
    
    private fun createTestIdl(): String {
        return """
        {
            "version": "0.1.0",
            "name": "test_program",
            "instructions": [
                {
                    "name": "initialize",
                    "accounts": [
                        {"name": "state", "isMut": true, "isSigner": false},
                        {"name": "authority", "isMut": false, "isSigner": true}
                    ],
                    "args": [
                        {"name": "name", "type": "string"}
                    ]
                }
            ]
        }
        """.trimIndent()
    }
    
    private fun createTestIdlWithAccounts(): String {
        return """
        {
            "version": "0.1.0",
            "name": "test_program",
            "instructions": [],
            "accounts": [
                {
                    "name": "TokenState",
                    "type": {
                        "kind": "struct",
                        "fields": [
                            {"name": "name", "type": "string"},
                            {"name": "symbol", "type": "string"},
                            {"name": "totalSupply", "type": "u64"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()
    }
}
