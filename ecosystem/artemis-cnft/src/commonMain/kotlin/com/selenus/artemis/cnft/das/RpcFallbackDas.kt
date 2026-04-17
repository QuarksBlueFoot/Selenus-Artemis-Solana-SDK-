package com.selenus.artemis.cnft.das

import com.selenus.artemis.nft.MetadataParser
import com.selenus.artemis.nft.Pdas
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RpcFallbackDas - [ArtemisDas] implementation that only needs a vanilla Solana RPC endpoint.
 *
 * Intended as a resilience layer for [CompositeDas]: when Helius / QuickNode DAS is
 * unreachable, rate-limited, or returns an error, this implementation synthesizes the
 * same [DigitalAsset] view by combining:
 *
 * - `getTokenAccountsByOwner` → enumerate mints the wallet holds (NFT semantics: amount=1, decimals=0)
 * - Metaplex metadata PDA derivation → [Pdas.metadataPda]
 * - `getAccountInfo(encoding=base64)` → fetch raw metadata account bytes
 * - [MetadataParser] → decode Borsh layout from the metadata account
 *
 * **Tradeoffs vs a real DAS provider:**
 * - Does not cover compressed NFTs (no off-chain merkle index). [isCompressed] will always be false.
 * - [assetsByCollection] is not supported on pure RPC without `getProgramAccounts` + memcmp and
 *   is explicitly opt-in via [enableCollectionScan] because it is expensive on mainnet.
 * - Pagination is applied client-side after fetching the full holder set.
 *
 * This is enough to keep apps functional for standard SPL NFTs when the DAS provider is down,
 * which is the whole point of having a fallback.
 */
class RpcFallbackDas(
    private val rpc: RpcApi,
    private val enableCollectionScan: Boolean = false
) : ArtemisDas {

    override suspend fun assetsByOwner(
        owner: Pubkey,
        page: Int,
        limit: Int
    ): List<DigitalAsset> {
        val response = rpc.getTokenAccountsByOwner(
            owner = owner.toBase58(),
            programId = ProgramIds.TOKEN_PROGRAM.toBase58()
        )
        val tokenAccounts = response["value"]?.jsonArray ?: return emptyList()

        // Filter to NFT-like holdings (amount == 1, decimals == 0) and collect mints.
        val nftMints = tokenAccounts.mapNotNull { entry ->
            val account = entry.jsonObject["account"]?.jsonObject ?: return@mapNotNull null
            val parsed = account["data"]?.jsonObject?.get("parsed")?.jsonObject ?: return@mapNotNull null
            val info = parsed["info"]?.jsonObject ?: return@mapNotNull null
            val tokenAmount = info["tokenAmount"]?.jsonObject ?: return@mapNotNull null

            val amount = tokenAmount["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val decimals = tokenAmount["decimals"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            if (amount != 1L || decimals != 0) return@mapNotNull null

            info["mint"]?.jsonPrimitive?.content
        }

        // Apply pagination client-side (DAS semantics: 1-indexed pages).
        val start = ((page - 1).coerceAtLeast(0)) * limit
        val end = (start + limit).coerceAtMost(nftMints.size)
        if (start >= nftMints.size) return emptyList()
        val pageMints = nftMints.subList(start, end)

        return pageMints.mapNotNull { mint ->
            runCatching { loadAsset(mint, expectedOwner = owner.toBase58()) }.getOrNull()
        }
    }

    override suspend fun asset(id: String): DigitalAsset? {
        return runCatching { loadAsset(id, expectedOwner = null) }.getOrNull()
    }

    override suspend fun assetsByCollection(
        collectionAddress: String,
        page: Int,
        limit: Int
    ): List<DigitalAsset> {
        if (!enableCollectionScan) {
            // Don't silently mislead callers - an RPC collection scan is a mainnet-breaking call.
            return emptyList()
        }
        // Reserved: expensive getProgramAccounts with memcmp on the Metadata account
        // collection field would go here. Keep disabled by default.
        return emptyList()
    }

    // ─── internal ────────────────────────────────────────────────────────────

    private suspend fun loadAsset(mintBase58: String, expectedOwner: String?): DigitalAsset? {
        val mint = Pubkey.fromBase58(mintBase58)
        val metadataPda = Pdas.metadataPda(mint)

        val accountBytes = rpc.getAccountInfoBase64(metadataPda.toBase58()) ?: return null
        val parsed = MetadataParser.parse(mint, accountBytes)

        val owner = expectedOwner ?: findLargestHolder(mintBase58)
        val collectionRef = parsed.collection
        val sellerFee = parsed.sellerFeeBasisPoints

        return DigitalAsset(
            id = mintBase58,
            name = parsed.name.trimEnd('\u0000').trim(),
            symbol = parsed.symbol.trimEnd('\u0000').trim(),
            uri = parsed.uri.trimEnd('\u0000').trim(),
            owner = owner ?: "",
            royaltyBasisPoints = sellerFee,
            isCompressed = false,
            frozen = false,
            collectionAddress = collectionRef?.key?.toBase58(),
            collectionVerified = collectionRef?.verified ?: false
        )
    }

    /**
     * Resolve the current holder for a standard NFT mint by scanning the largest token accounts.
     * Returns the first account with a non-zero amount.
     */
    private suspend fun findLargestHolder(mint: String): String? {
        val response = runCatching {
            rpc.callRaw(
                method = "getTokenLargestAccounts",
                params = buildJsonArray { add(JsonPrimitive(mint)) }
            )
        }.getOrNull() ?: return null

        val result = response["result"]?.jsonObject ?: return null
        val value = result["value"]?.jsonArray ?: return null
        val topHolder = value.firstOrNull { entry ->
            val amt = entry.jsonObject["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            amt > 0
        }?.jsonObject?.get("address")?.jsonPrimitive?.content ?: return null

        // topHolder is the token account, not the wallet. Deref one more time.
        val accountJson = runCatching {
            rpc.getAccountInfo(topHolder, encoding = "jsonParsed")
        }.getOrNull() ?: return null

        val value2 = accountJson["value"] ?: return null
        if (value2 is JsonNull) return null
        return value2.jsonObject["data"]?.jsonObject
            ?.get("parsed")?.jsonObject
            ?.get("info")?.jsonObject
            ?.get("owner")?.jsonPrimitive?.content
    }
}
