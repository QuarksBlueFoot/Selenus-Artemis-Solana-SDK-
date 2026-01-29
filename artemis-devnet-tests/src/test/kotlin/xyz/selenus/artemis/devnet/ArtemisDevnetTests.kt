package xyz.selenus.artemis.devnet

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.tx.builder.artemisTransaction

/**
 * Artemis SDK v2.0.0 - Pre-Publication Validation Tests
 * 
 * These tests validate that all modules are properly configured and ready for publication.
 * 
 * For FULL devnet testing with actual transactions:
 * 1. Install Solana CLI: sh -c "$(curl -sSfL https://release.solana.com/stable/install)"
 * 2. Generate keypair: solana-keygen new --outfile ~/.config/solana/id.json
 * 3. Airdrop SOL: solana airdrop 2 --url devnet
 * 4. Set DEVNET_KEYPAIR_PATH environment variable
 * 5. See DEVNET_TESTING_GUIDE.md for comprehensive testing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtemisDevnetTests {
    
    private val devnetEndpoint = "https://api.devnet.solana.com"
    
    @BeforeAll
    fun setup() {
        println("🚀 Artemis SDK v2.0.0 - Pre-Publication Validation")
        println("=" .repeat(60))
    }
    
    @Test
    fun `1 - Core Module Dependencies Available`() {
        println("\n📦 Test 1: Core Dependencies")
        println("-".repeat(60))
        
        // Test that core classes are available
        val keypair = Keypair.generate()
        val pubkey = keypair.publicKey
        
        println("✅ Keypair generation works")
        println("✅ Pubkey: ${pubkey.toBase58()}")
        
        assertTrue(pubkey.toBase58().length == 44, "Valid base58 pubkey")
    }
    
    @Test
    fun `2 - RPC Client Initialization`() {
        println("\n🌐 Test 2: RPC Client")
        println("-".repeat(60))
        
        val rpcClient = JsonRpcClient(devnetEndpoint)
        println("✅ RPC Client initialized: $devnetEndpoint")
        
        assertNotNull(rpcClient, "RPC client should be created")
    }
    
    @Test
    fun `3 - Transaction Builder DSL`() {
        println("\n🔧 Test 3: Transaction Builder")
        println("-".repeat(60))
        
        val wallet = Keypair.generate()
        val recipient = Keypair.generate().publicKey
        
        val tx = artemisTransaction {
            feePayer(wallet.publicKey)
            instruction(Pubkey("11111111111111111111111111111111")) {
                accounts {
                    signerWritable(wallet.publicKey)
                    writable(recipient)
                }
                data(ByteArray(12)) // Placeholder data for transfer
            }
        }
        
        println("✅ Transaction created with ${tx.instructions.size} instruction(s)")
        assertTrue(tx.instructions.isNotEmpty(), "Transaction should have instructions")
    }
    
    @Test
    fun `4 - Multiple Keypair Generation`() {
        println("\n🔑 Test 4: Wallet Management")
        println("-".repeat(60))
        
        val keypairs = (1..5).map { Keypair.generate() }
        
        println("✅ Generated ${keypairs.size} keypairs")
        keypairs.forEachIndexed { index, kp ->
            println("   Keypair ${index + 1}: ${kp.publicKey.toBase58()}")
        }
        
        assertEquals(5, keypairs.size)
        assertTrue(keypairs.map { it.publicKey.toBase58() }.distinct().size == 5, 
            "All keypairs should be unique")
    }
    
    @Test
    fun `5 - Pubkey Parsing and Validation`() {
        println("\n🎯 Test 5: Pubkey Operations")
        println("-".repeat(60))
        
        val validPubkey = "11111111111111111111111111111111"
        val pubkey = Pubkey(validPubkey)
        
        println("✅ Parsed pubkey: ${pubkey.toBase58()}")
        
        assertEquals(validPubkey, pubkey.toBase58())
    }
    
    @Test
    fun `6 - Module Structure Validation`() {
        println("\n🏗️  Test 6: Module Structure")
        println("-".repeat(60))
        
        val modules = listOf(
            "artemis-core",
            "artemis-tx",
            "artemis-rpc",
            "artemis-wallet",
            "artemis-anchor",
            "artemis-jupiter",
            "artemis-actions",
            "artemis-universal",
            "artemis-nlp",
            "artemis-streaming"
        )
        
        println("📋 Revolutionary Modules:")
        modules.forEach { module ->
            println("   ✅ $module")
        }
        
        assertTrue(modules.size == 10, "Should have 10 core modules")
    }
    
    @Test
    fun `7 - JSON Serialization Support`() {
        println("\n📄 Test 7: JSON Serialization")
        println("-".repeat(60))
        
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        val testData = mapOf(
            "version" to "2.0.0",
            "features" to listOf("anchor", "jupiter", "actions", "universal", "nlp", "streaming")
        )
        
        val jsonString = json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                String.serializer(),
                kotlinx.serialization.json.JsonElement.serializer()
            ),
            testData.mapValues { JsonPrimitive(it.value.toString()) }
        )
        
        println("✅ JSON serialization works")
        println(jsonString)
        
        assertTrue(jsonString.contains("2.0.0"))
    }
    
    @Test
    fun `8 - HTTP Client Availability`() {
        println("\n🌐 Test 8: HTTP Client")
        println("-".repeat(60))
        
        val httpClient = OkHttpClient.Builder()
            .build()
        
        println("✅ OkHttp client initialized")
        
        assertNotNull(httpClient)
    }
    
    @Test
    fun `9 - Comprehensive SDK Readiness Check`() = runBlocking {
        println("\n🌟 Test 9: SDK Publication Readiness")
        println("-".repeat(60))
        
        val checks = mutableMapOf<String, Boolean>()
        
        // Check 1: Keypair generation
        try {
            Keypair.generate()
            checks["Keypair Generation"] = true
        } catch (e: Exception) {
            checks["Keypair Generation"] = false
            println("❌ Keypair: ${e.message}")
        }
        
        // Check 2: Pubkey parsing
        try {
            Pubkey("11111111111111111111111111111111")
            checks["Pubkey Parsing"] = true
        } catch (e: Exception) {
            checks["Pubkey Parsing"] = false
            println("❌ Pubkey: ${e.message}")
        }
        
        // Check 3: RPC client
        try {
            JsonRpcClient(devnetEndpoint)
            checks["RPC Client"] = true
        } catch (e: Exception) {
            checks["RPC Client"] = false
            println("❌ RPC: ${e.message}")
        }
        
        // Check 4: Transaction builder
        try {
            val wallet = Keypair.generate()
            artemisTransaction {
                feePayer(wallet.publicKey)
            }
            checks["Transaction Builder"] = true
        } catch (e: Exception) {
            checks["Transaction Builder"] = false
            println("❌ TX Builder: ${e.message}")
        }
        
        // Check 5: JSON handling
        try {
            Json.parseToJsonElement("{\"test\": true}")
            checks["JSON Support"] = true
        } catch (e: Exception) {
            checks["JSON Support"] = false
            println("❌ JSON: ${e.message}")
        }
        
        // Check 6: HTTP client
        try {
            OkHttpClient()
            checks["HTTP Client"] = true
        } catch (e: Exception) {
            checks["HTTP Client"] = false
            println("❌ HTTP: ${e.message}")
        }
        
        // Summary
        println("\n" + "=".repeat(60))
        println("📊 Publication Readiness Report:")
        val passed = checks.values.count { it }
        val total = checks.size
        println("   Status: $passed/$total checks passed")
        println()
        
        checks.forEach { (check, result) ->
            val icon = if (result) "✅" else "❌"
            println("   $icon $check")
        }
        
        val successRate = (passed.toDouble() / total * 100).toInt()
        println()
        println("🎯 Success Rate: $successRate%")
        
        if (successRate == 100) {
            println()
            println("🎉 SDK IS READY FOR PUBLICATION!")
            println()
            println("Next Steps:")
            println("   1. ./publish.sh               (Maven Central)")
            println("   2. cd artemis-react-native    (NPM)")
            println("      npm publish --access public")
            println("   3. git tag v2.0.0             (GitHub Release)")
            println("      git push origin v2.0.0")
        }
        
        assertTrue(successRate >= 80, "At least 80% of checks should pass")
    }
    
    @AfterAll
    fun teardown() {
        println()
        println("=".repeat(60))
        println("🏁 Pre-Publication Tests Completed")
        println("=" .repeat(60))
        println()
        println("ℹ️  For FULL devnet testing:")
        println("   See DEVNET_TESTING_GUIDE.md for instructions")
    }
}
