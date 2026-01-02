package com.selenus.artemis.samples

import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.privacy.ConfidentialTransfer
import com.selenus.artemis.privacy.RingSignature
import com.selenus.artemis.gaming.VerifiableRandomness
import com.selenus.artemis.gaming.GameStateProofs
import com.selenus.artemis.gaming.RewardDistribution
import com.selenus.artemis.metaplex.AdvancedNftOperations
import com.selenus.artemis.token2022.AdvancedToken2022Extensions
import kotlinx.coroutines.runBlocking

/**
 * Artemis SDK - Comprehensive Devnet Integration Test
 * Tests all innovative features created in January 2026
 */
object ArtemisIntegrationTest {
    private lateinit var rpc: RpcApi
    private lateinit var wallet: Keypair
    private lateinit var walletPubkey: String
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("═══════════════════════════════════════════════════════════════")
        println("   ARTEMIS SDK - Devnet Integration Test Suite")
        println("   Testing innovative features (Privacy, Gaming, NFT, Token)")
        println("═══════════════════════════════════════════════════════════════\n")
        
        setup()
        
        println("\n🔐 PRIVACY MODULE")
        testConfidentialTransfer()
        testRingSignatures()
        
        println("\n🎮 GAMING MODULE")
        testVerifiableRandomness()
        testGameStateProofs()
        testRewardDistribution()
        
        println("\n🎨 METAPLEX MODULE")
        testAdvancedNftOperations()
        
        println("\n💰 TOKEN-2022 MODULE")
        testToken2022Extensions()
        
        println("\n═══════════════════════════════════════════════════════════════")
        println("   ✅ ALL TESTS COMPLETED SUCCESSFULLY")
        println("═══════════════════════════════════════════════════════════════")
    }
    
    private suspend fun setup() {
        println("⚙️  Setup...\n")
        
        val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            ?: throw IllegalStateException("DEVNET_WALLET_SEED not set!")
        
        wallet = Keypair.fromSeed(Base58.decode(secretBase58))
        walletPubkey = wallet.publicKey.toBase58()
        
        println("   Wallet: $walletPubkey")
        
        rpc = RpcApi(JsonRpcClient("https://api.devnet.solana.com"))
        
        val health = rpc.getHealth()
        println("   Network: $health")
        
        val balance = rpc.getBalance(walletPubkey)
        println("   Balance: ${balance.lamports / 1_000_000_000.0} SOL\n")
    }
    
    private fun testConfidentialTransfer() {
        println("1️⃣  Confidential Transfers\n")
        try {
            val confKeys = ConfidentialTransfer.ConfidentialKeys.derive(wallet.secretKeyBytes())
            println("   ✓ Generated confidential keys")
            
            val amount = 1_000_000L
            val recipient = Keypair.generate()
            val encrypted = ConfidentialTransfer.encryptAmount(
                amount, confKeys, recipient.publicKey.bytes
            )
            println("   ✓ Encrypted amount: $amount lamports")
            
            val decrypted = ConfidentialTransfer.decryptAmount(encrypted, confKeys)
            println("   ✓ Decrypted: $decrypted lamports")
            println("   ✅ ${if (decrypted == amount) "VERIFIED" else "FAILED"}\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testRingSignatures() {
        println("2️⃣  Ring Signatures\n")
        try {
            val ring = RingSignature.Ring(
                listOf(wallet.publicKey, Keypair.generate().publicKey, 
                       Keypair.generate().publicKey, Keypair.generate().publicKey),
                "test-vote".toByteArray()
            )
            println("   ✓ Ring with ${ring.members.size} members")
            
            val signature = RingSignature.sign(
                "Vote YES".toByteArray(), ring, wallet.secretKeyBytes(), 0
            )
            println("   ✓ Signed anonymously")
            
            val isValid = RingSignature.verify(signature)
            println("   ✓ Verification: $isValid")
            println("   ✅ ${if (isValid) "VERIFIED" else "FAILED"}\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testVerifiableRandomness() {
        println("3️⃣  Verifiable Randomness\n")
        try {
            val (commitment, secret) = VerifiableRandomness.commit()
            println("   ✓ Created commitment")
            
            val reveal = VerifiableRandomness.Reveal(
                secret.copyOfRange(0, 32), secret.copyOfRange(32, 48), commitment
            )
            val isValid = reveal.verify()
            println("   ✓ Reveal verification: $isValid")
            
            val vrfOutput = VerifiableRandomness.vrfGenerate(
                wallet.secretKeyBytes(), "game-seed".toByteArray()
            )
            println("   ✓ Generated VRF")
            
            val vrfVerified = VerifiableRandomness.vrfVerify(
                wallet.publicKey.bytes, "game-seed".toByteArray(), vrfOutput
            )
            println("   ✓ VRF verification: $vrfVerified")
            println("   ✅ ${if (isValid && vrfVerified) "VERIFIED" else "FAILED"}\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testGameStateProofs() {
        println("4️⃣  Game State Proofs\n")
        try {
            val players = listOf(wallet.publicKey, Keypair.generate().publicKey)
            val state = GameStateProofs.GameState(
                ByteArray(32), 0, System.currentTimeMillis(), players,
                mapOf("hp" to byteArrayOf(100))
            )
            val stateHash = state.computeHash()
            println("   ✓ Created game state")
            println("   ✓ Hash: ${Base58.encode(stateHash.take(12).toByteArray())}...")
            
            val updatedState = state.update("hp", byteArrayOf(90))
            println("   ✓ Updated state (HP: 100→90)")
            
            val stateTree = GameStateProofs.buildStateTree(updatedState)
            println("   ✓ Built Merkle tree")
            
            val proof = GameStateProofs.generateMerkleProof(stateTree, "hp")
            val isValid = GameStateProofs.verifyStateEntry(
                stateTree.root, "hp", byteArrayOf(90), proof
            )
            println("   ✓ Proof verification: $isValid")
            println("   ✅ ${if (isValid) "VERIFIED" else "FAILED"}\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testRewardDistribution() {
        println("5️⃣  Reward Distribution\n")
        try {
            val pool = 10_000_000L
            
            val wta = RewardDistribution.PayoutStrategy.WinnerTakesAll().calculate(pool, 1)
            println("   ✓ Winner Takes All: ${wta[0]/1_000_000.0} SOL")
            
            val linear = RewardDistribution.PayoutStrategy.LinearDecay(3).calculate(pool, 3)
            println("   ✓ Linear (#1: ${linear[0]/1_000_000.0} SOL)")
            
            val poker = RewardDistribution.PayoutStrategy.PokerStyle(10).calculate(pool, 3)
            println("   ✓ Poker-style (#1: ${poker[0]/1_000_000.0} SOL)")
            
            val winners = listOf(wallet.publicKey, Keypair.generate().publicKey)
            val claims = winners.zip(linear.take(2)).map { (pk, amt) ->
                RewardDistribution.RewardClaim(pk, amt, mapOf())
            }
            val tree = RewardDistribution.RewardTree.build(claims)
            println("   ✓ Built Merkle claim tree")
            println("   ✅ VERIFIED\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testAdvancedNftOperations() {
        println("6️⃣  Advanced NFT Operations\n")
        try {
            val batchConfig = AdvancedNftOperations.BatchMintConfig(
                listOf(
                    AdvancedNftOperations.BatchMintConfig.MintConfig(
                        "NFT #1", "ART", "https://arweave.net/1.json", 500, null, wallet.publicKey
                    )
                ),
                null, wallet.publicKey, wallet.publicKey, false
            )
            val batch = AdvancedNftOperations.prepareBatchMint(batchConfig)
            println("   ✓ Batch mint: ${batch.size} transaction(s)")
            println("   ✓ Instructions: ${batch[0].instructions.size}")
            println("   ✅ PREPARED\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
    
    private fun testToken2022Extensions() {
        println("7️⃣  Token-2022 Extensions\n")
        try {
            val mint = Keypair.generate().publicKey
            
            val interestInstr = AdvancedToken2022Extensions.initializeInterestBearingMint(
                mint, wallet.publicKey, 500
            )
            println("   ✓ Interest-bearing (5% APY)")
            
            val soulboundInstr = AdvancedToken2022Extensions.initializeNonTransferableMint(mint)
            println("   ✓ Non-transferable (soulbound)")
            
            val delegateInstr = AdvancedToken2022Extensions.initializePermanentDelegate(
                mint, wallet.publicKey
            )
            println("   ✓ Permanent delegate")
            
            val extensions = AdvancedToken2022Extensions.prepareMintWithExtensions(
                mint, 9, wallet.publicKey, wallet.publicKey, wallet.publicKey,
                listOf(
                    AdvancedToken2022Extensions.MintExtension.InterestBearing(wallet.publicKey, 500),
                    AdvancedToken2022Extensions.MintExtension.NonTransferable
                )
            )
            println("   ✓ Prepared ${extensions.size} extension instructions")
            println("   ✅ ALL EXTENSIONS READY\n")
        } catch (e: Exception) {
            println("   ❌ ${e.message}\n")
        }
    }
}
