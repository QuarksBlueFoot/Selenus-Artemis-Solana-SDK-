package com.selenus.artemis.candymachine.guards

import com.selenus.artemis.runtime.Pubkey

data class AllowlistProof(
  /** Merkle proof nodes; each must be 32 bytes. */
  val proof: List<ByteArray>,
)

data class GatekeeperTokenArg(
  val token: Pubkey?,
)

data class GuardArgs(
  val allowlistProof: AllowlistProof? = null,
  val mintLimitId: Int? = null,
  val allocationId: Int? = null,
  val gatekeeperToken: GatekeeperTokenArg? = null,
)
