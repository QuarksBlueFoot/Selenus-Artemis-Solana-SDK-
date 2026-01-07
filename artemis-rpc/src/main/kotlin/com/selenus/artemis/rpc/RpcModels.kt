package com.selenus.artemis.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class BlockhashResult(
  val blockhash: String,
  val lastValidBlockHeight: Long
)

@Serializable
data class BalanceResult(
  val lamports: Long
)

@Serializable
data class SignatureStatus(
  val slot: Long?,
  val confirmations: Long?,
  val err: JsonElement?,
  val confirmationStatus: String?
)

@Serializable
data class AddressLookupTableResult(
  val value: AddressLookupTable?
)

@Serializable
data class AddressLookupTable(
  val deactivationSlot: Long,
  val lastExtendedSlot: Long,
  val lastExtendedSlotStartIndex: Int,
  val authority: String? = null,
  val addresses: List<String>
)
