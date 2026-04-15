package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.programs.ProgramIds
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * MarketplacePreflight — validates asset and ownership state before executing marketplace transactions.
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
     * @param prependIxs    Instructions the caller should prepend to fix recoverable state
     *                      (e.g. a missing destination ATA). Empty when nothing is needed.
     */
    data class PreflightResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val prependIxs: List<Instruction> = emptyList()
    )

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

        return PreflightResult(valid = errors.isEmpty(), errors = errors)
    }

    /**
     * Validate that [wallet] holds a standard (non-compressed) SPL NFT with [mint].
     *
     * Checks that the wallet has a token account for [mint] with a non-zero balance.
     * If [recipient] is supplied, also ensures the destination ATA exists — and when
     * it doesn't, surfaces a `createAssociatedTokenAccount` instruction via
     * [PreflightResult.prependIxs] so the caller can prepend it to the outgoing tx.
     *
     * @param wallet    Wallet that will sign the transfer
     * @param mint      Mint address of the NFT
     * @param recipient Destination wallet (optional — enables ATA auto-create)
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
                }
            }

            PreflightResult(valid = errors.isEmpty(), errors = errors, prependIxs = prepend)
        } catch (e: Exception) {
            PreflightResult(
                valid = false,
                errors = listOf("Failed to verify token holdings for ${wallet.toBase58()}: ${e.message}")
            )
        }
    }

    /**
     * Pure ATA ensure helper — returns the destination ATA address for [recipient]
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
