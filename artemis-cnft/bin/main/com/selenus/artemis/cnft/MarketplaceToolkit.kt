package com.selenus.artemis.cnft

import com.selenus.artemis.cnft.das.DasClient
import com.selenus.artemis.cnft.das.DasProofParser
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.serialization.json.JsonObject

/**
 * High-level helpers used by marketplaces:
 * - Fetch proof via DAS
 * - Build delegate / transfer / burn ix bundles
 */
object MarketplaceToolkit {

  data class AssetWithProof(
    val asset: JsonObject,
    val proof: JsonObject
  )

  suspend fun fetchAssetWithProof(das: DasClient, assetId: String): AssetWithProof {
    val asset = das.getAsset(assetId)
    val proof = das.getAssetProof(assetId)
    return AssetWithProof(asset, proof)
  }

  fun buildDelegateThenTransfer(
    merkleTree: Pubkey,
    treeConfig: Pubkey,
    asset: JsonObject,
    proof: JsonObject,
    leafOwner: Pubkey,
    currentDelegate: Pubkey?,
    newDelegate: Pubkey,
    newLeafOwner: Pubkey
  ): List<Instruction> {
    val proofArgs = DasProofParser.parseProofArgs(asset, proof)
    val proofAccounts = DasProofParser.proofAccountsFromProof(proof)

    val delegateIx = BubblegumInstructions.delegate(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      currentDelegate = currentDelegate,
      args = BubblegumArgs.DelegateArgs(proofArgs, newDelegate),
      proofAccounts = proofAccounts
    )

    val transferIx = BubblegumInstructions.transfer(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      leafDelegate = newDelegate,
      newLeafOwner = newLeafOwner,
      args = BubblegumArgs.TransferArgs(proofArgs),
      proofAccounts = proofAccounts
    )

    return listOf(delegateIx, transferIx)
  }

  fun buildBurn(
    merkleTree: Pubkey,
    treeConfig: Pubkey,
    asset: JsonObject,
    proof: JsonObject,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?
  ): Instruction {
    val proofArgs = DasProofParser.parseProofArgs(asset, proof)
    val proofAccounts = DasProofParser.proofAccountsFromProof(proof)

    return BubblegumInstructions.burn(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      leafDelegate = leafDelegate,
      args = BubblegumArgs.BurnArgs(proofArgs),
      proofAccounts = proofAccounts
    )
  }
}
