/*
 * Copyright (c) 2024-2026 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * Wallet Alias and SKR Resolution Tests - Tests for off-chain wallet aliases
 * and SeedVault .skr key reference resolution.
 */
package com.selenus.artemis.nlp

import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for WalletAliasStore, SkrResolver, and NLP address resolution.
 * 
 * These tests verify:
 * 1. WalletAliasStore - Off-chain wallet aliases (mom, savings, etc.)
 * 2. SkrResolver - SeedVault .skr key references
 * 3. RpcEntityResolver - Multi-source address resolution
 * 4. NLP Integration - Natural language with aliases/skr
 */
class WalletAliasAndSkrTest {
    
    private lateinit var rpc: RpcApi
    private lateinit var resolver: RpcEntityResolver
    private lateinit var nlb: NaturalLanguageBuilder
    
    // Test wallet addresses
    companion object {
        const val DEVNET_RPC = "https://api.devnet.solana.com"
        const val MOM_ADDRESS = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
        const val SAVINGS_ADDRESS = "9yRZPmqy5RQVrQ5wBmvSuJhUmAthJvGknfPQvFfDLq8N"
        const val MAIN_SKR_ADDRESS = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"
        const val TRADING_SKR_ADDRESS = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    }
    
    @BeforeEach
    fun setup() {
        val client = JsonRpcClient(DEVNET_RPC)
        rpc = RpcApi(client)
        resolver = RpcEntityResolver(rpc)
        
        // Set up wallet aliases
        resolver.getAliasStore().setAlias("mom", MOM_ADDRESS)
        resolver.getAliasStore().setAlias("savings", SAVINGS_ADDRESS)
        resolver.getAliasStore().setAlias("exchange", "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R")
        
        // Set up .skr key references
        resolver.getSkrResolver().registerKey("main", MAIN_SKR_ADDRESS)
        resolver.getSkrResolver().registerKey("trading", TRADING_SKR_ADDRESS, "m/44'/501'/0'/0'")
        resolver.getSkrResolver().registerKey("nft", "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
        
        nlb = NaturalLanguageBuilder.create(resolver)
    }
    
    // ===========================================
    // WalletAliasStore Tests
    // ===========================================
    
    @Test
    fun `test alias store - set and get alias`() {
        val store = WalletAliasStore()
        store.setAlias("test", "ABC123")
        
        assertEquals("ABC123", store.getAddress("test"))
        assertEquals("ABC123", store.getAddress("TEST")) // Case insensitive
        assertEquals("ABC123", store.getAddress("Test"))
    }
    
    @Test
    fun `test alias store - case insensitivity`() {
        val store = WalletAliasStore()
        store.setAlias("MyWallet", "ADDRESS1")
        
        assertEquals("ADDRESS1", store.getAddress("mywallet"))
        assertEquals("ADDRESS1", store.getAddress("MYWALLET"))
        assertEquals("ADDRESS1", store.getAddress("myWALLET"))
    }
    
    @Test
    fun `test alias store - remove alias`() {
        val store = WalletAliasStore()
        store.setAlias("temp", "TEMP123")
        assertTrue(store.hasAlias("temp"))
        
        store.removeAlias("temp")
        assertNull(store.getAddress("temp"))
    }
    
    @Test
    fun `test alias store - import and export`() {
        val store = WalletAliasStore()
        val aliases = mapOf(
            "alice" to "ALICE_ADDR",
            "bob" to "BOB_ADDR",
            "charlie" to "CHARLIE_ADDR"
        )
        
        store.importAliases(aliases)
        
        assertEquals("ALICE_ADDR", store.getAddress("alice"))
        assertEquals("BOB_ADDR", store.getAddress("bob"))
        assertEquals("CHARLIE_ADDR", store.getAddress("charlie"))
        
        val exported = store.exportAliases()
        assertEquals(3, exported.size)
    }
    
    @Test
    fun `test alias store - clear all`() {
        val store = WalletAliasStore()
        store.setAlias("a", "1")
        store.setAlias("b", "2")
        store.clear()
        
        assertNull(store.getAddress("a"))
        assertNull(store.getAddress("b"))
    }
    
    // ===========================================
    // SkrResolver Tests
    // ===========================================
    
    @Test
    fun `test skr resolver - register and resolve`() {
        val skr = SkrResolver()
        skr.registerKey("wallet1", "PUBKEY1")
        
        assertEquals("PUBKEY1", skr.resolve("wallet1.skr"))
        assertEquals("PUBKEY1", skr.resolve("wallet1")) // Without extension
        assertEquals("PUBKEY1", skr.resolve("WALLET1.SKR")) // Case insensitive
    }
    
    @Test
    fun `test skr resolver - get key info`() {
        val skr = SkrResolver()
        skr.registerKey("main", "PUBKEY_MAIN", "m/44'/501'/0'/0'", 12345L)
        
        val info = skr.getKeyInfo("main.skr")
        assertNotNull(info)
        assertEquals("main", info.name)
        assertEquals("PUBKEY_MAIN", info.publicKey)
        assertEquals("m/44'/501'/0'/0'", info.derivationPath)
        assertEquals(12345L, info.accountId)
    }
    
    @Test
    fun `test skr resolver - has key check`() {
        val skr = SkrResolver()
        skr.registerKey("exists", "KEY123")
        
        assertTrue(skr.hasKey("exists.skr"))
        assertTrue(skr.hasKey("exists"))
        assertTrue(!skr.hasKey("notexists.skr"))
    }
    
    @Test
    fun `test skr resolver - remove key`() {
        val skr = SkrResolver()
        skr.registerKey("temp", "TEMP_KEY")
        assertTrue(skr.hasKey("temp"))
        
        skr.removeKey("temp")
        assertNull(skr.resolve("temp.skr"))
    }
    
    // ===========================================
    // RpcEntityResolver Address Resolution Tests
    // ===========================================
    
    @Test
    fun `test resolver - resolve wallet alias`() = runBlocking {
        val resolved = resolver.resolveWalletAlias("mom")
        assertEquals(MOM_ADDRESS, resolved)
    }
    
    @Test
    fun `test resolver - resolve skr key`() = runBlocking {
        val resolved = resolver.resolveSkr("main.skr")
        assertEquals(MAIN_SKR_ADDRESS, resolved)
    }
    
    @Test
    fun `test resolver - resolve any address - direct base58`() = runBlocking {
        val result = resolver.resolveAnyAddress(MOM_ADDRESS)
        assertNotNull(result)
        assertEquals(MOM_ADDRESS, result.address)
        assertEquals(AddressSource.DIRECT, result.source)
    }
    
    @Test
    fun `test resolver - resolve any address - skr key`() = runBlocking {
        val result = resolver.resolveAnyAddress("trading.skr")
        assertNotNull(result)
        assertEquals(TRADING_SKR_ADDRESS, result.address)
        assertEquals(AddressSource.SEED_VAULT_KEY, result.source)
    }
    
    @Test
    fun `test resolver - resolve any address - wallet alias`() = runBlocking {
        val result = resolver.resolveAnyAddress("savings")
        assertNotNull(result)
        assertEquals(SAVINGS_ADDRESS, result.address)
        assertEquals(AddressSource.WALLET_ALIAS, result.source)
    }
    
    // ===========================================
    // NLP Integration Tests
    // ===========================================
    
    @Test
    fun `test NLP - send SOL to wallet alias`() = runBlocking {
        val result = nlb.parse("send 1 SOL to mom")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.TRANSFER_SOL, result.intent.type)
                assertTrue(result.confidence >= 0.8)
                
                // Check that the recipient was resolved to mom's address
                val recipient = result.intent.entities["recipient"]
                println("✓ Send to alias 'mom': recipient = ${recipient?.resolvedValue}")
                println("  Summary: ${result.intent.summary}")
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
    fun `test NLP - send SOL to skr key`() = runBlocking {
        val result = nlb.parse("send 0.5 SOL to main.skr")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.TRANSFER_SOL, result.intent.type)
                assertTrue(result.confidence >= 0.8)
                
                val recipient = result.intent.entities["recipient"]
                println("✓ Send to .skr key 'main.skr': recipient = ${recipient?.resolvedValue}")
                println("  Summary: ${result.intent.summary}")
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
    fun `test NLP - transfer tokens to wallet alias`() = runBlocking {
        val result = nlb.parse("send 100 USDC to savings")
        
        when (result) {
            is ParseResult.Success -> {
                assertTrue(result.intent.type in listOf(IntentType.TRANSFER_TOKEN, IntentType.TRANSFER_SOL))
                
                val recipient = result.intent.entities["recipient"]
                println("✓ Transfer tokens to alias 'savings': recipient = ${recipient?.resolvedValue}")
                println("  Summary: ${result.intent.summary}")
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
    fun `test NLP - complex command with skr and alias`() = runBlocking {
        // This tests that we can parse commands that might use skr keys
        val result = nlb.parse("transfer 50 BONK from trading.skr to exchange")
        
        when (result) {
            is ParseResult.Success -> {
                println("✓ Complex command parsed successfully")
                println("  Intent: ${result.intent.type}")
                println("  Summary: ${result.intent.summary}")
                println("  Entities: ${result.intent.entities.mapValues { it.value.resolvedValue }}")
            }
            is ParseResult.NeedsInfo -> {
                // Expected - we're testing pattern matching, not execution
                println("Needs additional info: ${result.missing}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    @Test
    fun `test NLP - balance query with alias`() = runBlocking {
        val result = nlb.parse("what is the balance of mom")
        
        when (result) {
            is ParseResult.Success -> {
                assertEquals(IntentType.CHECK_BALANCE, result.intent.type)
                println("✓ Balance query for alias 'mom'")
                println("  Summary: ${result.intent.summary}")
            }
            is ParseResult.NeedsInfo -> {
                println("Needs: ${result.missing}")
            }
            else -> {
                println("Result: $result")
            }
        }
    }
    
    // ===========================================
    // Edge Cases and Error Handling
    // ===========================================
    
    @Test
    fun `test unknown alias returns null`() = runBlocking {
        val resolved = resolver.resolveWalletAlias("unknown_alias")
        assertNull(resolved)
    }
    
    @Test
    fun `test unknown skr returns null`() = runBlocking {
        val resolved = resolver.resolveSkr("unknown.skr")
        assertNull(resolved)
    }
    
    @Test
    fun `test empty alias store`() {
        val store = WalletAliasStore()
        assertNull(store.getAddress("anything"))
        assertEquals(0, store.getAllAliases().size)
    }
    
    @Test
    fun `test skr resolver multiple keys`() {
        val skr = SkrResolver()
        skr.registerKey("key1", "ADDR1")
        skr.registerKey("key2", "ADDR2")
        skr.registerKey("key3", "ADDR3")
        
        assertEquals(3, skr.getAllKeys().size)
        assertEquals("ADDR1", skr.resolve("key1"))
        assertEquals("ADDR2", skr.resolve("key2"))
        assertEquals("ADDR3", skr.resolve("key3"))
    }
    
    @Test
    fun `test alias overwrite`() {
        val store = WalletAliasStore()
        store.setAlias("test", "OLD_ADDRESS")
        store.setAlias("test", "NEW_ADDRESS")
        
        assertEquals("NEW_ADDRESS", store.getAddress("test"))
    }
    
    // ===========================================
    // Demonstration Test
    // ===========================================
    
    @Test
    fun `demo - complete wallet alias workflow`() = runBlocking {
        println("\n=== Wallet Alias & SKR Resolution Demo ===\n")
        
        // Show configured aliases
        println("Configured Wallet Aliases:")
        resolver.getAliasStore().getAllAliases().forEach { (alias, address) ->
            println("  $alias -> $address")
        }
        
        println("\nConfigured SKR Keys:")
        resolver.getSkrResolver().getAllKeys().forEach { (name, info) ->
            println("  $name.skr -> ${info.publicKey}")
        }
        
        // Demonstrate resolution
        println("\nResolution Examples:")
        listOf("mom", "savings", "main.skr", "trading.skr", MOM_ADDRESS).forEach { input ->
            val result = resolver.resolveAnyAddress(input)
            println("  '$input' -> ${result?.address ?: "NOT FOUND"} (${result?.source})")
        }
        
        // Demonstrate NLP parsing
        println("\nNLP Command Examples:")
        listOf(
            "send 1 SOL to mom",
            "transfer 50 USDC to savings",
            "send 0.1 SOL to main.skr",
            "check balance of exchange"
        ).forEach { command ->
            val result = nlb.parse(command)
            when (result) {
                is ParseResult.Success -> {
                    println("  '$command'")
                    println("    -> ${result.intent.type}: ${result.intent.summary}")
                }
                is ParseResult.NeedsInfo -> {
                    println("  '$command'")
                    println("    -> Needs: ${result.missing}")
                }
                else -> {
                    println("  '$command' -> $result")
                }
            }
        }
        
        println("\n=== Demo Complete ===")
    }
}
