package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction

/**
 * Marketplace-oriented helpers for cNFTs.
 *
 * These are opinionated "flow" helpers that compose the low-level Bubblegum instruction builders.
 */
object CnftMarketplaceFlows {

  /**
   * Create the Bubblegum [set_tree_delegate] instruction.
   */
  fun setTreeDelegate(merkleTree: Pubkey, treeCreator: Pubkey, newDelegate: Pubkey): Instruction {
    return BubblegumInstructions.setTreeDelegate(
      merkleTree = merkleTree,
      treeCreator = treeCreator,
      newTreeDelegate = newDelegate,
      treeConfig = BubblegumPdas.treeConfig(merkleTree)
    )
  }

  /**
   * Redeem a cNFT leaf into a voucher account.
   *
   * @param proof The leaf proof. Its [ProofArgs.proof] list is appended as remaining accounts.
   * @param leafDelegate Optional leaf delegate signer (if the delegate, not the owner, is executing).
   */
  fun redeemToVoucher(
    merkleTree: Pubkey,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    leafOwner: Pubkey,
    leafDelegate: Pubkey? = null,
    proof: ProofArgs
  ): Pair<Pubkey, Instruction> {
    val voucher = BubblegumPdas.voucher(merkleTree, proof.nonce)
    val ix = BubblegumInstructions.redeem(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      leafDelegate = leafDelegate,
      voucher = voucher,
      args = BubblegumArgs.RedeemArgs(proof),
      proofAccounts = proof.proof
    )
    return voucher to ix
  }

  /**
   * Cancel a redeem and keep the voucher flow reversible.
   */
  fun cancelRedeem(
    merkleTree: Pubkey,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    leafOwner: Pubkey,
    leafDelegate: Pubkey? = null,
    voucher: Pubkey,
    root: ByteArray
  ): Instruction {
    return BubblegumInstructions.cancelRedeem(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      leafDelegate = leafDelegate,
      voucher = voucher,
      args = BubblegumArgs.CancelRedeemArgs(root)
    )
  }

  /**
   * Convenience: redeem + decompress (standard NFT) instruction list.
   */
  fun redeemAndDecompress(
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    proof: ProofArgs,
    decompressAccounts: DecompressAccounts,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    leafDelegate: Pubkey? = null
  ): List<Instruction> {
    val (voucher, redeemIx) = redeemToVoucher(
      merkleTree = merkleTree,
      treeConfig = treeConfig,
      leafOwner = leafOwner,
      leafDelegate = leafDelegate,
      proof = proof
    )
    // Caller is responsible for ensuring decompressAccounts.voucher matches the derived voucher.
    require(decompressAccounts.voucher == voucher) {
      "decompressAccounts.voucher must equal derived voucher PDA"
    }
    val decompressIx = BubblegumInstructions.decompressV1(
      treeConfig = treeConfig,
      merkleTree = merkleTree,
      leafOwner = leafOwner,
      args = BubblegumArgs.DecompressArgs(proof),
      proofAccounts = proof.proof,
      extraAccounts = decompressAccounts.toMetas()
    )
    return listOf(redeemIx, decompressIx)
  }
}
