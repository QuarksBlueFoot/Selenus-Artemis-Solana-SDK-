/*
 * Drop-in source compatibility with foundation.metaplex.readapi (solana-kmp 0.2.x).
 *
 * The Metaplex solana-kmp `readapi` module is the Kotlin-first DAS client that
 * Metaplex ships alongside the RPC module. This shim re-publishes the same
 * types at the same fully qualified package path and delegates to the Artemis
 * native DAS layer (`ArtemisDas` / `HeliusDas`), so apps that imported
 * `foundation.metaplex.readapi.ReadApiDecorator` continue to compile and pick
 * up the Artemis fallback + composite reliability features automatically.
 */
package foundation.metaplex.readapi

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.HeliusDas
import foundation.metaplex.solanapublickeys.PublicKey

/**
 * Read API interface exposing the standard DAS query shape.
 *
 * Matches the upstream `ReadApiInterface` method list. Return types use the
 * `ReadApiAsset` model below so callers that destructure on upstream field
 * names keep working.
 */
interface ReadApiInterface {
    suspend fun getAsset(id: String): ReadApiAsset?
    suspend fun getAssetsByOwner(input: GetAssetsByOwnerRpcInput): ReadApiAssetList
    suspend fun getAssetsByGroup(input: GetAssetsByGroupRpcInput): ReadApiAssetList
    suspend fun getAssetProof(id: String): GetAssetProofRpcResponse?
}

/**
 * Concrete implementation that fronts the Artemis DAS client.
 *
 * Users construct it exactly like upstream: `ReadApiDecorator(rpcUrl)` where
 * [rpcUrl] is a DAS-compatible endpoint (Helius, Triton, QuickNode).
 */
class ReadApiDecorator(rpcUrl: String) : ReadApiInterface {

    private val das: ArtemisDas = HeliusDas(rpcUrl)

    override suspend fun getAsset(id: String): ReadApiAsset? =
        das.asset(id)?.let { asset -> asset.toReadApi() }

    override suspend fun getAssetsByOwner(input: GetAssetsByOwnerRpcInput): ReadApiAssetList {
        val owner = com.selenus.artemis.runtime.Pubkey.fromBase58(input.ownerAddress)
        val assets = das.assetsByOwner(owner, input.page, input.limit ?: 100)
        return ReadApiAssetList(
            total = assets.size,
            limit = input.limit ?: 100,
            items = assets.map { it.toReadApi() },
            page = input.page,
            before = input.before,
            after = input.after
        )
    }

    override suspend fun getAssetsByGroup(input: GetAssetsByGroupRpcInput): ReadApiAssetList {
        val assets = das.assetsByCollection(input.groupValue, input.page ?: 1, input.limit ?: 100)
        return ReadApiAssetList(
            total = assets.size,
            limit = input.limit ?: 100,
            items = assets.map { it.toReadApi() },
            page = input.page ?: 1,
            before = input.before,
            after = input.after
        )
    }

    override suspend fun getAssetProof(id: String): GetAssetProofRpcResponse? {
        // Artemis DAS does not expose proof fetching through the generic
        // ArtemisDas interface. Users who need proofs can drop down to
        // HeliusDas and call getAssetProof directly.
        return null
    }

    private fun com.selenus.artemis.cnft.das.DigitalAsset.toReadApi(): ReadApiAsset = ReadApiAsset(
        id = id,
        interfaceType = if (isCompressed) "V1_NFT" else "ProgrammableNFT",
        ownership = ReadApiAssetOwnership(
            owner = owner,
            ownershipModel = "single",
            delegated = false,
            delegate = null,
            frozen = frozen
        ),
        mutable = true,
        authorities = emptyList(),
        content = ReadApiAssetContent(
            uri = uri,
            json_uri = uri,
            metadata = ReadApiAssetMetadata(name = name, symbol = symbol)
        ),
        royalty = ReadApiAssetRoyalty(
            royalty_model = "creators",
            basis_points = royaltyBasisPoints,
            primary_sale_happened = true
        ),
        creators = emptyList(),
        grouping = collectionAddress?.let {
            listOf(ReadApiAssetGrouping(group_key = "collection", group_value = it, verified = collectionVerified))
        } ?: emptyList(),
        compression = ReadApiAssetCompression(
            eligible = false,
            compressed = isCompressed,
            data_hash = null,
            creator_hash = null,
            asset_hash = null,
            tree = null,
            seq = null,
            leaf_id = null
        )
    )
}

/** DAS asset record matching upstream shape. */
data class ReadApiAsset(
    val id: String,
    val interfaceType: String,
    val ownership: ReadApiAssetOwnership,
    val mutable: Boolean,
    val authorities: List<ReadApiAssetAuthority>,
    val content: ReadApiAssetContent,
    val royalty: ReadApiAssetRoyalty,
    val creators: List<ReadApiAssetCreator>,
    val grouping: List<ReadApiAssetGrouping>,
    val compression: ReadApiAssetCompression
)

data class ReadApiAssetOwnership(
    val owner: String,
    val ownershipModel: String,
    val delegated: Boolean,
    val delegate: String?,
    val frozen: Boolean
)

data class ReadApiAssetAuthority(val address: String, val scopes: List<String>)

data class ReadApiAssetContent(
    val uri: String,
    val json_uri: String,
    val metadata: ReadApiAssetMetadata
)

data class ReadApiAssetMetadata(val name: String, val symbol: String)

data class ReadApiAssetRoyalty(
    val royalty_model: String,
    val basis_points: Int,
    val primary_sale_happened: Boolean
)

data class ReadApiAssetCreator(val address: String, val verified: Boolean, val share: Int)

data class ReadApiAssetGrouping(val group_key: String, val group_value: String, val verified: Boolean)

data class ReadApiAssetCompression(
    val eligible: Boolean,
    val compressed: Boolean,
    val data_hash: String?,
    val creator_hash: String?,
    val asset_hash: String?,
    val tree: String?,
    val seq: Long?,
    val leaf_id: Long?
)

/** Paginated asset list response. */
data class ReadApiAssetList(
    val total: Int,
    val limit: Int,
    val items: List<ReadApiAsset>,
    val page: Int = 1,
    val before: String? = null,
    val after: String? = null,
    val errors: List<ReadApiRpcResponseError>? = null
)

/** Input to `getAssetsByOwner`. */
data class GetAssetsByOwnerRpcInput(
    val ownerAddress: String,
    val page: Int = 1,
    val limit: Int? = null,
    val before: String? = null,
    val after: String? = null,
    val sortBy: ReadApiParamAssetSortBy? = null
)

/** Input to `getAssetsByGroup`. */
data class GetAssetsByGroupRpcInput(
    val groupKey: String,
    val groupValue: String,
    val page: Int? = null,
    val limit: Int? = null,
    val before: String? = null,
    val after: String? = null,
    val sortBy: ReadApiParamAssetSortBy? = null
)

/** Sort parameter matching upstream enum. */
data class ReadApiParamAssetSortBy(val sortBy: String, val sortDirection: String = "desc")

/** `getAssetProof` response record. */
data class GetAssetProofRpcResponse(
    val root: String,
    val proof: List<String>,
    val nodeIndex: Long?,
    val leaf: String,
    val treeId: String?
)

/** Error record returned inside the `errors` field of a `ReadApiAssetList`. */
data class ReadApiRpcResponseError(val id: String, val error: String)

/** Convenience alias so upstream's `JsonMetadata` import continues to resolve. */
typealias JsonMetadata = ReadApiAssetMetadata

/**
 * Asset interface enum matching upstream. Users commonly pattern-match on it.
 */
enum class ReadApiAssetInterface(val value: String) {
    V1_NFT("V1_NFT"),
    V1_PNFT("ProgrammableNFT"),
    LEGACY_NFT("LEGACY_NFT"),
    FUNGIBLE_TOKEN("FungibleToken"),
    IDENTITY("Identity"),
    EXECUTABLE("Executable"),
    CUSTOM("Custom");
}
