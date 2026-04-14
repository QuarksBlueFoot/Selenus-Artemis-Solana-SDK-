package com.selenus.artemis.cnft.das

import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * HeliusDas — [ArtemisDas] implementation backed by the Helius DAS API.
 *
 * Uses the Helius JSON-RPC DAS endpoints (`getAssetsByOwner`, `getAsset`,
 * `getAssetsByGroup`). Compatible with any DAS-compliant provider
 * (QuickNode, Triton, etc.) that exposes the same method names.
 *
 * ```kotlin
 * val das = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=<KEY>")
 * val nfts = das.assetsByOwner(walletPubkey)
 * ```
 */
class HeliusDas(rpcUrl: String) : ArtemisDas {

    private val rpc = JsonRpcClient(rpcUrl)

    override suspend fun assetsByOwner(
        owner: Pubkey,
        page: Int,
        limit: Int
    ): List<DigitalAsset> {
        val params = buildJsonObject {
            put("ownerAddress", owner.toBase58())
            put("page", page)
            put("limit", limit)
        }
        val result = rpc.call("getAssetsByOwner", params)
        return parseAssetList(result["result"])
    }

    override suspend fun asset(id: String): DigitalAsset? {
        val params = buildJsonObject { put("id", id) }
        val result = rpc.call("getAsset", params)
        val item = result["result"] ?: return null
        if (item is JsonNull) return null
        return parseAsset(item.jsonObject)
    }

    override suspend fun assetsByCollection(
        collectionAddress: String,
        page: Int,
        limit: Int
    ): List<DigitalAsset> {
        val params = buildJsonObject {
            put("groupKey", "collection")
            put("groupValue", collectionAddress)
            put("page", page)
            put("limit", limit)
        }
        val result = rpc.call("getAssetsByGroup", params)
        return parseAssetList(result["result"])
    }

    // ─── parsing ─────────────────────────────────────────────────────────────

    private fun parseAssetList(element: JsonElement?): List<DigitalAsset> {
        if (element == null || element is JsonNull) return emptyList()
        // DAS returns { items: [...], total, limit, page }
        val items: JsonArray = when {
            element is JsonObject -> element["items"]?.jsonArray ?: return emptyList()
            element is JsonArray -> element
            else -> return emptyList()
        }
        return items.mapNotNull { item ->
            runCatching { parseAsset(item.jsonObject) }.getOrNull()
        }
    }

    private fun parseAsset(obj: JsonObject): DigitalAsset {
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        val content = obj["content"]?.jsonObject
        val metadata = content?.get("metadata")?.jsonObject
        val name = metadata?.get("name")?.jsonPrimitive?.content ?: ""
        val symbol = metadata?.get("symbol")?.jsonPrimitive?.content ?: ""
        val uri = content?.get("json_uri")?.jsonPrimitive?.content ?: ""

        val ownership = obj["ownership"]?.jsonObject
        val owner = ownership?.get("owner")?.jsonPrimitive?.content ?: ""
        val frozen = ownership?.get("frozen")?.jsonPrimitive?.content?.toBoolean() ?: false

        val royalty = obj["royalty"]?.jsonObject
        val basisPoints = royalty?.get("royalty_percentage")?.jsonPrimitive?.content
            ?.toDoubleOrNull()?.let { (it * 100).toInt() } ?: 0

        val compressed = obj["compression"]?.jsonObject
            ?.get("compressed")?.jsonPrimitive?.content?.toBoolean() ?: false

        val grouping = obj["grouping"]?.jsonArray
        val collectionEntry = grouping?.firstOrNull { entry ->
            entry.jsonObject["group_key"]?.jsonPrimitive?.content == "collection"
        }?.jsonObject
        val collectionAddress = collectionEntry?.get("group_value")?.jsonPrimitive?.content
        val collectionVerified = collectionEntry?.get("verified")?.jsonPrimitive?.content
            ?.toBoolean() ?: false

        return DigitalAsset(
            id = id,
            name = name,
            symbol = symbol,
            uri = uri,
            owner = owner,
            royaltyBasisPoints = basisPoints,
            isCompressed = compressed,
            frozen = frozen,
            collectionAddress = collectionAddress,
            collectionVerified = collectionVerified
        )
    }
}
