package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.DasClient
import com.selenus.artemis.cnft.das.DasProofParser
import com.selenus.artemis.cnft.das.DigitalAsset
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.vtx.TxEngine
import com.selenus.artemis.wallet.SignTxRequest
import com.selenus.artemis.wallet.WalletAdapter

/**
 * MarketplaceEngine - high-level interface for listing and buying NFTs.
 *
 * Abstracts cNFT (Bubblegum) transfer flows into simple `list()` and `buy()` surfaces.
 * For regular SPL NFTs, direct token-transfer instructions are composed automatically.
 *
 * ```kotlin
 * val marketplace = MarketplaceEngine(rpc, txEngine, das)
 *
 * // List a cNFT for sale (transfers to escrow via Bubblegum delegate + transfer)
 * val sig = marketplace.transferNft(
 *     wallet      = mwaAdapter,
 *     assetId     = "GdR7...",
 *     merkleTree  = Pubkey.fromBase58("tree..."),
 *     newOwner    = escrowPubkey
 * )
 *
 * // Fetch all NFTs owned by a wallet via DAS
 * val assets = marketplace.getAssetsByOwner(ownerPubkey)
 * ```
 *
 * **Design note:** On-chain auctions house programs (Auction House, Tensor, etc.) require
 * protocol-specific instruction builders that live outside this library. `MarketplaceEngine`
 * provides the transport/signing layer; protocol instruction builders plug into [executeInstructions].
 */
class MarketplaceEngine(
    private val rpc: RpcApi,
    private val txEngine: TxEngine,
    private val das: ArtemisDas? = null
) {

    private val preflight = MarketplacePreflight(rpc, das)

    /**
     * Result returned from a marketplace transaction.
     */
    data class MarketplaceResult(
        val signature: String,
        val confirmed: Boolean
    )

    // ─── Asset discovery ──────────────────────────────────────────────────────

    /**
     * Fetch all digital assets owned by [owner] via the configured DAS provider.
     *
     * @throws IllegalStateException if no [ArtemisDas] was provided at construction
     */
    suspend fun getAssetsByOwner(owner: Pubkey): List<DigitalAsset> {
        return checkNotNull(das) {
            "MarketplaceEngine.getAssetsByOwner() requires an ArtemisDas instance. " +
            "Provide one when constructing MarketplaceEngine."
        }.assetsByOwner(owner)
    }

    /**
     * Fetch all assets in a given collection.
     */
    suspend fun getAssetsByCollection(collectionAddress: String): List<DigitalAsset> {
        return checkNotNull(das) {
            "MarketplaceEngine.getAssetsByCollection() requires an ArtemisDas instance."
        }.assetsByCollection(collectionAddress)
    }

    // ─── cNFT transfer ───────────────────────────────────────────────────────

    /**
     * Transfer a compressed NFT to a new owner using the Bubblegum protocol.
     *
     * Runs a [MarketplacePreflight] by default to verify ownership and frozen state
     * before building the transaction. Set [runPreflight] to false to skip validation
     * (useful when the caller has already confirmed asset state via DAS).
     *
     * @param wallet      The wallet adapter that signs the transaction
     * @param dasClient   DAS client used to fetch the asset proof
     * @param assetId     The asset ID of the cNFT
     * @param merkleTree  Merkle tree address the cNFT is stored in
     * @param treeConfig  Tree config PDA (derived from merkleTree if omitted)
     * @param newOwner    Recipient's wallet address
     * @param runPreflight Whether to validate ownership and frozen state before building the tx
     */
    suspend fun transferCnft(
        wallet: WalletAdapter,
        dasClient: DasClient,
        assetId: String,
        merkleTree: Pubkey,
        newOwner: Pubkey,
        treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
        runPreflight: Boolean = true
    ): MarketplaceResult {
        if (runPreflight) {
            val check = preflight.validateCnftTransfer(wallet.publicKey, assetId)
            if (!check.valid) {
                throw IllegalStateException(
                    "MarketplacePreflight failed for $assetId: ${check.errors.joinToString("; ")}"
                )
            }
        }
        val assetWithProof = MarketplaceToolkit.fetchAssetWithProof(dasClient, assetId)
        val proofArgs = DasProofParser.parseProofArgs(assetWithProof.asset, assetWithProof.proof)
        val proofAccounts = DasProofParser.proofAccountsFromProof(assetWithProof.proof)

        val leafOwner = wallet.publicKey
        val transferIx = BubblegumInstructions.transfer(
            treeConfig = treeConfig,
            merkleTree = merkleTree,
            leafOwner = leafOwner,
            leafDelegate = leafOwner,
            newLeafOwner = newOwner,
            args = BubblegumArgs.TransferArgs(proofArgs),
            proofAccounts = proofAccounts
        )

        val result = txEngine.execute(
            instructions = listOf(transferIx),
            feePayer = leafOwner,
            externalSign = { unsignedTxBytes -> wallet.signMessage(unsignedTxBytes, SignTxRequest("signTransaction")) }
        )
        return MarketplaceResult(result.signatureOrNull ?: "", result.isSuccess)
    }

    // ─── Generic instruction execution ───────────────────────────────────────

    /**
     * Execute a pre-built set of marketplace instructions through the transaction pipeline.
     *
     * Intended for on-chain protocol interaction (Tensor, Magic Eden, custom auction houses)
     * where the caller builds the protocol instructions and this engine handles
     * blockhash, signing, sending, and confirmation.
     *
     * @param wallet       The wallet adapter that signs
     * @param instructions The pre-built protocol instructions
     */
    suspend fun executeInstructions(
        wallet: WalletAdapter,
        instructions: List<com.selenus.artemis.tx.Instruction>
    ): MarketplaceResult {
        val result = txEngine.execute(
            instructions = instructions,
            feePayer = wallet.publicKey,
            externalSign = { txBytes -> wallet.signMessage(txBytes, SignTxRequest("signTransaction")) }
        )
        return MarketplaceResult(result.signatureOrNull ?: "", result.isSuccess)
    }

}
