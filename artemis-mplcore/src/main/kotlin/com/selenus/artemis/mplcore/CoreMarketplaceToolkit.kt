package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction

/**
 * Core marketplace patterns.
 *
 * Core builds differ. These helpers provide the common shapes:
 * - delegate: grant transfer rights to a marketplace delegate
 * - revokeDelegate: remove delegate
 * - lock and unlock: prevent transfers during listing
 *
 * You can override method names and raw data payloads per program build.
 */
object CoreMarketplaceToolkit {

  fun delegate(
    asset: Pubkey,
    ownerAuthority: Pubkey,
    delegate: Pubkey,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    methodName: String = "delegate",
    data: ByteArray = delegate.bytes
  ): Instruction {
    val ixData = MplCoreCodec.disc(methodName) + data
    return Instruction(
      programId = programId,
      accounts = listOf(
        AccountMeta(asset, isSigner = false, isWritable = true),
        AccountMeta(ownerAuthority, isSigner = true, isWritable = false),
        AccountMeta(delegate, isSigner = false, isWritable = false),
      ),
      data = ixData
    )
  }

  fun revokeDelegate(
    asset: Pubkey,
    ownerAuthority: Pubkey,
    delegate: Pubkey,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    methodName: String = "revoke_delegate",
    data: ByteArray = byteArrayOf()
  ): Instruction {
    val ixData = MplCoreCodec.disc(methodName) + data
    return Instruction(
      programId = programId,
      accounts = listOf(
        AccountMeta(asset, isSigner = false, isWritable = true),
        AccountMeta(ownerAuthority, isSigner = true, isWritable = false),
        AccountMeta(delegate, isSigner = false, isWritable = false),
      ),
      data = ixData
    )
  }

  fun buildListingBundle(
    asset: Pubkey,
    ownerAuthority: Pubkey,
    marketplaceDelegate: Pubkey,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    delegateMethodName: String = "delegate",
    lockMethodName: String = "lock"
  ): List<Instruction> {
    val delegateIx = delegate(asset, ownerAuthority, marketplaceDelegate, programId, delegateMethodName)
    val lockIx = MplCoreInstructions.lock(asset, ownerAuthority, programId, lockMethodName)
    return listOf(delegateIx, lockIx)
  }

  fun buildUnlistBundle(
    asset: Pubkey,
    ownerAuthority: Pubkey,
    marketplaceDelegate: Pubkey,
    programId: Pubkey = MplCorePrograms.DEFAULT_PROGRAM_ID,
    unlockMethodName: String = "unlock",
    revokeMethodName: String = "revoke_delegate"
  ): List<Instruction> {
    val unlockIx = MplCoreInstructions.unlock(asset, ownerAuthority, programId, unlockMethodName)
    val revokeIx = revokeDelegate(asset, ownerAuthority, marketplaceDelegate, programId, revokeMethodName)
    return listOf(unlockIx, revokeIx)
  }
}
