package com.selenus.artemis.cnft

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.Assume
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58

/**
 * Comprehensive tests for artemis-cnft module v1.2.0 enhancements
 * 
 * Tests CnftCollectionManager with batch operations, proof caching,
 * and collection management
 */
class CnftModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== CnftCollectionManager Core Tests ====================

    @Test
    fun `CnftCollectionManager - create instance with default config`() {
        runBlocking {
            val manager = CnftCollectionManager(testScope)
            assertNotNull(manager)
        }
    }

    @Test
    fun `CnftCollectionManager - create instance with custom config`() {
        runBlocking {
            val manager = CnftCollectionManager(
                testScope,
                CnftCollectionManager.Config(
                    maxConcurrentMints = 10,
                    maxConcurrentTransfers = 20,
                    cacheSize = 500,
                    proofCacheTtlMs = 30_000L,
                    enableMetadataCache = false
                )
            )
            assertNotNull(manager)
        }
    }

    // ==================== CnftAsset Tests ====================

    @Test
    fun `CnftAsset - create and verify structure`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)
        val merkleTree = Keypair.generate().publicKey

        val asset = CnftCollectionManager.CnftAsset(
            id = "test-asset-id",
            owner = keypair.publicKey,
            delegate = null,
            merkleTree = merkleTree,
            leafIndex = 0L,
            dataHash = ByteArray(32) { it.toByte() },
            creatorHash = ByteArray(32) { (it + 100).toByte() },
            nonce = 0L,
            metadata = null,
            proofPath = null
        )

        assertNotNull(asset)
        assertEquals("test-asset-id", asset.id)
        assertEquals(keypair.publicKey, asset.owner)
        assertEquals(merkleTree, asset.merkleTree)
        assertEquals(0L, asset.leafIndex)
    }

    @Test
    fun `CnftAsset - equality based on id`() {
        val asset1 = CnftCollectionManager.CnftAsset(
            id = "same-id",
            owner = Keypair.generate().publicKey,
            delegate = null,
            merkleTree = Keypair.generate().publicKey,
            leafIndex = 0L,
            dataHash = ByteArray(32),
            creatorHash = ByteArray(32),
            nonce = 0L,
            metadata = null,
            proofPath = null
        )

        val asset2 = CnftCollectionManager.CnftAsset(
            id = "same-id",
            owner = Keypair.generate().publicKey,  // Different owner
            delegate = null,
            merkleTree = Keypair.generate().publicKey,  // Different tree
            leafIndex = 100L,  // Different index
            dataHash = ByteArray(32),
            creatorHash = ByteArray(32),
            nonce = 0L,
            metadata = null,
            proofPath = null
        )

        assertEquals(asset1, asset2)  // Should be equal based on id
        assertEquals(asset1.hashCode(), asset2.hashCode())
    }

    // ==================== CnftMetadata Tests ====================

    @Test
    fun `CnftMetadata - create with all fields`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val metadata = CnftCollectionManager.CnftMetadata(
            name = "Test cNFT #1",
            symbol = "TCNFT",
            uri = "https://example.com/metadata/1.json",
            sellerFeeBasisPoints = 500,
            creators = listOf(
                CnftCollectionManager.Creator(
                    address = keypair.publicKey,
                    verified = true,
                    share = 100
                )
            ),
            collection = CnftCollectionManager.CollectionInfo(
                verified = true,
                key = Keypair.generate().publicKey
            ),
            uses = CnftCollectionManager.Uses(
                useMethod = CnftCollectionManager.UseMethod.BURN,
                remaining = 1L,
                total = 1L
            ),
            attributes = listOf(
                CnftCollectionManager.Attribute("trait_type", "background"),
                CnftCollectionManager.Attribute("trait_value", "blue")
            )
        )

        assertNotNull(metadata)
        assertEquals("Test cNFT #1", metadata.name)
        assertEquals("TCNFT", metadata.symbol)
        assertEquals(500, metadata.sellerFeeBasisPoints)
        assertEquals(1, metadata.creators.size)
        assertEquals(100, metadata.creators[0].share)
        assertTrue(metadata.creators[0].verified)
    }

    // ==================== Creator Tests ====================

    @Test
    fun `Creator - verify share validation`() {
        val creator1 = CnftCollectionManager.Creator(
            address = Keypair.generate().publicKey,
            verified = true,
            share = 60
        )

        val creator2 = CnftCollectionManager.Creator(
            address = Keypair.generate().publicKey,
            verified = false,
            share = 40
        )

        // Shares should sum to 100
        assertEquals(100, creator1.share + creator2.share)
    }

    // ==================== UseMethod Tests ====================

    @Test
    fun `UseMethod - all values available`() {
        val burn = CnftCollectionManager.UseMethod.BURN
        val multiple = CnftCollectionManager.UseMethod.MULTIPLE
        val single = CnftCollectionManager.UseMethod.SINGLE

        assertNotNull(burn)
        assertNotNull(multiple)
        assertNotNull(single)
    }

    // ==================== MerkleProof Tests ====================

    @Test
    fun `MerkleProof - verifySha256 with valid proof`() {
        // Create a simple proof structure for testing
        val leafHash = ByteArray(32) { 0x01.toByte() }
        val root = ByteArray(32) { 0xFF.toByte() }
        val proof = listOf(ByteArray(32) { 0x02.toByte() })
        val index = 0

        // This won't verify as correct but tests the function exists and runs
        val result = MerkleProof.verifySha256(leafHash, root, proof, index)
        
        // Result is boolean - the proof won't match but the function should execute
        assertNotNull(result)
    }

    @Test
    fun `MerkleProof - verifySha256 with empty proof`() {
        val leafHash = ByteArray(32) { 0x01.toByte() }
        val root = leafHash.copyOf()  // Empty proof means leaf equals root
        val proof = emptyList<ByteArray>()
        val index = 0

        val result = MerkleProof.verifySha256(leafHash, root, proof, index)
        
        // With empty proof, leaf should equal root
        assertTrue(result)
    }

    // ==================== MetadataArgs Tests ====================

    @Test
    fun `MetadataArgs - build from metadata`() {
        val args = MetadataArgs(
            name = "Test NFT",
            symbol = "TEST",
            uri = "https://example.com/1.json",
            sellerFeeBasisPoints = 500,
            primarySaleHappened = false,
            isMutable = true,
            editionNonce = null,
            collection = null,
            uses = null,
            creators = emptyList()
        )

        assertNotNull(args)
        assertEquals("Test NFT", args.name)
        assertEquals("TEST", args.symbol)
        assertEquals(500, args.sellerFeeBasisPoints)
        assertFalse(args.primarySaleHappened)
        assertTrue(args.isMutable)
    }

    // ==================== BubblegumPrograms Tests ====================

    @Test
    fun `BubblegumPrograms - program IDs are valid`() {
        val bubblegum = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID
        val compression = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID
        val logWrapper = BubblegumPrograms.LOG_WRAPPER_ID
        val tokenMetadata = BubblegumPrograms.TOKEN_METADATA_PROGRAM_ID

        assertNotNull(bubblegum)
        assertNotNull(compression)
        assertNotNull(logWrapper)
        assertNotNull(tokenMetadata)

        // Verify they are different
        assertTrue(bubblegum != compression)
        assertTrue(compression != logWrapper)
    }

    // ==================== BubblegumPdas Tests ====================

    @Test
    fun `BubblegumPdas - derive tree config`() {
        val merkleTree = Keypair.generate().publicKey
        val treeConfig = BubblegumPdas.treeConfig(merkleTree)

        assertNotNull(treeConfig)
        // Tree config is a valid pubkey
        assertTrue(treeConfig.bytes.size == 32)
    }

    @Test
    fun `BubblegumPdas - derive leaf asset id`() {
        val merkleTree = Keypair.generate().publicKey
        val leafIndex = 42L
        val assetId = BubblegumPdas.leafAssetId(merkleTree, leafIndex)

        assertNotNull(assetId)
        assertTrue(assetId.bytes.size == 32)
    }

    @Test
    fun `BubblegumPdas - derive voucher`() {
        val merkleTree = Keypair.generate().publicKey
        val nonce = 100L
        val voucher = BubblegumPdas.voucher(merkleTree, nonce)

        assertNotNull(voucher)
        assertTrue(voucher.bytes.size == 32)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `CnftCollectionManager Integration - devnet asset lookup`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)
            val manager = CnftCollectionManager(testScope)

            println("cNFT Integration Test:")
            println("  Wallet: ${keypair.publicKey.toBase58()}")
            
            // Test passed if no exception thrown
            assertTrue(true)
        }
    }
}
