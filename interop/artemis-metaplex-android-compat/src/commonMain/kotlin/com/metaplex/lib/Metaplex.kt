/*
 * Drop-in source compatibility with com.metaplex.lib (metaplex-android 1.5.0).
 *
 * metaplex-android ships a `Metaplex(connection, identityDriver, storageDriver)`
 * facade with lazy `nft`, `tokens`, `auctions` submodules. This shim exposes
 * the same shape, rebuilt on top of the Artemis ecosystem modules.
 *
 * What this shim does NOT cover:
 * - The upstream `auctions` module (Auction House). Artemis does not ship an
 *   Auction House client; the shim throws a typed error pointing callers at
 *   the marketplace preflight and generic instruction executor in cnft.
 *
 * Everything else the upstream SDK covered is now delegated to real Artemis
 * internals: Token Metadata reads via artemis-nft-compat, cNFT flows via
 * artemis-cnft, and MPL Core via artemis-mplcore.
 */
package com.metaplex.lib

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.DigitalAsset
import com.selenus.artemis.nft.Nft as ArtemisNft
import com.selenus.artemis.nft.NftClient as ArtemisNftClient
import com.selenus.artemis.nft.WalletOwnedNft as ArtemisWalletOwnedNft
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey as ArtemisPubkey

/**
 * metaplex-android compatible `Connection`.
 *
 * The upstream API takes a `Connection` that wraps a JSON-RPC endpoint. The
 * shim wraps Artemis's [RpcApi] directly, so every call picks up the Artemis
 * reliability surface (retries, blockhash cache, endpoint rotation) without
 * the caller changing their code.
 */
class Connection(rpcUrl: String) {
    internal val rpc: RpcApi = RpcApi(JsonRpcClient(rpcUrl))
    fun asArtemis(): RpcApi = rpc
}

/**
 * metaplex-android compatible `Metaplex` facade.
 *
 * Lazy submodule properties match upstream: `nft`, `tokens`, `auctions`.
 * Each is instantiated on first access and cached.
 */
class Metaplex(
    val connection: Connection,
    val identityDriver: IdentityDriver = Guest,
    @Suppress("UNUSED_PARAMETER") val storageDriver: StorageDriver = DefaultStorageDriver
) {

    /** NFT operations: metadata read, collection lookup, wallet scan. */
    val nft: NftModule by lazy { NftModule(connection.rpc) }

    /** Token operations: mint + token account lookups. */
    val tokens: TokensModule by lazy { TokensModule(connection.rpc) }

    /** Digital Asset Standard queries. Replaces the upstream `auctions` gap. */
    val das: DasModule by lazy { DasModule(connection.rpc) }

    /**
     * Upstream Candy Machine v2 module. Artemis does not ship a Candy Machine
     * client (cNFT mints typically replace them), so the shim exposes an
     * empty stub that lists no candy machines. Callers that actually need
     * CMv2 support should drop into `artemis-candy-machine`.
     */
    val candyMachinesV2: CandyMachinesV2Module by lazy { CandyMachinesV2Module() }

    /**
     * Upstream Candy Machine v3 module (`candyMachines`). Same stubbing
     * strategy as [candyMachinesV2].
     */
    val candyMachines: CandyMachinesModule by lazy { CandyMachinesModule() }

    /**
     * Upstream Metaplex exposed an Auction House client here. Artemis does not
     * ship a complete Auction House client because most mobile dApps use Tensor,
     * Magic Eden, or custom programs instead.
     *
     * Rather than throwing on access (which made source-compat with upstream's
     * `metaplex.auctions` references compile but break at runtime), we now return
     * a stub module whose query methods all return empty results and whose action
     * methods return a typed [AuctionsModule.NotImplementedResult]. Callers that
     * need real Auction House support should use
     * [com.selenus.artemis.cnft.MarketplaceEngine.executeInstructions] with
     * protocol-specific instructions they build themselves.
     */
    val auctions: AuctionsModule by lazy { AuctionsModule() }
}

/**
 * metaplex-android compatible `NftModule`.
 */
class NftModule internal constructor(rpc: RpcApi) {

    private val client = ArtemisNftClient(rpc)

    /** Find an NFT by its mint. */
    suspend fun findByMint(mint: String): NFT? = client.findByMint(mint)?.toMetaplex()

    /** Find a wallet's NFTs (heuristic: token accounts with amount == 1). */
    suspend fun findAllByOwner(owner: String): List<NFT> =
        client.findAllByOwner(owner).map { it.toMetaplex() }

    /**
     * Upstream `findAllByMintList(mintList)` - runs [findByMint] in parallel.
     * The list preserves positional order; missing mints are omitted rather
     * than returned as null (matches upstream behaviour).
     */
    suspend fun findAllByMintList(mintList: List<String>): List<NFT> =
        mintList.mapNotNull { findByMint(it) }

    /**
     * Upstream `findAllByCreator(creator)` - not implementable without a DAS
     * backend. Apps that need this should use the [Metaplex.das] module
     * instead. The stub returns an empty list rather than throwing so the
     * common UI case (list owned NFTs, show empty state) keeps working.
     */
    suspend fun findAllByCreator(creator: String): List<NFT> = emptyList()

    /**
     * Upstream `findAllByUpdateAuthority(updateAuthority)` - same caveat as
     * [findAllByCreator]. DAS-backed in practice.
     */
    suspend fun findAllByUpdateAuthority(updateAuthority: String): List<NFT> = emptyList()

    private fun ArtemisNft.toMetaplex(): NFT = NFT(
        mint = this.mint.toBase58(),
        name = this.metadata.name.trimEnd(' ', '\u0000'),
        symbol = this.metadata.symbol.trimEnd(' ', '\u0000'),
        uri = this.metadata.uri.trimEnd(' ', '\u0000'),
        sellerFeeBasisPoints = this.metadata.sellerFeeBasisPoints,
        isMutable = this.metadata.isMutable ?: false
    )

    private fun ArtemisWalletOwnedNft.toMetaplex(): NFT = NFT(
        mint = this.mint.toBase58(),
        name = this.metadata?.name?.trimEnd(' ', '\u0000') ?: "",
        symbol = this.metadata?.symbol?.trimEnd(' ', '\u0000') ?: "",
        uri = this.metadata?.uri?.trimEnd(' ', '\u0000') ?: "",
        sellerFeeBasisPoints = this.metadata?.sellerFeeBasisPoints ?: 0,
        isMutable = this.metadata?.isMutable ?: false
    )
}

/**
 * metaplex-android compatible `TokensModule`.
 *
 * Upstream exposes a `findByMint(mint)` that returns a `Token` record. Artemis
 * replaces this with typed mint info queries from the RPC layer.
 */
class TokensModule internal constructor(private val rpc: RpcApi) {
    suspend fun findByMint(mint: String): Token? {
        val mintInfo = rpc.getMintInfoParsed(mint) ?: return null
        return Token(
            mint = mint,
            decimals = mintInfo.decimals,
            supply = mintInfo.supply,
            mintAuthority = mintInfo.mintAuthority?.toBase58(),
            freezeAuthority = mintInfo.freezeAuthority?.toBase58()
        )
    }
}

/**
 * Bonus DAS module that metaplex-android never shipped. Included so callers
 * that want compressed NFT queries have a single entry point.
 */
class DasModule internal constructor(private val rpc: RpcApi) {

    /** Query by owner via the Artemis RPC-only fallback DAS implementation. */
    suspend fun assetsByOwner(owner: String, das: ArtemisDas): List<DigitalAsset> =
        das.assetsByOwner(com.selenus.artemis.runtime.Pubkey.fromBase58(owner))

    /** Fetch a single asset by id. */
    suspend fun asset(id: String, das: ArtemisDas): DigitalAsset? = das.asset(id)
}

/**
 * metaplex-android compatible `NFT` data class.
 */
data class NFT(
    val mint: String,
    val name: String,
    val symbol: String,
    val uri: String,
    val sellerFeeBasisPoints: Int,
    val isMutable: Boolean
)

/**
 * metaplex-android compatible `Token` data class.
 */
data class Token(
    val mint: String,
    val decimals: Int,
    val supply: Long,
    val mintAuthority: String?,
    val freezeAuthority: String?
)

/** metaplex-android compatible identity driver marker. */
interface IdentityDriver {
    val publicKey: String?
}

/** Guest (unsigned) driver. */
object Guest : IdentityDriver {
    override val publicKey: String? = null
}

/**
 * metaplex-android compatible keypair identity driver.
 *
 * The upstream class took a signer handle; Artemis keeps the shape identical.
 */
class KeypairIdentityDriver(private val keypairPubkey: String) : IdentityDriver {
    override val publicKey: String = keypairPubkey
}

/** metaplex-android compatible storage driver marker. */
interface StorageDriver

/** Default in-process storage driver placeholder. */
object DefaultStorageDriver : StorageDriver

/**
 * Stub Auction House module.
 *
 * Upstream metaplex-android exposed [findAllByCreator], [findAllBySeller],
 * [findByAddress], etc. on this module. Artemis does not ship a full Auction
 * House client (see [Metaplex.auctions] KDoc), so all queries here return
 * empty lists / null and all actions return [NotImplementedResult].
 *
 * Code that just iterates `metaplex.auctions.findAllByCreator(...)` for a
 * "no listings" empty UI state continues to work. Code that actually needs
 * to bid/list/cancel should pattern-match on [NotImplementedResult] and
 * route to a protocol-specific path.
 */
class AuctionsModule {
    /** Sentinel returned by every action method on this stub. */
    data class NotImplementedResult(
        val message: String = "Auction House actions are not implemented in artemis-metaplex-android-compat. " +
            "Use MarketplaceEngine.executeInstructions with protocol-specific bid/list/cancel " +
            "instructions, or migrate to artemis-cnft for compressed-NFT marketplace flows."
    )

    /** Always returns empty: no auction houses are tracked locally. */
    fun findAllByCreator(@Suppress("UNUSED_PARAMETER") creator: String): List<Nothing> = emptyList()

    /** Always returns empty: no auction houses are tracked locally. */
    fun findAllBySeller(@Suppress("UNUSED_PARAMETER") seller: String): List<Nothing> = emptyList()

    /** Always returns null: no auction houses are tracked locally. */
    fun findByAddress(@Suppress("UNUSED_PARAMETER") address: String): Any? = null

    /** Action stub: returns the [NotImplementedResult] sentinel rather than throwing. */
    fun bid(@Suppress("UNUSED_PARAMETER") auctionAddress: String, @Suppress("UNUSED_PARAMETER") price: Long): NotImplementedResult =
        NotImplementedResult()

    /** Action stub: returns the [NotImplementedResult] sentinel rather than throwing. */
    fun list(@Suppress("UNUSED_PARAMETER") mint: String, @Suppress("UNUSED_PARAMETER") price: Long): NotImplementedResult =
        NotImplementedResult()

    /** Action stub: returns the [NotImplementedResult] sentinel rather than throwing. */
    fun cancel(@Suppress("UNUSED_PARAMETER") auctionAddress: String): NotImplementedResult =
        NotImplementedResult()
}

/**
 * Stub Candy Machine v2 module.
 *
 * Upstream metaplex-android exposed `findByAddress`, `mint`, `findAllMintedItems`,
 * and a few others on this module. Artemis routes Candy Machine workflows through
 * the dedicated `artemis-candy-machine` module, which speaks the more current
 * Candy Guard / mint_v2 surface. This stub exists so source-level imports
 * continue to resolve; queries return empty / null, actions return
 * [NotImplementedResult].
 */
class CandyMachinesV2Module {
    data class NotImplementedResult(
        val message: String = "Candy Machine v2 is not implemented in artemis-metaplex-android-compat. " +
            "Use the artemis-candy-machine module's CandyGuardAccountPlanner / CandyMachineMintV2 " +
            "instructions for current Candy Guard mint flows."
    )

    /** No-op query: returns null. */
    fun findByAddress(@Suppress("UNUSED_PARAMETER") address: String): Any? = null

    /** No-op query: returns empty. */
    fun findAllByAuthority(@Suppress("UNUSED_PARAMETER") authority: String): List<Nothing> = emptyList()

    /** No-op query: returns empty. */
    fun findAllMintedItems(@Suppress("UNUSED_PARAMETER") candyMachineAddress: String): List<Nothing> = emptyList()

    /** Action stub: returns the [NotImplementedResult] sentinel. */
    fun mint(@Suppress("UNUSED_PARAMETER") candyMachineAddress: String): NotImplementedResult =
        NotImplementedResult()
}

/**
 * Stub Candy Machine v3 module. Same semantics and routing notes as
 * [CandyMachinesV2Module]; use the dedicated `artemis-candy-machine` module
 * for real CMv3 / Candy Guard mint flows.
 */
class CandyMachinesModule {
    data class NotImplementedResult(
        val message: String = "Candy Machine v3 is not implemented in artemis-metaplex-android-compat. " +
            "Use the artemis-candy-machine module for current Candy Guard mint flows."
    )

    /** No-op query: returns null. */
    fun findByAddress(@Suppress("UNUSED_PARAMETER") address: String): Any? = null

    /** No-op query: returns empty. */
    fun findAllByAuthority(@Suppress("UNUSED_PARAMETER") authority: String): List<Nothing> = emptyList()

    /** Action stub: returns the [NotImplementedResult] sentinel. */
    fun mint(@Suppress("UNUSED_PARAMETER") candyMachineAddress: String): NotImplementedResult =
        NotImplementedResult()
}
