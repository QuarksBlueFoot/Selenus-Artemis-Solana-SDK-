/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * NLP DevNet Test - Interactive testing of NLP transaction building on devnet.
 */
package com.selenus.artemis.nlp

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.wallet.LocalSignerWalletAdapter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * DevNet tests for NLP transaction building.
 * 
 * These tests interact with the real Solana devnet.
 * Run with: ./gradlew :artemis-nlp:test --tests "NlpDevnetTest"
 */
class NlpDevnetTest {
    
    private lateinit var rpc: RpcApi
    private lateinit var resolver: RpcEntityResolver
    private lateinit var nlb: NaturalLanguageBuilder
    
    // Devnet test wallet (fund with `solana airdrop 2` before running)
    private val testKeypair = Keypair.generate()
    
    companion object {
        const val DEVNET_RPC = "https://api.devnet.solana.com"
    }
    
    @BeforeEach
    fun setup() {
        val client = JsonRpcClient(DEVNET_RPC)
        rpc = RpcApi(client)
        resolver = RpcEntityResolver(rpc)
        nlb = NaturalLanguageBuilder.create(resolver)
    }
    
    // ===========================================
    // Pattern Matching Tests (No RPC needed)
    // ===========================================
    
    @Test
    fun `test transfer SOL pattern matching`() = runBlocking {
        val result = nlb.parse("send 1 SOL to 7xKX...")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.TRANSFER_SOL, result.intent.type)
                assertTrue(result.confidence >= 0.8)
                println("✓ Transfer SOL: ${result.intent.summary}")
            }
            is ParseResult.NeedsInfo -> {
                println("Needs: ${result.missing}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test swap pattern matching`() = runBlocking {
        val result = nlb.parse("swap 100 USDC for SOL")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.SWAP, result.intent.type)
                println("✓ Swap: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test stake pattern matching`() = runBlocking {
        val result = nlb.parse("stake 10 SOL")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.STAKE, result.intent.type)
                println("✓ Stake: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test airdrop pattern matching`() = runBlocking {
        val result = nlb.parse("airdrop 1 SOL")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.AIRDROP, result.intent.type)
                println("✓ Airdrop: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test balance check pattern matching`() = runBlocking {
        val result = nlb.parse("check my balance")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.CHECK_BALANCE, result.intent.type)
                println("✓ Balance: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test wrap SOL pattern matching`() = runBlocking {
        val result = nlb.parse("wrap 1 SOL")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.WRAP_SOL, result.intent.type)
                println("✓ Wrap SOL: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test buy pattern matching`() = runBlocking {
        val result = nlb.parse("buy 1000 BONK")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.SWAP, result.intent.type)
                println("✓ Buy: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test create token account pattern`() = runBlocking {
        val result = nlb.parse("create account for USDC")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.CREATE_ATA, result.intent.type)
                println("✓ Create ATA: ${result.intent.summary}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    // ===========================================
    // Token Resolution Tests
    // ===========================================
    
    @Test
    fun `test token symbol resolution`() = runBlocking {
        val usdcMint = resolver.resolveTokenSymbol("USDC")
        assertNotNull(usdcMint)
        assertEquals("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", usdcMint)
        println("✓ USDC resolved to: $usdcMint")
        
        val solMint = resolver.resolveTokenSymbol("SOL")
        assertNotNull(solMint)
        println("✓ SOL resolved to: $solMint")
        
        val bonkMint = resolver.resolveTokenSymbol("BONK")
        assertNotNull(bonkMint)
        println("✓ BONK resolved to: $bonkMint")
    }
    
    @Test
    fun `test program resolution`() = runBlocking {
        val tokenProgram = resolver.resolveProgramName("token")
        assertNotNull(tokenProgram)
        assertEquals("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", tokenProgram)
        println("✓ Token program: $tokenProgram")
        
        val jupiter = resolver.resolveProgramName("jupiter")
        assertNotNull(jupiter)
        println("✓ Jupiter program: $jupiter")
    }
    
    // ===========================================
    // RPC Integration Tests (Requires DevNet)
    // ===========================================
    
    @Test
    @Disabled("Requires devnet SOL - enable when testing manually")
    fun `test balance check on devnet`() = runBlocking {
        val balance = resolver.getBalance(testKeypair.publicKey.toBase58())
        println("✓ Balance: $balance SOL")
        assertTrue(balance >= 0)
    }
    
    @Test
    @Disabled("Requires devnet airdrop - enable when testing manually")
    fun `test devnet airdrop and transfer`() = runBlocking {
        // Request airdrop
        val signature = rpc.requestAirdrop(testKeypair.publicKey.toBase58(), 1_000_000_000)
        println("Airdrop signature: $signature")
        
        // Wait for confirmation
        kotlinx.coroutines.delay(2000)
        
        // Check balance
        val balance = resolver.getBalance(testKeypair.publicKey.toBase58())
        println("Balance after airdrop: $balance SOL")
        assertTrue(balance >= 0.9)
    }
    
    // ===========================================
    // NLP Executor Tests (Simulation Only)
    // ===========================================
    
    @Test
    @Disabled("Requires wallet setup - enable when testing manually")
    fun `test NLP executor simulation`() = runBlocking {
        val wallet = LocalSignerWalletAdapter(testKeypair)
        val executor = NlpExecutor(rpc, wallet, NlpExecutorConfig(cluster = "devnet"))
        
        // Parse command
        val parseResult = nlb.parse("send 0.001 SOL to ${Keypair.generate().publicKey.toBase58()}")
        
        if (parseResult is ParseResult.Success) {
            // Simulate (don't actually send)
            val simResult = executor.simulate(parseResult.intent)
            
            when (simResult) {
                is SimulationResult.Success -> {
                    println("✓ Simulation succeeded")
                    println("  Units consumed: ${simResult.unitsConsumed}")
                    println("  Estimated fee: ${simResult.estimatedFee} lamports")
                }
                is SimulationResult.Failed -> {
                    println("✗ Simulation failed: ${simResult.error}")
                }
            }
        }
    }
    
    // ===========================================
    // Suggestion Tests
    // ===========================================
    
    @Test
    fun `test get suggestions`() {
        val suggestions = nlb.getSuggestions("send")
        assertTrue(suggestions.isNotEmpty())
        
        println("Suggestions for 'send':")
        for (suggestion in suggestions) {
            println("  - ${suggestion.template}")
            println("    ${suggestion.description}")
        }
    }
    
    @Test
    fun `test get all supported types`() {
        val types = nlb.getSupportedTypes()
        assertTrue(types.isNotEmpty())
        
        println("Supported transaction types:")
        for (type in types) {
            println("  ${type.type}: ${type.description}")
            println("    Template: ${type.template}")
            println("    Examples: ${type.examples.take(2).joinToString(", ")}")
        }
    }
    
    // ===========================================
    // Natural Language Variations
    // ===========================================
    
    @Test
    fun `test natural language variations for transfer`() = runBlocking {
        val phrases = listOf(
            "send 1 SOL to alice.sol",
            "transfer 1 SOL to bob.sol",
            "pay 1 SOL to charlie.sol",
            "give 1 SOL to dave.sol"
        )
        
        for (phrase in phrases) {
            val result = nlb.parse(phrase)
            when (result) {
                is ParseResult.Success -> {
                    println("✓ '$phrase' -> ${result.intent.type}")
                }
                else -> {
                    println("? '$phrase' -> $result")
                }
            }
        }
    }
    
    @Test
    fun `test natural language variations for swap`() = runBlocking {
        val phrases = listOf(
            "swap 100 USDC for SOL",
            "exchange 50 SOL for USDC",
            "convert 1 SOL to BONK",
            "buy 1000 BONK",
            "sell 100 JUP"
        )
        
        for (phrase in phrases) {
            val result = nlb.parse(phrase)
            when (result) {
                is ParseResult.Success -> {
                    println("✓ '$phrase' -> ${result.intent.type}")
                }
                else -> {
                    println("? '$phrase' -> $result")
                }
            }
        }
    }
    
    @Test  
    fun `test natural language variations for balance`() = runBlocking {
        val phrases = listOf(
            "check balance",
            "show my balance",
            "what's my balance",
            "how much SOL do I have"
        )
        
        for (phrase in phrases) {
            val result = nlb.parse(phrase)
            when (result) {
                is ParseResult.Success -> {
                    println("✓ '$phrase' -> ${result.intent.type}")
                }
                else -> {
                    println("? '$phrase' -> $result")
                }
            }
        }
    }
}

// Note: RpcApi already has requestAirdrop method - no extension needed
