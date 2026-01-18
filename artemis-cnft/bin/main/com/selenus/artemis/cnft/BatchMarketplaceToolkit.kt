package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.DasClient
import com.selenus.artemis.cnft.das.DasProofParser
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.Instruction
import com.selenus.artemis.vtx.AltToolkit
import kotlinx.serialization.json.JsonObject

/**
 * Batch helpers for marketplace flows.
 *
 * Goal:
 * - Build many cNFT listing or purchase operations in one v0 transaction
 * - Use ALT fetching and optimization to keep message size under control
 *
 * Typical patterns:
 * - Listing: delegate (to marketplace escrow delegate) then optionally lock via program logic
 * - Purchase: transfer (delegate signs) to buyer
 */
object BatchMarketplaceToolkit {

  data class AssetWithProof(val asset: JsonObject, val proof: JsonObject)

  /**
   * Fetch asset and proof for each asset id from DAS.
   */
  suspend fun fetchBatch(das: DasClient, assetIds: List<String>): List<AssetWithProof> {
    return assetIds.map { id -> AssetWithProof(das.getAsset(id), das.getAssetProof(id)) }
  }

  /**
   * Build delegate instructions for many assets.
   *
   * Each delegate ix has its own proof nodes, which are appended as remaining accounts.
   */
  fun buildDelegateMany(
    merkleTree: Pubkey,
    treeConfig: Pubkey,
    leafOwner: Pubkey,
    currentDelegate: Pubkey?,
    newDelegate: Pubkey,
    batch: List<AssetWithProof>
  ): List<Instruction> {
    return batch.map { (asset, proof) ->
      val proofArgs = DasProofParser.parseProofArgs(asset, proof)
      val proofAccounts = DasProofParser.proofAccountsFromProof(proof)
      BubblegumInstructions.delegate(
        treeConfig = treeConfig,
        merkleTree = merkleTree,
        leafOwner = leafOwner,
        currentDelegate = currentDelegate,
        args = BubblegumArgs.DelegateArgs(proofArgs, newDelegate),
        proofAccounts = proofAccounts
      )
    }
  }

  /**
   * Build transfer instructions for many assets.
   *
   * Commonly used for purchases when the delegate is authorized to transfer to buyer.
   */
  fun buildTransferMany(
    merkleTree: Pubkey,
    treeConfig: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?,
    newLeafOwner: Pubkey,
    batch: List<AssetWithProof>
  ): List<Instruction> {
    return batch.map { (asset, proof) ->
      val proofArgs = DasProofParser.parseProofArgs(asset, proof)
      val proofAccounts = DasProofParser.proofAccountsFromProof(proof)
      BubblegumInstructions.transfer(
        treeConfig = treeConfig,
        merkleTree = merkleTree,
        leafOwner = leafOwner,
        leafDelegate = leafDelegate,
        newLeafOwner = newLeafOwner,
        args = BubblegumArgs.TransferArgs(proofArgs),
        proofAccounts = proofAccounts
      )
    }
  }

  /**
   * Compile, sign, and send a batch as a v0 transaction with ALT support.
   *
   * Returns the signature string.
   */
  suspend fun signAndSendV0(
    rpc: RpcApi,
    feePayer: Signer,
    instructions: List<Instruction>,
    lookupTableAddresses: List<Pubkey>,
    additionalSigners: List<Signer> = emptyList(),
    optimizeMode: com.selenus.artemis.vtx.AltOptimizer.Mode = com.selenus.artemis.vtx.AltOptimizer.Mode.SIZE,
    skipPreflight: Boolean = false
  ): String {
    val bh = rpc.getLatestBlockhash().blockhash
    val tx = AltToolkit.compileAndSignV0WithAutoAlts(
      rpc = rpc,
      feePayer = feePayer,
      additionalSigners = additionalSigners,
      recentBlockhash = bh,
      instructions = instructions,
      lookupTableAddresses = lookupTableAddresses,
      optimizeMode = optimizeMode,
      optimize = true
    )
    val base64 = tx.toBase64()
    return rpc.sendTransaction(base64Tx = base64, skipPreflight = skipPreflight)
  }
}
