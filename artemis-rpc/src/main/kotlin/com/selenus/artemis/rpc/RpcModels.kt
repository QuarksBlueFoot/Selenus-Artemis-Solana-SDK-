package com.selenus.artemis.rpc

import kotlinx.serialization.json.JsonObject

data class BlockhashResult(
  val blockhash: String,
  val lastValidBlockHeight: Long
)

data class BalanceResult(
  val lamports: Long
)

data class SignatureStatus(
  val slot: Long?,
  val confirmations: Long?,
  val err: String?,
  val confirmationStatus: String?
)

data class AddressLookupTableResult(
  val value: JsonObject?
)
