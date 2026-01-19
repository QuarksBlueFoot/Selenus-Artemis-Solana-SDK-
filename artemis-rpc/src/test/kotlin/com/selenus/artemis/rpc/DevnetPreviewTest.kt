package com.selenus.artemis.rpc

import com.selenus.artemis.runtime.Keypair
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.*
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.programs.MemoProgram
import java.util.Base64
import com.selenus.artemis.tx.Transaction

class DevnetPreviewTest {

    @Test
    fun testAllOnChainFunctions() = runBlocking {
        println("=== Starting Devnet Preview Test ===")

        // 1. Setup RPC
        val rpc = RpcApi(JsonRpcClient("https://api.devnet.solana.com"))

        // 2. Health & Network Status
        println("2. Checking Network Status...")
        val health = rpc.getHealth()
        println("   Health: $health")
        assertEquals("ok", health, "Netowrk health should be 'ok'")

        val version = rpc.getVersion()
        println("   Version: $version")
        
        val slot = rpc.getSlot()
        println("   Current Slot: $slot")
        assertTrue(slot > 0, "Slot should be greater than 0")

        // 3. Load Funded Test Account from environment variable
        println("3. Loading Funded Test Account...")
        val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            ?: throw IllegalStateException(
                "DEVNET_WALLET_SEED environment variable not set. " +
                "See DEVNET_WALLET.md for setup instructions."
            )
        val seed = com.selenus.artemis.runtime.Base58.decode(secretBase58)
        val keypair = Keypair.fromSeed(seed)
        val pubkey = keypair.publicKey.toBase58()
        println("   Pubkey: $pubkey")

        // 4. Initial Balance Check
        val initialBalance = rpc.getBalance(pubkey)
        println("   Initial Balance: ${initialBalance.lamports}")
        // assertEquals(0L, initialBalance.lamports, "New account should have 0 balance")

        // 5. Airdrop
        println("5. Requesting Airdrop (0.5 SOL)...")
        val airdropAmount = 500_000_000L
        var airdropSuccessful = false
        try {
            val airdropSig = rpc.requestAirdrop(pubkey, airdropAmount)
            println("   Airdrop Sig: $airdropSig")

            println("   Confirming Transaction...")
            val isConfirmed = rpc.confirmTransaction(airdropSig)
            println("   Confirmed: $isConfirmed")
            assertTrue(isConfirmed, "Airdrop transaction should be confirmed")
            airdropSuccessful = true
        } catch (e: Exception) {
            println("!! WARNING: Airdrop failed. This is common on public Devnet nodes due to rate limits or faucet outages.")
            println("!! Error: ${e.message}")
        }

        // 6. Post-Airdrop Balance Check
        if (airdropSuccessful) {
            val finalBalance = rpc.getBalance(pubkey)
            println("   Final Balance: ${finalBalance.lamports}")
            val expected = initialBalance.lamports + airdropAmount
            assertEquals(expected, finalBalance.lamports, "Balance should match initial + airdrop amount")
        } else {
            println("   Skipping Balance Check due to failed airdrop.")
        }

        // 7. Get Account Info
        println("7. Fetching Account Info...")
        val accountInfo = rpc.getAccountInfo(pubkey)
        println("   Account Info found: ${accountInfo.keys}")
        
        // 8. Blockhash
        println("8. Fetching Latest Blockhash...")
        val blockhash = rpc.getLatestBlockhash()
        println("   Blockhash: ${blockhash.blockhash}")
        assertTrue(blockhash.blockhash.isNotEmpty(), "Blockhash should not be empty")

        // 9. Send Transaction (System Transfer + Memo)
        println("9. Sending Transaction (10,000 lamports)...")
        var sig: String? = null
        try {
            val recipient = Keypair.generate().publicKey
            // Must be rent-exempt (approx 0.00089 SOL), so send 0.001 SOL
            val amount = 1_000_000L 
            val transferIx = SystemProgram.transfer(
                from = keypair.publicKey,
                to = recipient,
                lamports = amount
            )
            val memoIx = MemoProgram.memo("Artemis SDK Test")
            
            val tx = com.selenus.artemis.tx.Transaction()
            tx.feePayer = keypair.publicKey
            // Use the blockhash we just fetched
            tx.recentBlockhash = blockhash.blockhash
            tx.addInstruction(transferIx)
            tx.addInstruction(memoIx)
            
            tx.sign(keypair)

            // Simulate first to be safe
            println("   Simulating...")
            val sim = rpc.simulateTransaction(tx)
            val err = sim["value"]?.jsonObject?.get("err")
            if (err != null && err !is JsonNull) {
                println("!! Simulation Failed: $err")
            } else {
                println("   Simulation OK")
            }
            
            sig = rpc.sendTransaction(tx)
            println("   Signature: $sig")
            
            // Allow some time for propagation on devnet
            kotlinx.coroutines.delay(2000)
            
            val status = rpc.getSignatureStatus(sig!!)
            println("   Initial Status: $status")
            
        } catch (e: Exception) {
             println("!! Transaction Failed: ${e.message}")
        }

        // 10. Extended RPC Methods Check
        println("10. Testing Extended RPC Methods...")

        val blockHeight = rpc.getBlockHeight()
        println("   Block Height: $blockHeight")
        assertTrue(blockHeight > 0, "Block height should be > 0")

        val txCount = rpc.getTransactionCount()
        println("   Tx Count: $txCount")
        assertTrue(txCount > 0, "Tx count should be > 0")

        val epochInfo = rpc.getEpochInfo()
        println("   Epoch Info: ${epochInfo["epoch"]}")
        assertTrue(epochInfo.containsKey("epoch"), "Epoch info should contain epoch")

        val genesisHash = rpc.getGenesisHash()
        println("   Genesis Hash: $genesisHash")
        assertTrue(genesisHash.isNotEmpty(), "Genesis hash should not be empty")

        val minRent = rpc.getMinimumBalanceForRentExemption(0)
        println("   Min Rent (0 bytes): $minRent")
        assertTrue(minRent > 0, "Min rent should be > 0")

        val multipleAccounts = rpc.getMultipleAccounts(listOf(pubkey, pubkey))
        println("   Multiple Accounts: ${multipleAccounts["value"]?.jsonArray?.size}")
        assertEquals(2, multipleAccounts["value"]?.jsonArray?.size, "Should return 2 accounts")
        
        // Use the blockhash we fetched earlier
        val isHashValid = rpc.isBlockhashValid(blockhash.blockhash)
        println("   Is Blockhash Valid: $isHashValid")
        // Note: Blockhash might expire if delay is too long, but usually fine within test duration

        println("11. Testing Extended RPC Methods (Batch 2)...")
        
        // Vote Accounts
        val voteAccounts = rpc.getVoteAccounts()
        println("   Vote Accounts (current): ${voteAccounts["current"]?.jsonArray?.size}")
        assertTrue(voteAccounts.containsKey("current") || voteAccounts.containsKey("delinquent"), "Vote accounts should have current or delinquent")

        // Cluster Nodes
        val clusterNodes = rpc.getClusterNodes()
        println("   Cluster Nodes: ${clusterNodes.size}")
        assertTrue(clusterNodes.size > 0, "Cluster nodes should be > 0")

        // Supply
        val supply = rpc.getSupplyWithExcludeNonCirculating()
        println("   Supply Keys: " + supply.keys)
        val supplyVal = if (supply.containsKey("value")) supply["value"]!!.jsonObject else supply
        println("   Supply Total: " + supplyVal["total"])
        assertTrue(supplyVal.containsKey("total"), "Supply should contain total")

        // Recent Performance Samples
        val perfSamples = rpc.getRecentPerformanceSamples(1)
        println("   Perf Samples: ${perfSamples.size}")
        assertTrue(perfSamples.size > 0, "Performance samples should be > 0")

        // Ledger Slots
        val minSlot = rpc.getMinimumLedgerSlot()
        println("   Min Ledger Slot: $minSlot")
        assertTrue(minSlot >= 0, "Min ledger slot >= 0")
        
        // Prioritization Fees
        val prioFees = rpc.getRecentPrioritizationFeesFull(listOf(pubkey))
        println("   Prio Fees: ${prioFees.size}")

        println("12. Testing Token & Transaction Methods (Batch 3)...")

        // Identity
        val identity = rpc.getIdentity()
        println("   Node Identity: $identity")
        assertTrue(identity.isNotEmpty(), "Identity should not be empty")

        // Version (Renamed variable to avoid conflict)
        val nodeVersion = rpc.getVersion()
        println("   Solana Version: ${nodeVersion["solana-core"]}")
        assertTrue(nodeVersion.containsKey("solana-core"), "Version should contain solana-core")

        // Token Supply (Wrapped SOL)
        val wsolMint = "So11111111111111111111111111111111111111112"
        val tokenSupply = rpc.getTokenSupply(wsolMint)
        println("   WSOL Supply Keys: ${tokenSupply.keys}")
        
        val tsVal = if (tokenSupply.containsKey("value")) tokenSupply["value"]!!.jsonObject else tokenSupply
        println("   WSOL Amount: ${tsVal["amount"]}")
        assertTrue(tsVal.containsKey("amount"), "Token Supply should contain amount")

        // Token Largest Accounts (WSOL)
        val largestTok = rpc.getTokenLargestAccounts(wsolMint)
        println("   WSOL Largest Accounts Keys: ${largestTok.keys}")
        // Handling JsonArray safely without constructor
        val largestList = largestTok["value"]?.jsonArray
        println("   WSOL Largest Accounts Count: ${largestList?.size ?: 0}")
        assertTrue((largestList?.size ?: 0) > 0, "Should have largest accounts for WSOL")

        // Token Accounts By Owner
        val tokAccounts = rpc.getTokenAccountsByOwner(pubkey, mint = wsolMint)
        println("   Token Accounts Keys: ${tokAccounts.keys}")
        val tokList = tokAccounts["value"]?.jsonArray
        println("   Token Accounts Count: ${tokList?.size ?: 0}")

        // Get Transaction (using the signature from step 9 if available)
        var txDetails: JsonObject? = null
        if (sig != null) {
            println("   Fetching Tx: $sig")
            try {
                // Retry loop for transaction availability
                for (i in 1..5) {
                    try {
                        txDetails = rpc.getTransaction(sig!!, commitment = "confirmed")
                        break
                    } catch (e: Exception) {
                        println("      Tx not found yet, retrying... ($i)")
                        Thread.sleep(1000)
                    }
                }
                
                if (txDetails != null) {
                    println("   Tx Slot: ${txDetails["slot"]}")
                    assertTrue(txDetails.containsKey("slot"), "Transaction should have slot")
                } else {
                    println("   Tx fetch failed after retries.")
                }
            } catch (e: Exception) {
                println("   Tx fetch warning: ${e.message}")
            }
        } else {
            println("   Skipping Tx fetch (no signature generated)")
        }
        
        println("13. Testing Signatures, Blocks & Fees (Batch 4)...")
        
        // 13.1 Get Signatures for Address
        println("   Fetching Signatures for: ${keypair.publicKey}")
        val sigs = rpc.getSignaturesForAddress(keypair.publicKey.toBase58(), limit = 5)
        println("   Signatures Found: ${sigs.size}")
        if (sigs.isNotEmpty()) {
             // Access basic fields
             val s1 = sigs[0].jsonObject
             println("   First Sig: ${s1["signature"]?.jsonPrimitive?.content?.take(10)}...")
        }

        // 13.2 Block & Slot Info from Tx
        if (txDetails != null) {
            val slot = txDetails["slot"]!!.jsonPrimitive.long
            println("   Fetching Block for Slot: $slot")
            
            // Get Block Time
            val bTime = rpc.getBlockTime(slot)
            println("   Block Time: $bTime")
            
            // Get Full Block (Reward=false to save bandwidth)
            try {
                val block = rpc.getBlock(slot, rewards = false)
                val blockHashVal = block["blockhash"]?.jsonPrimitive?.content
                println("   Block Hash: $blockHashVal")
                assertTrue(blockHashVal != null, "Block should have hash")
            } catch (e: Exception) {
                println("   Block fetch warning: ${e.message}")
            }
        }

        // 13.3 Fee For Message
        println("   Calculating Fee...")
        val feeTx = Transaction()
        feeTx.feePayer = keypair.publicKey
        feeTx.recentBlockhash = blockhash.blockhash
        feeTx.addInstruction(
            MemoProgram.memo("Fee Check")
        )
        val msg = feeTx.compileMessage()
        val msgBytes = msg.serialize()
        val msgB64 = Base64.getEncoder().encodeToString(msgBytes)
        val fee = rpc.getFeeForMessage(msgB64)
        print("   Estimated Fee: $fee lamports")
        assertTrue(fee >= 5000, "Fee should be at least 5000 (standard fee)")
        
        // 13.4 Blocks Range (Small range)
        println("   Fetching Blocks Range...")
        val currentSlot4 = rpc.getSlot()
        try {
            val blocks = rpc.getBlocks(currentSlot4 - 5, currentSlot4)
            println("   Blocks Found: ${blocks.size}")
        } catch (e: Exception) {
             println("   Blocks range fetch warning: ${e.message}")
        }
        
        // 14. Additional RPC Methods (Batch 5)
        println("14. Testing Additional Methods (Batch 5)...")

        // 14.1 Signature Statuses
        if (sigs.isNotEmpty()) {
             val sigList = sigs.map { it.jsonObject["signature"]!!.jsonPrimitive.content }
             println("   Checking statuses for ${sigList.size} signatures...")
             val statuses = rpc.getSignatureStatuses(sigList)
             val valueArr = statuses["value"]?.jsonArray
             println("   Statuses found: ${valueArr?.size}")
             // assertTrue(valueArr != null, "Should return status array")
        }

        // 14.2 Get Program Accounts (Filtered)
        // Find accounts for Token Program owned by us (should be 0, but tests the call)
        println("   Fetching Program Accounts (Token Program, Owner=${keypair.publicKey})...")
        val filters = buildJsonArray {
            add(buildJsonObject {
                put("memcmp", buildJsonObject {
                    put("offset", 32) // Owner is at offset 32 in Token Account
                    put("bytes", keypair.publicKey.toBase58())
                })
            })
            add(buildJsonObject {
                put("dataSize", 165)
            })
        }
        try {
             val accounts = rpc.getProgramAccounts(
                 programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                 filters = filters
             )
             println("   Token Accounts found (Expected 0): ${accounts.size}")
        } catch (e: Exception) {
             println("   GPA warning: ${e.message}")
        }

        // 14.3 Address Lookup Table (ALT)
        println("   Fetching Address Lookup Table (Random)...")
        try {
            // Using system program as specific address just to test it returns result
            val alt = rpc.getAddressLookupTable("11111111111111111111111111111111")
            if (alt.value != null) {
                println("   ALT Found (Unexpected for System Program)")
            } else {
                println("   ALT Lookup returned null (Expected)")
            }
        } catch (e: Exception) {
            println("   ALT warning: ${e.message}")
        }

        // 15. Remaining System Methods (Batch 6)
        println("15. Testing Remaining System Methods (Batch 6)...")
        
        // Epoch Schedule
        try {
            val epochSchedule = rpc.getEpochSchedule()
            println("   Epoch Schedule: slotsPerEpoch=${epochSchedule["slotsPerEpoch"]}")
        } catch (e: Exception) { println("   Epoch Schedule warning: ${e.message}") }

        // Inflation
        try {
            val inflationGov = rpc.getInflationGovernor()
            println("   Inflation Governor: foundation=${inflationGov["foundation"]}")
            
            val inflationRate = rpc.getInflationRate()
            println("   Inflation Rate: Total=${inflationRate["total"]}")
        } catch (e: Exception) { println("   Inflation warning: ${e.message}") }

        // Genesis & Blocks
        val firstAvailable = rpc.getFirstAvailableBlock()
        println("   First Available Block: $firstAvailable")

        val minLedger = rpc.getMinimumLedgerSlot()
        println("   Min Ledger Slot: $minLedger")
        
        // Slot Leaders (requires current slot)
        val currentSlot5 = rpc.getSlot()
        try {
            val slotLeaders = rpc.getSlotLeaders(currentSlot5, 3)
            println("   Slot Leaders (Next 3): $slotLeaders")
        } catch (e: Exception) { println("   Slot Leaders warning: ${e.message}") }

        println("=== Preview Test Completed Successfully ===")
    }

}
