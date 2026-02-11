package com.selenus.artemis.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

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

// ════════════════════════════════════════════════════════════════════════════
// TYPED RPC RESPONSE MODELS
// Parity with sol4k typed responses for getEpochInfo, getTokenSupply, etc.
// ════════════════════════════════════════════════════════════════════════════

/**
 * Epoch info response from getEpochInfo.
 * Matches sol4k EpochInfo data class.
 */
@Serializable
data class EpochInfo(
  val absoluteSlot: Long,
  val blockHeight: Long,
  val epoch: Long,
  val slotIndex: Long,
  val slotsInEpoch: Long,
  val transactionCount: Long? = null
) {
  companion object {
    fun fromJson(json: JsonObject): EpochInfo {
      return EpochInfo(
        absoluteSlot = json["absoluteSlot"]!!.asLong(),
        blockHeight = json["blockHeight"]!!.asLong(),
        epoch = json["epoch"]!!.asLong(),
        slotIndex = json["slotIndex"]!!.asLong(),
        slotsInEpoch = json["slotsInEpoch"]!!.asLong(),
        transactionCount = json["transactionCount"]?.asLongOrNull()
      )
    }
  }
}

/**
 * Token supply from getTokenSupply.
 */
@Serializable
data class TokenSupply(
  val amount: String,
  val decimals: Int,
  val uiAmount: Double?,
  val uiAmountString: String?
)

/**
 * Prioritization fee from getRecentPrioritizationFees.
 * Matches sol4k PrioritizationFee.
 */
@Serializable
data class PrioritizationFee(
  val slot: Long,
  val prioritizationFee: Long
)

/**
 * Transaction simulation result.
 * Matches sol4k TransactionSimulation.
 */
@Serializable
data class TransactionSimulationResult(
  val err: JsonElement? = null,
  val logs: List<String>? = null,
  val accounts: JsonElement? = null,
  val unitsConsumed: Long? = null,
  val returnData: JsonElement? = null
) {
  val isSuccess: Boolean get() = err == null
  val isError: Boolean get() = err != null
}

/**
 * Signature status for a list of signatures.
 */
@Serializable
data class SignatureInfo(
  val signature: String,
  val slot: Long,
  val err: JsonElement? = null,
  val memo: String? = null,
  val blockTime: Long? = null,
  val confirmationStatus: String? = null
)

/**
 * Version info from getVersion.
 */
@Serializable
data class VersionInfo(
  val solanaCore: String,
  val featureSet: Long? = null
) {
  companion object {
    fun fromJson(json: JsonObject): VersionInfo {
      return VersionInfo(
        solanaCore = json["solana-core"]!!.asString(),
        featureSet = json["feature-set"]?.asLongOrNull()
      )
    }
  }
}

// ════════════════════════════════════════════════════════════════════════════
// JSON EXTENSION HELPERS
// ════════════════════════════════════════════════════════════════════════════

internal fun JsonElement.asLong(): Long = 
  jsonPrimitive.long

internal fun JsonElement.asLongOrNull(): Long? = 
  try { jsonPrimitive.longOrNull } catch (_: Exception) { null }

internal fun JsonElement.asString(): String = 
  jsonPrimitive.content
