package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey

/**
 * Bubblegum / cNFT argument serializers.
 */
object BubblegumArgs {

  data class LeafSchema(
    val owner: Pubkey,
    val delegate: Pubkey,
    val nonce: Long,
    val dataHash: ByteArray,
    val creatorHash: ByteArray
  ) {
    fun serialize(): ByteArray {
      require(dataHash.size == 32) { "dataHash must be 32 bytes" }
      require(creatorHash.size == 32) { "creatorHash must be 32 bytes" }
      return CnftCodec.concat(
        listOf(
          owner.toByteArray(),
          delegate.toByteArray(),
          CnftCodec.u64le(nonce),
          dataHash,
          creatorHash
        )
      )
    }
  }

  fun leafHash(leaf: LeafSchema): ByteArray {
    val prefix = "leaf".encodeToByteArray()
    return Hashing.sha256(prefix + leaf.serialize())
  }

  data class TransferArgs(
    val proof: ProofArgs
  ) {
    fun serialize(): ByteArray = proof.serialize()
  }

  fun buildTransferIxData(
    args: TransferArgs,
    methodName: String = "transfer"
  ): ByteArray {
    val disc = AnchorDiscriminator.global(methodName)
    return disc + args.serialize()
  }

  data class BurnArgs(
    val proof: ProofArgs
  ) {
    fun serialize(): ByteArray = proof.serialize()
  }

  fun buildBurnIxData(
    args: BurnArgs,
    methodName: String = "burn"
  ): ByteArray {
    val disc = AnchorDiscriminator.global(methodName)
    return disc + args.serialize()
  }

  data class DelegateArgs(
    val proof: ProofArgs,
    val newDelegate: Pubkey
  ) {
    fun serialize(): ByteArray {
      return CnftCodec.concat(listOf(proof.serialize(), newDelegate.toByteArray()))
    }
  }

  fun buildDelegateIxData(
    args: DelegateArgs,
    methodName: String = "delegate"
  ): ByteArray {
    val disc = AnchorDiscriminator.global(methodName)
    return disc + args.serialize()
  }

  data class VerifyCreatorArgs(
    val proof: ProofArgs,
    val creator: Pubkey
  ) {
    fun serialize(): ByteArray = CnftCodec.concat(listOf(proof.serialize(), creator.toByteArray()))
  }

  fun buildVerifyCreatorIxData(
    args: VerifyCreatorArgs,
    methodName: String = "verify_creator"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()

  fun buildUnverifyCreatorIxData(
    args: VerifyCreatorArgs,
    methodName: String = "unverify_creator"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()

  data class VerifyCollectionArgs(
    val proof: ProofArgs,
    val collectionMint: Pubkey
  ) {
    fun serialize(): ByteArray = CnftCodec.concat(listOf(proof.serialize(), collectionMint.toByteArray()))
  }

  fun buildVerifyCollectionIxData(
    args: VerifyCollectionArgs,
    methodName: String = "verify_collection"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()

  fun buildUnverifyCollectionIxData(
    args: VerifyCollectionArgs,
    methodName: String = "unverify_collection"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()

  data class DecompressArgs(
    val proof: ProofArgs
  ) {
    fun serialize(): ByteArray = proof.serialize()
  }

  fun buildDecompressIxData(
    args: DecompressArgs,
    methodName: String = "decompress_v1"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()


  /**
   * Redeem a leaf into a Voucher (marketplace/uncompressed bridge flow).
   * Proof nodes are passed as remaining accounts.
   */
  data class RedeemArgs(
    val proof: ProofArgs
  ) {
    fun serialize(): ByteArray = proof.serialize()
  }

  fun buildRedeemIxData(
    args: RedeemArgs,
    methodName: String = "redeem"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()

  /**
   * Cancel a redeem (root only).
   */
  data class CancelRedeemArgs(
    val root: ByteArray
  ) {
    fun serialize(): ByteArray {
      require(root.size == 32) { "root must be 32 bytes" }
      return root
    }
  }

  fun buildCancelRedeemIxData(
    args: CancelRedeemArgs,
    methodName: String = "cancel_redeem"
  ): ByteArray = AnchorDiscriminator.global(methodName) + args.serialize()
}
