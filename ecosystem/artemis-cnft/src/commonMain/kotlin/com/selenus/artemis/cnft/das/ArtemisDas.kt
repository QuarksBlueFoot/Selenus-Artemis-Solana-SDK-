package com.selenus.artemis.cnft.das

import com.selenus.artemis.runtime.Pubkey

/**
 * DigitalAsset - typed representation of a DAS (Digital Asset Standard) asset.
 *
 * Maps the subset of DAS fields used by most applications. Full raw JSON is
 * preserved in [raw] for callers that need fields not surfaced here.
 */
data class DigitalAsset(
    /** The asset's address (mint for SPL NFTs, asset ID for cNFTs). */
    val id: String,
    /** Human-readable name from the token metadata. */
    val name: String,
    /** Symbol from the token metadata. */
    val symbol: String,
    /** URI pointing to the off-chain JSON metadata. */
    val uri: String,
    /** Current owner's wallet public key. */
    val owner: String,
    /** Royalty basis points (e.g. 500 = 5%). */
    val royaltyBasisPoints: Int,
    /** Whether this asset is a compressed NFT (cNFT / Bubblegum). */
    val isCompressed: Boolean,
    /** Whether this asset is currently frozen (transfer restricted). */
    val frozen: Boolean,
    /** Collection address this asset belongs to, if any. */
    val collectionAddress: String?,
    /** Collection verified flag. */
    val collectionVerified: Boolean
)

/**
 * ArtemisDas - Digital Asset Standard query interface.
 *
 * Abstracts the Helius DAS API behind a typed surface so apps can
 * switch RPC providers without changing call sites.
 *
 * ```kotlin
 * val das: ArtemisDas = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=...")
 *
 * val nfts = das.assetsByOwner(session.publicKey)
 * val asset = das.asset("assetId123")
 * ```
 */
interface ArtemisDas {

    /**
     * Fetch all digital assets owned by [owner].
     *
     * @param owner The wallet whose assets to enumerate
     * @param page  Page number (1-indexed)
     * @param limit Maximum results per page (max 1000 per DAS spec)
     */
    suspend fun assetsByOwner(
        owner: Pubkey,
        page: Int = 1,
        limit: Int = 100
    ): List<DigitalAsset>

    /**
     * Fetch a single asset by its ID.
     */
    suspend fun asset(id: String): DigitalAsset?

    /**
     * Fetch all assets in a given collection.
     *
     * @param collectionAddress The collection's mint/group key
     * @param page  Page number (1-indexed)
     * @param limit Maximum results per page
     */
    suspend fun assetsByCollection(
        collectionAddress: String,
        page: Int = 1,
        limit: Int = 100
    ): List<DigitalAsset>
}
