/*
 * Tests for the royalty surface on MarketplacePreflight.
 *
 * The preflight layer does not enforce royalties on-chain; it simply
 * surfaces the declared royalty so marketplace UIs can render a warning
 * before the user signs. These tests validate the RoyaltyInfo type and the
 * cNFT preflight populates it correctly without needing a live DAS endpoint.
 */
package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.DigitalAsset
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketplacePreflightRoyaltyTest {

    private val wallet = Pubkey(ByteArray(32) { 1 })
    private val assetId = "TestAssetId1234567890"

    private fun rpc(): RpcApi = RpcApi(JsonRpcClient("https://api.devnet.solana.com"))

    /** Fake DAS that returns a pre-configured asset without hitting the network. */
    private class FakeDas(private val asset: DigitalAsset?) : ArtemisDas {
        override suspend fun assetsByOwner(owner: Pubkey, page: Int, limit: Int): List<DigitalAsset> =
            emptyList()

        override suspend fun asset(id: String): DigitalAsset? = asset

        override suspend fun assetsByCollection(
            collectionAddress: String,
            page: Int,
            limit: Int
        ): List<DigitalAsset> = emptyList()
    }

    @Test
    fun `RoyaltyInfo computes percent from basis points`() {
        val info = MarketplacePreflight.RoyaltyInfo(basisPoints = 500, verifiedCollection = true)
        assertEquals(5.0, info.percent, 1e-9)
    }

    @Test
    fun `RoyaltyInfo handles zero royalty`() {
        val info = MarketplacePreflight.RoyaltyInfo(basisPoints = 0, verifiedCollection = false)
        assertEquals(0.0, info.percent, 1e-9)
    }

    @Test
    fun `cNFT preflight surfaces royalty info when asset has one`() = runBlocking {
        val das = FakeDas(
            DigitalAsset(
                id = assetId,
                name = "Test",
                symbol = "T",
                uri = "https://example.com/meta.json",
                owner = wallet.toBase58(),
                royaltyBasisPoints = 250,
                isCompressed = true,
                frozen = false,
                collectionAddress = "CollMint1234",
                collectionVerified = true
            )
        )
        val preflight = MarketplacePreflight(rpc = rpc(), das = das)

        val result = preflight.validateCnftTransfer(wallet, assetId)

        assertTrue(result.valid)
        val royalty = result.royalty
        assertNotNull(royalty)
        assertEquals(250, royalty.basisPoints)
        assertEquals(2.5, royalty.percent, 1e-9)
        assertTrue(royalty.verifiedCollection)
        // When royalty is non-zero, the preflight attaches a warning.
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { "2.5%" in it })
    }

    @Test
    fun `cNFT preflight still populates royalty when collection is unverified`() = runBlocking {
        val das = FakeDas(
            DigitalAsset(
                id = assetId,
                name = "Test",
                symbol = "T",
                uri = "https://example.com/meta.json",
                owner = wallet.toBase58(),
                royaltyBasisPoints = 100,
                isCompressed = true,
                frozen = false,
                collectionAddress = null,
                collectionVerified = false
            )
        )
        val preflight = MarketplacePreflight(rpc = rpc(), das = das)

        val result = preflight.validateCnftTransfer(wallet, assetId)

        assertTrue(result.valid)
        val royalty = result.royalty
        assertNotNull(royalty)
        assertEquals(false, royalty.verifiedCollection)
        assertTrue(
            result.warnings.any { "unverified" in it || "not verified" in it },
            "unverified collection should raise a warning: ${result.warnings}"
        )
    }

    @Test
    fun `cNFT preflight omits royalty when DAS record is missing`() = runBlocking {
        val das = FakeDas(null)
        val preflight = MarketplacePreflight(rpc = rpc(), das = das)
        val result = preflight.validateCnftTransfer(wallet, assetId)
        // Asset not found in DAS flags the transaction invalid.
        assertEquals(false, result.valid)
        // No royalty info when the asset itself could not be resolved.
        assertNull(result.royalty)
    }
}
