package com.selenus.artemis.cnft

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * Bubblegum instruction builders.
 *
 * Proof-based instructions take proof nodes as remaining accounts (readonly).
 *
 * Discriminators are computed using AnchorDiscriminator.global(methodName).
 * If your target Bubblegum build uses different method names, override the methodName parameter.
 */
object BubblegumInstructions {

  fun createTreeConfig(
    merkleTree: Pubkey,
    payer: Pubkey,
    treeCreator: Pubkey,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    args: BubblegumV1Builders.CreateTreeConfigArgs,
    methodName: String = "create_tree_config"
  ): Instruction {
    val data = AnchorDiscriminator.global(methodName) + args.serialize()
    return Instruction(
      programId = BubblegumPrograms.BUBBLEGUM_PROGRAM_ID,
      accounts = listOf(
        AccountMeta(merkleTree, isSigner = false, isWritable = true),
        AccountMeta(treeConfig, isSigner = false, isWritable = true),
        AccountMeta(payer, isSigner = true, isWritable = true),
        AccountMeta(treeCreator, isSigner = true, isWritable = false),
        AccountMeta(logWrapper, isSigner = false, isWritable = false),
        AccountMeta(compressionProgram, isSigner = false, isWritable = false),
        AccountMeta(systemProgram, isSigner = false, isWritable = false),
      ),
      data = data
    )
  }

  fun mintV1(
    merkleTree: Pubkey,
    payer: Pubkey,
    treeCreatorOrDelegate: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey = leafOwner,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    metadata: MetadataArgs,
    creatorSigners: List<Pubkey> = emptyList(),
    methodName: String = "mint_v1"
  ): Instruction {
    val data = AnchorDiscriminator.global(methodName) + BubblegumV1Builders.MintV1Args(metadata).serialize()
    val metas = ArrayList<AccountMeta>(9 + creatorSigners.size)
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(payer, isSigner = true, isWritable = true))
    metas.add(AccountMeta(treeCreatorOrDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(leafOwner, isSigner = false, isWritable = false))
    metas.add(AccountMeta(leafDelegate, isSigner = false, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (s in creatorSigners) metas.add(AccountMeta(s, isSigner = true, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun mintToCollectionV1(
    merkleTree: Pubkey,
    payer: Pubkey,
    treeCreatorOrDelegate: Pubkey,
    leafOwner: Pubkey,
    collectionMint: Pubkey,
    collectionAuthority: Pubkey,
    leafDelegate: Pubkey = leafOwner,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    tokenMetadataProgram: Pubkey = BubblegumPrograms.TOKEN_METADATA_PROGRAM_ID,
    collectionMetadata: Pubkey,
    collectionEdition: Pubkey,
    collectionAuthorityRecordPda: Pubkey = BubblegumPdas.collectionAuthorityRecordPda(tokenMetadataProgram, collectionMint, collectionAuthority),
    bubblegumSigner: Pubkey = BubblegumSigner.pda(),
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    metadata: MetadataArgs,
    creatorSigners: List<Pubkey> = emptyList(),
    methodName: String = "mint_to_collection_v1"
  ): Instruction {
    val data = AnchorDiscriminator.global(methodName) + BubblegumV1Builders.MintToCollectionV1Args(metadata).serialize()

    val metas = ArrayList<AccountMeta>(16 + creatorSigners.size)
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(payer, isSigner = true, isWritable = true))
    metas.add(AccountMeta(treeCreatorOrDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(leafOwner, isSigner = false, isWritable = false))
    metas.add(AccountMeta(leafDelegate, isSigner = false, isWritable = false))

    metas.add(AccountMeta(collectionMint, isSigner = false, isWritable = false))
    metas.add(AccountMeta(collectionMetadata, isSigner = false, isWritable = false))
    metas.add(AccountMeta(collectionEdition, isSigner = false, isWritable = false))
    metas.add(AccountMeta(collectionAuthority, isSigner = true, isWritable = false))
    metas.add(AccountMeta(collectionAuthorityRecordPda, isSigner = false, isWritable = false))

    metas.add(AccountMeta(tokenMetadataProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(bubblegumSigner, isSigner = false, isWritable = false))

    for (s in creatorSigners) metas.add(AccountMeta(s, isSigner = true, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun transfer(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?,
    newLeafOwner: Pubkey,
    args: BubblegumArgs.TransferArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "transfer"
  ): Instruction {
    val data = BubblegumArgs.buildTransferIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    if (leafDelegate != null) metas.add(AccountMeta(leafDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(newLeafOwner, isSigner = false, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun burn(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?,
    args: BubblegumArgs.BurnArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "burn"
  ): Instruction {
    val data = BubblegumArgs.buildBurnIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    if (leafDelegate != null) metas.add(AccountMeta(leafDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun delegate(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    currentDelegate: Pubkey?,
    args: BubblegumArgs.DelegateArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "delegate"
  ): Instruction {
    val data = BubblegumArgs.buildDelegateIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    if (currentDelegate != null) metas.add(AccountMeta(currentDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun verifyCreator(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    authority: Pubkey,
    args: BubblegumArgs.VerifyCreatorArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "verify_creator"
  ): Instruction {
    val data = BubblegumArgs.buildVerifyCreatorIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(authority, isSigner = true, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun unverifyCreator(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    authority: Pubkey,
    args: BubblegumArgs.VerifyCreatorArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "unverify_creator"
  ): Instruction = verifyCreator(treeConfig, merkleTree, authority, args, proofAccounts, logWrapper, compressionProgram, systemProgram, methodName)

  fun verifyCollection(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    collectionAuthority: Pubkey,
    args: BubblegumArgs.VerifyCollectionArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "verify_collection"
  ): Instruction {
    val data = BubblegumArgs.buildVerifyCollectionIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(collectionAuthority, isSigner = true, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  fun unverifyCollection(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    collectionAuthority: Pubkey,
    args: BubblegumArgs.VerifyCollectionArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "unverify_collection"
  ): Instruction = verifyCollection(treeConfig, merkleTree, collectionAuthority, args, proofAccounts, logWrapper, compressionProgram, systemProgram, methodName)

  /**
   * Decompress a compressed NFT into a standard token/NFT.
   *
   * Decompression requires a voucher + metadata accounts; those can vary based on flow.
   * This builder captures the proof portion and leaves additional accounts to the caller.
   */
  fun decompressV1(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    args: BubblegumArgs.DecompressArgs,
    proofAccounts: List<Pubkey>,
    extraAccounts: List<AccountMeta>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "decompress_v1"
  ): Instruction {
    val data = BubblegumArgs.buildDecompressIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    metas.addAll(extraAccounts)
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  /**
   * Set the Tree Delegate (commonly used to allow a marketplace to mint/manage mints).
   */
  fun setTreeDelegate(
    merkleTree: Pubkey,
    treeCreator: Pubkey,
    newTreeDelegate: Pubkey,
    treeConfig: Pubkey = BubblegumPdas.treeConfig(merkleTree),
    methodName: String = "set_tree_delegate"
  ): Instruction {
    val data = AnchorDiscriminator.global(methodName)
    val metas = listOf(
      AccountMeta(treeConfig, isSigner = false, isWritable = true),
      AccountMeta(merkleTree, isSigner = false, isWritable = false),
      AccountMeta(treeCreator, isSigner = true, isWritable = false),
      AccountMeta(newTreeDelegate, isSigner = false, isWritable = false),
    )
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  /**
   * Redeem a compressed NFT into a Voucher account.
   *
   * Proof nodes are passed as remaining accounts.
   */
  fun redeem(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?,
    voucher: Pubkey,
    args: BubblegumArgs.RedeemArgs,
    proofAccounts: List<Pubkey>,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "redeem"
  ): Instruction {
    val data = BubblegumArgs.buildRedeemIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    if (leafDelegate != null) metas.add(AccountMeta(leafDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(voucher, isSigner = false, isWritable = true))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    for (p in proofAccounts) metas.add(AccountMeta(p, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

  /**
   * Cancel a redeem.
   */
  fun cancelRedeem(
    treeConfig: Pubkey,
    merkleTree: Pubkey,
    leafOwner: Pubkey,
    leafDelegate: Pubkey?,
    voucher: Pubkey,
    args: BubblegumArgs.CancelRedeemArgs,
    logWrapper: Pubkey = BubblegumPrograms.LOG_WRAPPER_ID,
    compressionProgram: Pubkey = BubblegumPrograms.ACCOUNT_COMPRESSION_PROGRAM_ID,
    systemProgram: Pubkey = BubblegumPrograms.SYSTEM_PROGRAM_ID,
    methodName: String = "cancel_redeem"
  ): Instruction {
    val data = BubblegumArgs.buildCancelRedeemIxData(args, methodName)
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(treeConfig, isSigner = false, isWritable = true))
    metas.add(AccountMeta(merkleTree, isSigner = false, isWritable = true))
    metas.add(AccountMeta(leafOwner, isSigner = true, isWritable = false))
    if (leafDelegate != null) metas.add(AccountMeta(leafDelegate, isSigner = true, isWritable = false))
    metas.add(AccountMeta(voucher, isSigner = false, isWritable = true))
    metas.add(AccountMeta(logWrapper, isSigner = false, isWritable = false))
    metas.add(AccountMeta(compressionProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    return Instruction(BubblegumPrograms.BUBBLEGUM_PROGRAM_ID, metas, data)
  }

}
