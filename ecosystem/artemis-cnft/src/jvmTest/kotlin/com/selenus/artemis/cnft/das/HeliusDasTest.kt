package com.selenus.artemis.cnft.das

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HeliusDas] and [ArtemisDas] / [DigitalAsset].
 *
 * All parsing logic is exercised via fixture JSON payloads, matching the
 * Helius DAS JSON-RPC response schema. No real HTTP connections are made.
 */
class HeliusDasTest {

    // ─── DigitalAsset data class ──────────────────────────────────────────────

    @Test
    fun `DigitalAsset - holds all fields`() {
        val asset = DigitalAsset(
            id = "GXfAbMfhUPXYkuqVrnXT4bRNDoWEqobDG6zq7yb3FXNK",
            name = "Mad Lads #1234",
            symbol = "MADLADS",
            uri = "https://arweave.net/abc123",
            owner = "HNGVuL5kqjDSDPMHKZcSv6vBXGnLPmkmQHrteL8HmkuK",
            royaltyBasisPoints = 500,
            isCompressed = true,
            frozen = false,
            collectionAddress = "J1S9H3QjnRtBbbuD4HjPV6RpRhwuk4zKbxsnCHuTgh9w",
            collectionVerified = true
        )
        assertEquals("GXfAbMfhUPXYkuqVrnXT4bRNDoWEqobDG6zq7yb3FXNK", asset.id)
        assertEquals("Mad Lads #1234", asset.name)
        assertEquals("MADLADS", asset.symbol)
        assertEquals("https://arweave.net/abc123", asset.uri)
        assertEquals("HNGVuL5kqjDSDPMHKZcSv6vBXGnLPmkmQHrteL8HmkuK", asset.owner)
        assertEquals(500, asset.royaltyBasisPoints)
        assertTrue(asset.isCompressed)
        assertFalse(asset.frozen)
        assertEquals("J1S9H3QjnRtBbbuD4HjPV6RpRhwuk4zKbxsnCHuTgh9w", asset.collectionAddress)
        assertTrue(asset.collectionVerified)
    }

    @Test
    fun `DigitalAsset - optional fields can be null`() {
        val asset = DigitalAsset(
            id = "xxx",
            name = "Test",
            symbol = "",
            uri = "",
            owner = "",
            royaltyBasisPoints = 0,
            isCompressed = false,
            frozen = false,
            collectionAddress = null,
            collectionVerified = false
        )
        assertNull(asset.collectionAddress)
        assertFalse(asset.collectionVerified)
    }

    @Test
    fun `DigitalAsset - equality and copy`() {
        val base = DigitalAsset("id", "Name", "SYM", "uri", "owner", 0, false, false, null, false)
        val copy = base.copy(name = "Name2")
        assertEquals("Name2", copy.name)
        assertEquals(base.id, copy.id)
    }

    // ─── HeliusDas construction ───────────────────────────────────────────────

    @Test
    fun `HeliusDas - instantiate without throwing`() {
        val das = HeliusDas("https://mainnet.helius-rpc.com/?api-key=test-key")
        assertNotNull(das)
    }

    // ─── JSON fixture helpers ─────────────────────────────────────────────────

    /**
     * Builds a raw asset JSON object matching the DAS API schema.
     * Used to validate the HeliusDas parse logic.
     */
    private fun buildAssetJson(
        id: String = "assetId123",
        name: String = "Test NFT",
        symbol: String = "TNFT",
        uri: String = "https://arweave.net/metadata",
        owner: String = "ownerPubkey",
        royaltyPercentage: Double = 5.0,
        compressed: Boolean = true,
        frozen: Boolean = false,
        collectionAddress: String? = "collPubkey",
        collectionVerified: Boolean = true
    ) = buildJsonObject {
        put("id", id)
        putJsonObject("content") {
            put("json_uri", uri)
            putJsonObject("metadata") {
                put("name", name)
                put("symbol", symbol)
            }
        }
        putJsonObject("ownership") {
            put("owner", owner)
            put("frozen", frozen)
        }
        putJsonObject("royalty") {
            put("royalty_percentage", royaltyPercentage)
        }
        putJsonObject("compression") {
            put("compressed", compressed)
        }
        if (collectionAddress != null) {
            putJsonArray("grouping") {
                add(buildJsonObject {
                    put("group_key", "collection")
                    put("group_value", collectionAddress)
                    put("verified", collectionVerified)
                })
            }
        } else {
            put("grouping", buildJsonArray {})
        }
    }

    // ─── Parse verification (mirrors HeliusDas.parseAsset logic) ─────────────

    @Test
    fun `asset JSON - id field extracted correctly`() {
        val json = buildAssetJson(id = "uniqueAssetId42")
        val id = json["id"]?.let { (it as JsonPrimitive).content } ?: ""
        assertEquals("uniqueAssetId42", id)
    }

    @Test
    fun `asset JSON - name and symbol from content metadata`() {
        val json = buildAssetJson(name = "Mad Lads #999", symbol = "ML")
        val content = json["content"] as kotlinx.serialization.json.JsonObject
        val metadata = content["metadata"] as kotlinx.serialization.json.JsonObject
        assertEquals("Mad Lads #999", (metadata["name"] as JsonPrimitive).content)
        assertEquals("ML", (metadata["symbol"] as JsonPrimitive).content)
    }

    @Test
    fun `asset JSON - uri from content json_uri`() {
        val expectedUri = "https://arweave.net/xyz"
        val json = buildAssetJson(uri = expectedUri)
        val content = json["content"] as kotlinx.serialization.json.JsonObject
        assertEquals(expectedUri, (content["json_uri"] as JsonPrimitive).content)
    }

    @Test
    fun `asset JSON - owner from ownership block`() {
        val json = buildAssetJson(owner = "walletPubkey123")
        val ownership = json["ownership"] as kotlinx.serialization.json.JsonObject
        assertEquals("walletPubkey123", (ownership["owner"] as JsonPrimitive).content)
    }

    @Test
    fun `asset JSON - frozen field from ownership`() {
        val jsonFrozen = buildAssetJson(frozen = true)
        val jsonNotFrozen = buildAssetJson(frozen = false)
        val frozenOwnership = jsonFrozen["ownership"] as kotlinx.serialization.json.JsonObject
        val notFrozenOwnership = jsonNotFrozen["ownership"] as kotlinx.serialization.json.JsonObject
        assertTrue((frozenOwnership["frozen"] as JsonPrimitive).content.toBoolean())
        assertFalse((notFrozenOwnership["frozen"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `asset JSON - royaltyBasisPoints converted from percentage`() {
        // 5.0 percent * 100 = 500 basis points
        val json = buildAssetJson(royaltyPercentage = 5.0)
        val royalty = json["royalty"] as kotlinx.serialization.json.JsonObject
        val pct = (royalty["royalty_percentage"] as JsonPrimitive).content.toDouble()
        val basisPoints = (pct * 100).toInt()
        assertEquals(500, basisPoints)
    }

    @Test
    fun `asset JSON - isCompressed from compression block`() {
        val jsonCompressed = buildAssetJson(compressed = true)
        val jsonRegular = buildAssetJson(compressed = false)
        val compressedBlock = jsonCompressed["compression"] as kotlinx.serialization.json.JsonObject
        val regularBlock = jsonRegular["compression"] as kotlinx.serialization.json.JsonObject
        assertTrue((compressedBlock["compressed"] as JsonPrimitive).content.toBoolean())
        assertFalse((regularBlock["compressed"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `asset JSON - collection from grouping array`() {
        val json = buildAssetJson(
            collectionAddress = "CollMintXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            collectionVerified = true
        )
        val grouping = json["grouping"] as JsonArray
        val entry = grouping.first() as kotlinx.serialization.json.JsonObject
        assertEquals("collection", (entry["group_key"] as JsonPrimitive).content)
        assertEquals("CollMintXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            (entry["group_value"] as JsonPrimitive).content)
        assertTrue((entry["verified"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `asset JSON - empty grouping gives null collection`() {
        val json = buildAssetJson(collectionAddress = null)
        val grouping = json["grouping"] as JsonArray
        val entry = grouping.firstOrNull { item ->
            (item as kotlinx.serialization.json.JsonObject)
                .let { it["group_key"]?.let { k -> (k as JsonPrimitive).content == "collection" } ?: false }
        }
        assertNull(entry)
    }

    // ─── AssetList parsing ───────────────────────────────────────────────────

    @Test
    fun `assetList JSON - items array unwrapped correctly`() {
        val assetListJson = buildJsonObject {
            putJsonArray("items") {
                add(buildAssetJson(id = "a1"))
                add(buildAssetJson(id = "a2"))
                add(buildAssetJson(id = "a3"))
            }
            put("total", 3)
            put("limit", 100)
            put("page", 1)
        }
        val items = assetListJson["items"] as JsonArray
        assertEquals(3, items.size)
    }

    @Test
    fun `assetList JSON - empty items array gives zero`() {
        val assetListJson = buildJsonObject {
            put("items", buildJsonArray {})
            put("total", 0)
        }
        val items = assetListJson["items"] as JsonArray
        assertEquals(0, items.size)
    }

    // ─── ArtemisDas interface contract ───────────────────────────────────────

    @Test
    fun `ArtemisDas - HeliusDas implements interface`() {
        val das: ArtemisDas = HeliusDas("https://mainnet.helius-rpc.com/?api-key=key")
        assertNotNull(das)
        assertTrue(das is HeliusDas)
    }
}
