package com.selenus.artemis.candymachine.planner

import com.selenus.artemis.candymachine.CandyGuardMintV2

data class MintPlan(
  val accounts: CandyGuardMintV2.Accounts,
  val mintArgsBorsh: ByteArray?,
  val warnings: List<String> = emptyList(),
)
