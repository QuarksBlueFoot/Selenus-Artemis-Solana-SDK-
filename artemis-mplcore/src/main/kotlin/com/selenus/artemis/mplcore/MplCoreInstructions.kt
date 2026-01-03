package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * MPL Core instruction builders.
 *
 * Discriminator: sha256("global:<method>") first 8 bytes.
 * Method names can differ across builds; override methodName if needed.
 */
object MplCoreInstructions {

  fun createCollection(
    collection: Pubkey,
    payer: Pubkey,
    authority: Pubkey,
    args: MplCoreArgs.CreateCollectionArgs,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    systemProgram: Pubkey = Pubkey.fromBase58("11111111111111111111111111111111"),
    methodName: String = "create_collection"
  ): Instruction {
    val data = MplCoreCodec.disc(methodName) + args.serialize()
    return Instruction(
      programId = programId,
      accounts = listOf(
        AccountMeta(collection, isSigner = false, isWritable = true),
        AccountMeta(payer, isSigner = true, isWritable = true),
        AccountMeta(systemProgram, isSigner = false, isWritable = false),
        AccountMeta(authority, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  fun createAsset(
    asset: Pubkey,
    payer: Pubkey,
    owner: Pubkey,
    authority: Pubkey,
    args: MplCoreArgs.CreateAssetArgs,
    collection: Pubkey? = args.collection,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    systemProgram: Pubkey = Pubkey.fromBase58("11111111111111111111111111111111"),
    methodName: String = "create_asset"
  ): Instruction {
    val data = MplCoreCodec.disc(methodName) + args.serialize()
    val metas = ArrayList<AccountMeta>()
    metas.add(AccountMeta(asset, isSigner = false, isWritable = true))
    metas.add(AccountMeta(payer, isSigner = true, isWritable = true))
    metas.add(AccountMeta(systemProgram, isSigner = false, isWritable = false))
    metas.add(AccountMeta(owner, isSigner = false, isWritable = false))
    metas.add(AccountMeta(authority, isSigner = true, isWritable = false))
    if (collection != null) metas.add(AccountMeta(collection, isSigner = false, isWritable = false))
    return Instruction(programId, metas, data)
  }

  fun updateAuthority(
    target: Pubkey,
    authority: Pubkey,
    args: MplCoreArgs.UpdateAuthorityArgs,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    methodName: String = "update_authority"
  ): Instruction {
    val data = MplCoreCodec.disc(methodName) + args.serialize()
    return Instruction(
      programId = programId,
      accounts = listOf(
        AccountMeta(target, isSigner = false, isWritable = true),
        AccountMeta(authority, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }

  fun updateMetadata(
    target: Pubkey,
    authority: Pubkey,
    name: String?,
    uri: String?,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    methodName: String = "update_metadata"
  ): Instruction {
    val hasName = name != null
    val hasUri = uri != null
    val payload = MplCoreCodec.concat(
      listOf(
        MplCoreCodec.u8(if (hasName) 1 else 0),
        if (hasName) MplCoreCodec.borshString(name!!) else byteArrayOf(),
        MplCoreCodec.u8(if (hasUri) 1 else 0),
        if (hasUri) MplCoreCodec.borshString(uri!!) else byteArrayOf()
      )
    )
    val data = MplCoreCodec.disc(methodName) + payload
    return Instruction(
      programId = programId,
      accounts = listOf(
        AccountMeta(target, isSigner = false, isWritable = true),
        AccountMeta(authority, isSigner = true, isWritable = false),
      ),
      data = data
    )
  }


/**
 * Add an existing asset to a collection.
 */
fun addToCollection(
  asset: Pubkey,
  collection: Pubkey,
  authority: Pubkey,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "add_to_collection",
  data: ByteArray = byteArrayOf()
): Instruction {
  val ixData = MplCoreCodec.disc(methodName) + data
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(collection, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = ixData
  )
}

/**
 * Remove an asset from a collection.
 */
fun removeFromCollection(
  asset: Pubkey,
  collection: Pubkey,
  authority: Pubkey,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "remove_from_collection",
  data: ByteArray = byteArrayOf()
): Instruction {
  val ixData = MplCoreCodec.disc(methodName) + data
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(collection, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = ixData
  )
}

/**
 * Update royalties plugin on an asset (pragmatic encoding).
 */
fun setRoyalties(
  asset: Pubkey,
  authority: Pubkey,
  royalties: MplCorePlugins.Royalties,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "set_royalties"
): Instruction {
  val data = MplCoreCodec.disc(methodName) + royalties.serialize()
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = data
  )
}

/**
 * Update attributes plugin on an asset (pragmatic encoding).
 */
fun setAttributes(
  asset: Pubkey,
  authority: Pubkey,
  attributes: MplCorePlugins.Attributes,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "set_attributes"
): Instruction {
  val data = MplCoreCodec.disc(methodName) + attributes.serialize()
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = data
  )
}

/**
 * Raw instruction helper when you already have encoded args for a specific build.
 */
fun raw(
  accounts: List<AccountMeta>,
  data: ByteArray,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID
): Instruction = Instruction(programId, accounts, data)



/**
 * Marketplace safety rail: lock an asset (prevent transfer) if supported by your Core build.
 *
 * Many implementations expose this as a plugin toggle or a "freeze/lock" instruction.
 * Provide methodName that matches your target program build.
 */
fun lock(
  asset: Pubkey,
  authority: Pubkey,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "lock",
  data: ByteArray = byteArrayOf()
): Instruction {
  val ixData = MplCoreCodec.disc(methodName) + data
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = ixData
  )
}

/**
 * Unlock an asset that was previously locked.
 */
fun unlock(
  asset: Pubkey,
  authority: Pubkey,
  programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
  methodName: String = "unlock",
  data: ByteArray = byteArrayOf()
): Instruction {
  val ixData = MplCoreCodec.disc(methodName) + data
  return Instruction(
    programId = programId,
    accounts = listOf(
      AccountMeta(asset, isSigner = false, isWritable = true),
      AccountMeta(authority, isSigner = true, isWritable = false),
    ),
    data = ixData
  )
}

}
