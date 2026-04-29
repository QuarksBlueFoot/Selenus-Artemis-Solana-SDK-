package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * MarketplacePreflight - validates asset and ownership state before executing marketplace transactions.
 *
 * Run before [MarketplaceEngine.transferCnft] or [MarketplaceEngine.executeInstructions] to catch
 * common failure conditions (wrong owner, frozen asset, missing DAS record) before submitting
 * a transaction that will fail on-chain.
 *
 * ```kotlin
 * val preflight = MarketplacePreflight(rpc, das)
 *
 * val result = preflight.validateCnftTransfer(wallet.publicKey, assetId)
 * if (!result.valid) {
 *     error("Transfer would fail: ${result.errors.joinToString()}")
 * }
 * ```
 */
class MarketplacePreflight(
    private val rpc: RpcApi,
    private val das: ArtemisDas? = null,
    private val ataEnsurer: AtaEnsurer = AtaEnsurer(rpc)
) {

    /**
     * Result of a preflight check.
     *
     * @param valid         True if all checks passed and the transaction should succeed.
     * @param errors        Human-readable descriptions of each check that failed.
     * @param warnings      Non-fatal advisories the UI should surface (for example a
     *                      royalty fee that will be enforced on the chain side).
     * @param prependIxs    Instructions the caller should prepend to fix recoverable
     *                      state (a missing destination ATA, etc.).
     * @param royalty       Structured royalty information for the asset if it could be
     *                      resolved. Apps render this alongside the transaction approval
     *                      dialog so users see the effective take-rate before signing.
     */
    data class PreflightResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val prependIxs: List<Instruction> = emptyList(),
        val royalty: RoyaltyInfo? = null
    )

    /**
     * Structured royalty metadata used by preflight results.
     *
     * Surfacing royalties in the preflight surface lets marketplace apps show
     * the effective creator fee before the user signs. Royalty enforcement
     * itself is handled by the on-chain program (Metaplex Token Metadata
     * enforces on pNFTs; Bubblegum honors it for compressed NFTs); the
     * preflight layer only reports the declared value so the UI can warn
     * users when a trade will pay a non-zero fee.
     *
     * @param basisPoints Royalty in hundredths of a percent. 500 means 5%.
     * @param verifiedCollection Whether the asset belongs to a verified Metaplex
     *                           collection. Unverified collections do not legally
     *                           enforce creator royalties on-chain.
     */
    data class RoyaltyInfo(
        val basisPoints: Int,
        val verifiedCollection: Boolean
    ) {
        /** Royalty as a percent, e.g. 5.0 for a 500 basis-point fee. */
        val percent: Double get() = basisPoints / 100.0
    }

    /**
     * Validate a compressed NFT (cNFT) transfer before executing it.
     *
     * Checks:
     * - Asset exists in DAS
     * - Asset is a compressed NFT
     * - [wallet] is the current owner
     * - Asset is not frozen
     *
     * @param wallet  Wallet that will sign the transfer (must be the current owner)
     * @param assetId DAS asset ID of the cNFT
     */
    suspend fun validateCnftTransfer(
        wallet: Pubkey,
        assetId: String
    ): PreflightResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val d = das
        if (d == null) {
            return PreflightResult(
                valid = false,
                errors = listOf("DAS client required for cNFT preflight. Provide dasUrl when constructing ArtemisMobile.")
            )
        }

        val asset = try {
            d.asset(assetId)
        } catch (e: Exception) {
            return PreflightResult(
                valid = false,
                errors = listOf("Failed to fetch asset $assetId from DAS: ${e.message}")
            )
        }

        if (asset == null) {
            return PreflightResult(valid = false, errors = listOf("Asset $assetId not found in DAS"))
        }

        if (!asset.isCompressed) {
            errors += "Asset $assetId is not a compressed NFT. Use a standard token transfer instead."
        }

        val walletAddress = wallet.toBase58()
        if (asset.owner != walletAddress) {
            errors += "Wallet $walletAddress is not the owner of $assetId (current owner: ${asset.owner})"
        }

        if (asset.frozen) {
            errors += "Asset $assetId is frozen and cannot be transferred"
        }

        val royalty = RoyaltyInfo(
            basisPoints = asset.royaltyBasisPoints,
            verifiedCollection = asset.collectionVerified
        )
        if (royalty.basisPoints > 0) {
            warnings += "Asset carries a ${royalty.percent}% creator royalty" +
                if (!royalty.verifiedCollection) " (collection is not verified, royalty may not be enforced)" else ""
        }

        return PreflightResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            royalty = royalty
        )
    }

    /**
     * Validate that [wallet] holds a standard (non-compressed) SPL NFT with [mint].
     *
     * Checks that the wallet has a token account for [mint] with a non-zero balance.
     * If [recipient] is supplied, also ensures the destination ATA exists - and when
     * it doesn't, surfaces a `createAssociatedTokenAccount` instruction via
     * [PreflightResult.prependIxs] so the caller can prepend it to the outgoing tx.
     *
     * @param wallet    Wallet that will sign the transfer
     * @param mint      Mint address of the NFT
     * @param recipient Destination wallet (optional - enables ATA auto-create)
     * @param payer     Account that pays the ATA rent if creation is needed. Defaults to [wallet].
     * @param tokenProgram Token program for the mint ([ProgramIds.TOKEN_2022_PROGRAM] for Token-2022).
     */
    suspend fun validateNftTransfer(
        wallet: Pubkey,
        mint: Pubkey,
        recipient: Pubkey? = null,
        payer: Pubkey = wallet,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): PreflightResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val prepend = mutableListOf<Instruction>()
        return try {
            val result = rpc.getTokenAccountsByOwner(
                owner = wallet.toBase58(),
                mint = mint.toBase58()
            )
            val value = result["value"]
            if (value == null) {
                errors += "Wallet ${wallet.toBase58()} has no token account for mint ${mint.toBase58()}"
            }

            if (recipient != null) {
                val ataResolution = runCatching {
                    ataEnsurer.resolve(payer, recipient, mint, tokenProgram)
                }.getOrNull()

                if (ataResolution == null) {
                    errors += "Unable to verify destination ATA for recipient ${recipient.toBase58()}"
                } else if (ataResolution.createIx != null) {
                    prepend += ataResolution.createIx
                    warnings += "Destination ATA does not exist and will be created (rent will be paid by ${payer.toBase58()})"
                }
            }

            // Best-effort royalty surface: when the caller provides a DAS client it
            // is reused, since mints indexed by DAS carry authoritative royalty data.
            // Non-DAS mints skip the royalty block rather than guess, so the UI never
            // renders fabricated numbers.
            val royalty: RoyaltyInfo? = das?.let { dasClient ->
                runCatching { dasClient.asset(mint.toBase58()) }.getOrNull()?.let { asset ->
                    val info = RoyaltyInfo(
                        basisPoints = asset.royaltyBasisPoints,
                        verifiedCollection = asset.collectionVerified
                    )
                    if (info.basisPoints > 0) {
                        warnings += "Asset carries a ${info.percent}% creator royalty" +
                            if (!info.verifiedCollection) " (collection unverified)" else ""
                    }
                    info
                }
            }

            PreflightResult(
                valid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                prependIxs = prepend,
                royalty = royalty
            )
        } catch (e: Exception) {
            PreflightResult(
                valid = false,
                errors = listOf("Failed to verify token holdings for ${wallet.toBase58()}: ${e.message}")
            )
        }
    }

    /**
     * Pure ATA ensure helper - returns the destination ATA address for [recipient]
     * and a create instruction if the account does not exist yet.
     *
     * Thin proxy over [AtaEnsurer] so callers who already know the mint is valid
     * don't need to import both types.
     */
    suspend fun ensureDestinationAta(
        payer: Pubkey,
        recipient: Pubkey,
        mint: Pubkey,
        tokenProgram: Pubkey = ProgramIds.TOKEN_PROGRAM
    ): AtaEnsurer.AtaResolution = ataEnsurer.resolve(payer, recipient, mint, tokenProgram)
}
