package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.DigitalAsset
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.vtx.TxEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for [MarketplaceEngine].
 *
 * Tests cover:
 * - [MarketplaceEngine.MarketplaceResult] data class contract
 * - Guard: [MarketplaceEngine.getAssetsByOwner] requires a non-null [ArtemisDas]
 * - Guard: [MarketplaceEngine.getAssetsByCollection] requires a non-null [ArtemisDas]
 * - Delegation: asset queries are correctly forwarded to the [ArtemisDas] implementation
 *
 * Tests that require a real network connection (transferCnft, executeInstructions) are
 * covered by the integration tests in artemis-integration-tests / artemis-devnet-tests.
 */
class MarketplaceEngineTest {

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Builds a [MarketplaceEngine] wired to the given [das], with no real network. */
    private fun engine(das: ArtemisDas? = null): MarketplaceEngine {
        val rpc = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))
        val txEngine = TxEngine(rpc)
        return MarketplaceEngine(rpc, txEngine, das)
    }

    /** Zero-filled mock pubkey. */
    private val zeroPubkey = Pubkey(ByteArray(32) { 0 })

    /** Fake [ArtemisDas] that records calls and returns configured fixture data. */
    private class FakeDas(
        private val ownedAssets: List<DigitalAsset> = emptyList(),
        private val collectionAssets: List<DigitalAsset> = emptyList(),
        private val singleAsset: DigitalAsset? = null
    ) : ArtemisDas {
        var assetsByOwnerCalled = false
        var assetsByCollectionCalled = false
        var lastOwner: String? = null
        var lastCollection: String? = null

        override suspend fun assetsByOwner(owner: Pubkey, page: Int, limit: Int): List<DigitalAsset> {
            assetsByOwnerCalled = true
            lastOwner = owner.toBase58()
            return ownedAssets
        }

        override suspend fun asset(id: String): DigitalAsset? = singleAsset

        override suspend fun assetsByCollection(
            collectionAddress: String,
            page: Int,
            limit: Int
        ): List<DigitalAsset> {
            assetsByCollectionCalled = true
            lastCollection = collectionAddress
            return collectionAssets
        }
    }

    // ─── MarketplaceResult ────────────────────────────────────────────────────

    @Test
    fun `MarketplaceResult - holds signature and confirmed`() {
        val result = MarketplaceEngine.MarketplaceResult(
            signature = "5VERv8NMvzbJMEkV8xcB1QVWHtJpBmJnEAWj6oa8FKRT",
            confirmed = true
        )
        assertEquals("5VERv8NMvzbJMEkV8xcB1QVWHtJpBmJnEAWj6oa8FKRT", result.signature)
        assertTrue(result.confirmed)
    }

    @Test
    fun `MarketplaceResult - confirmed can be false`() {
        val result = MarketplaceEngine.MarketplaceResult(signature = "abc", confirmed = false)
        assertFalse(result.confirmed)
    }

    @Test
    fun `MarketplaceResult - equals and copy`() {
        val a = MarketplaceEngine.MarketplaceResult("sig1", true)
        val b = MarketplaceEngine.MarketplaceResult("sig1", true)
        val c = a.copy(confirmed = false)
        assertEquals(a, b)
        assertFalse(c.confirmed)
    }

    // ─── das = null guard ─────────────────────────────────────────────────────

    @Test
    fun `getAssetsByOwner - throws IllegalStateException when das is null`() {
        val marketplace = engine(das = null)
        try {
            runBlocking { marketplace.getAssetsByOwner(zeroPubkey) }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("ArtemisDas") == true,
                "Error message should mention ArtemisDas. Got: ${e.message}")
        }
    }

    @Test
    fun `getAssetsByCollection - throws IllegalStateException when das is null`() {
        val marketplace = engine(das = null)
        try {
            runBlocking { marketplace.getAssetsByCollection("CollMint1234") }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
    }

    // ─── DAS delegation ───────────────────────────────────────────────────────

    @Test
    fun `getAssetsByOwner - delegates to ArtemisDas and returns assets`() {
        val fixture = listOf(
            DigitalAsset("id1", "NFT One",   "NFT", "uri1", "owner", 500, true,  false, null, false),
            DigitalAsset("id2", "NFT Two",   "NFT", "uri2", "owner", 500, false, false, "col1", true),
            DigitalAsset("id3", "NFT Three", "NFT", "uri3", "owner", 200, true,  true,  "col2", true)
        )
        val fakeDas = FakeDas(ownedAssets = fixture)
        val marketplace = engine(das = fakeDas)
        val result = runBlocking { marketplace.getAssetsByOwner(zeroPubkey) }

        assertTrue(fakeDas.assetsByOwnerCalled)
        assertEquals(3, result.size)
        assertEquals("id1", result[0].id)
        assertEquals("NFT Two", result[1].name)
        assertFalse(result[2].isCompressed.not()) // result[2].isCompressed == true
    }

    @Test
    fun `getAssetsByOwner - passes owner pubkey as base58 to DAS`() {
        val pubkey = Pubkey(ByteArray(32) { it.toByte() })
        val fakeDas = FakeDas()
        val marketplace = engine(das = fakeDas)
        runBlocking { marketplace.getAssetsByOwner(pubkey) }

        assertEquals(pubkey.toBase58(), fakeDas.lastOwner)
    }

    @Test
    fun `getAssetsByOwner - returns empty list when DAS returns empty`() {
        val fakeDas = FakeDas(ownedAssets = emptyList())
        val marketplace = engine(das = fakeDas)
        val result = runBlocking { marketplace.getAssetsByOwner(zeroPubkey) }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAssetsByCollection - delegates to ArtemisDas and returns assets`() {
        val collAddr = "J1S9H3QjnRtBbbuD4HjPV6RpRhwuk4zKbxsnCHuTgh9w"
        val fixture = listOf(
            DigitalAsset("id10", "Collection NFT", "CNFT", "uri10", "owner2", 0, true, false, collAddr, true)
        )
        val fakeDas = FakeDas(collectionAssets = fixture)
        val marketplace = engine(das = fakeDas)
        val result = runBlocking { marketplace.getAssetsByCollection(collAddr) }

        assertTrue(fakeDas.assetsByCollectionCalled)
        assertEquals(collAddr, fakeDas.lastCollection)
        assertEquals(1, result.size)
        assertEquals("id10", result[0].id)
    }

    @Test
    fun `getAssetsByCollection - passes exact collection address to DAS`() {
        val collAddr = "SomeMintAddressXXXX"
        val fakeDas = FakeDas()
        val marketplace = engine(das = fakeDas)
        runBlocking { marketplace.getAssetsByCollection(collAddr) }
        assertEquals(collAddr, fakeDas.lastCollection)
    }

    // ─── MarketplaceEngine construction ──────────────────────────────────────

    @Test
    fun `MarketplaceEngine - instantiate without das`() {
        val marketplace = engine(das = null)
        assertNotNull(marketplace)
    }

    @Test
    fun `MarketplaceEngine - instantiate with das`() {
        val marketplace = engine(das = FakeDas())
        assertNotNull(marketplace)
    }
}
