package com.selenus.artemis.mplcore

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Pda

/**
 * PDA helpers for MPL Core.
 *
 * Many apps create assets/collections as newly generated keypairs, but some ecosystems
 * derive deterministic addresses. These helpers provide common deterministic options.
 */
object MplCorePdas {

  /**
   * Asset PDA often derived from ["asset", owner, name] in some deployments.
   * Since Core seeds can vary by client, treat this as an optional helper.
   */
  fun assetPda(owner: Pubkey, name: String): Pubkey {
    return Pda.findProgramAddress(
      seeds = listOf("asset".encodeToByteArray(), owner.bytes, name.encodeToByteArray()),
      programId = MplCorePrograms.DEFAULT_PROGRAM_ID
    ).address
  }

  fun collectionPda(authority: Pubkey, name: String): Pubkey {
    return Pda.findProgramAddress(
      seeds = listOf("collection".encodeToByteArray(), authority.bytes, name.encodeToByteArray()),
      programId = MplCorePrograms.DEFAULT_PROGRAM_ID
    ).address
  }
}
