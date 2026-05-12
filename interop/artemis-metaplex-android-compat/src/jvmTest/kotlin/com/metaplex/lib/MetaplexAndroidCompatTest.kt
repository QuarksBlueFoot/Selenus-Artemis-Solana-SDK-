/*
 * Smoke tests for the com.metaplex.lib drop-in shim.
 *
 * These exercise the shim's data-class layer and the Metaplex facade
 * construction. RPC round-trips are out of scope here because they would
 * require a live devnet endpoint; the live RPC path is exercised by
 * testing/artemis-devnet-tests.
 */
package com.metaplex.lib

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.DigitalAsset
import com.selenus.artemis.runtime.Pubkey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MetaplexAndroidCompatTest {

    @Test
    fun `Connection wraps an Artemis RpcApi`() {
        val connection = Connection("https://api.devnet.solana.com")
        assertNotNull(connection.asArtemis())
    }

    @Test
    fun `Metaplex facade exposes nft, tokens, and das modules`() {
        val connection = Connection("https://api.devnet.solana.com")
        val metaplex = Metaplex(connection)
        assertNotNull(metaplex.nft)
        assertNotNull(metaplex.tokens)
        assertNotNull(metaplex.das)
    }

    @Test
    fun `NftModule creator and update authority queries delegate to DAS when provided`() = runBlocking {
        val das = FakeDas(
            creatorAssets = listOf(asset("creator-asset", name = "Creator Asset")),
            authorityAssets = listOf(asset("authority-asset", name = "Authority Asset"))
        )
        val metaplex = Metaplex(
            connection = Connection("https://api.devnet.solana.com"),
            dasProvider = das
        )

        val byCreator = metaplex.nft.findAllByCreator("creator-address")
        val byAuthority = metaplex.nft.findAllByUpdateAuthority("authority-address")

        assertEquals("creator-address", das.lastCreator)
        assertEquals("authority-address", das.lastAuthority)
        assertEquals("creator-asset", byCreator.single().mint)
        assertEquals("Creator Asset", byCreator.single().name)
        assertEquals(true, byCreator.single().isMutable)
        assertEquals("authority-asset", byAuthority.single().mint)
    }

    @Test
    fun `NftModule creator queries keep empty RPC-only fallback`() = runBlocking {
        val metaplex = Metaplex(Connection("https://api.devnet.solana.com"))

        assertEquals(emptyList(), metaplex.nft.findAllByCreator("creator-address"))
        assertEquals(emptyList(), metaplex.nft.findAllByUpdateAuthority("authority-address"))
    }

    @Test
    fun `auctions module surfaces a typed error`() {
        val connection = Connection("https://api.devnet.solana.com")
        val metaplex = Metaplex(connection)
        val result = metaplex.auctions.bid("auction", price = 1L)
        assertEquals(true, result.message.contains("Auction House"))
    }

    @Test
    fun `NFT data class preserves field ordering`() {
        val nft = NFT(
            mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            name = "Example",
            symbol = "EX",
            uri = "https://example.com/metadata.json",
            sellerFeeBasisPoints = 500,
            isMutable = true
        )
        assertEquals("Example", nft.name)
        assertEquals(500, nft.sellerFeeBasisPoints)
        assertEquals(true, nft.isMutable)
    }

    @Test
    fun `Token data class exposes mint authorities`() {
        val token = Token(
            mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            decimals = 6,
            supply = 1_000_000_000L,
            mintAuthority = "GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv",
            freezeAuthority = null
        )
        assertEquals(6, token.decimals)
        assertEquals(null, token.freezeAuthority)
    }

    @Test
    fun `KeypairIdentityDriver reports its public key`() {
        val driver = KeypairIdentityDriver("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv")
        assertEquals("GdRNeX9mbzrhm5tKWGP3mN1p6ZqoDMCK7BmLbsjc23Jv", driver.publicKey)
    }

    @Test
    fun `Guest driver is unauthenticated`() {
        assertEquals(null, Guest.publicKey)
    }

    private class FakeDas(
        private val creatorAssets: List<DigitalAsset> = emptyList(),
        private val authorityAssets: List<DigitalAsset> = emptyList()
    ) : ArtemisDas {
        var lastCreator: String? = null
            private set
        var lastAuthority: String? = null
            private set

        override suspend fun assetsByOwner(owner: Pubkey, page: Int, limit: Int): List<DigitalAsset> = emptyList()

        override suspend fun asset(id: String): DigitalAsset? = null

        override suspend fun assetsByCollection(
            collectionAddress: String,
            page: Int,
            limit: Int
        ): List<DigitalAsset> = emptyList()

        override suspend fun assetsByCreator(
            creatorAddress: String,
            page: Int,
            limit: Int,
            onlyVerified: Boolean
        ): List<DigitalAsset> {
            lastCreator = creatorAddress
            return creatorAssets
        }

        override suspend fun assetsByUpdateAuthority(
            authorityAddress: String,
            page: Int,
            limit: Int
        ): List<DigitalAsset> {
            lastAuthority = authorityAddress
            return authorityAssets
        }
    }

    private fun asset(id: String, name: String): DigitalAsset = DigitalAsset(
        id = id,
        name = name,
        symbol = "ART",
        uri = "https://example.com/$id.json",
        owner = "owner",
        royaltyBasisPoints = 500,
        isCompressed = false,
        frozen = false,
        collectionAddress = null,
        collectionVerified = false,
        isMutable = true
    )
}
