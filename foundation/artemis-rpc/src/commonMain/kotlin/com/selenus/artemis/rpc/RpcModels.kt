package com.selenus.artemis.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// TYPED RPC RESPONSE MODELS
// Parity with sol4k typed responses for getEpochInfo, getTokenSupply, etc.
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
        absoluteSlot = json.reqLong("absoluteSlot"),
        blockHeight = json.reqLong("blockHeight"),
        epoch = json.reqLong("epoch"),
        slotIndex = json.reqLong("slotIndex"),
        slotsInEpoch = json.reqLong("slotsInEpoch"),
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
        solanaCore = json.reqString("solana-core"),
        featureSet = json["feature-set"]?.asLongOrNull()
      )
    }
  }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// TYPED RPC RESPONSE MODELS â€” EXTENDED
// Full parity with sol4k typed responses and Solana JSON-RPC spec.
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

/**
 * Epoch schedule from getEpochSchedule.
 */
@Serializable
data class EpochSchedule(
  val slotsPerEpoch: Long,
  val leaderScheduleSlotOffset: Long,
  val warmup: Boolean,
  val firstNormalEpoch: Long,
  val firstNormalSlot: Long
) {
  companion object {
    fun fromJson(json: JsonObject): EpochSchedule = EpochSchedule(
      slotsPerEpoch = json.reqLong("slotsPerEpoch"),
      leaderScheduleSlotOffset = json.reqLong("leaderScheduleSlotOffset"),
      warmup = json.reqBoolean("warmup"),
      firstNormalEpoch = json.reqLong("firstNormalEpoch"),
      firstNormalSlot = json.reqLong("firstNormalSlot")
    )
  }
}

/**
 * Inflation governor parameters from getInflationGovernor.
 */
@Serializable
data class InflationGovernor(
  val initial: Double,
  val terminal: Double,
  val taper: Double,
  val foundation: Double,
  val foundationTerm: Double
) {
  companion object {
    fun fromJson(json: JsonObject): InflationGovernor = InflationGovernor(
      initial = json.reqDouble("initial"),
      terminal = json.reqDouble("terminal"),
      taper = json.reqDouble("taper"),
      foundation = json.reqDouble("foundation"),
      foundationTerm = json.reqDouble("foundationTerm")
    )
  }
}

/**
 * Current inflation rate from getInflationRate.
 */
@Serializable
data class InflationRate(
  val total: Double,
  val validator: Double,
  val foundation: Double,
  val epoch: Long
) {
  companion object {
    fun fromJson(json: JsonObject): InflationRate = InflationRate(
      total = json.reqDouble("total"),
      validator = json.reqDouble("validator"),
      foundation = json.reqDouble("foundation"),
      epoch = json.reqLong("epoch")
    )
  }
}

/**
 * Supply info from getSupply.
 */
@Serializable
data class Supply(
  val total: Long,
  val circulating: Long,
  val nonCirculating: Long,
  val nonCirculatingAccounts: List<String>
) {
  companion object {
    fun fromJson(json: JsonObject): Supply {
      val value = json["value"]?.jsonObject ?: json
      return Supply(
        total = value.reqLong("total"),
        circulating = value.reqLong("circulating"),
        nonCirculating = value.reqLong("nonCirculating"),
        nonCirculatingAccounts = value["nonCirculatingAccounts"]?.jsonArray?.map { it.asString() } ?: emptyList()
      )
    }
  }
}

/**
 * Stake activation from getStakeActivation.
 */
@Serializable
data class StakeActivation(
  val state: String,
  val active: Long,
  val inactive: Long
) {
  companion object {
    fun fromJson(json: JsonObject): StakeActivation = StakeActivation(
      state = json.reqString("state"),
      active = json.reqLong("active"),
      inactive = json.reqLong("inactive")
    )
  }
}

/**
 * Cluster node info from getClusterNodes.
 */
@Serializable
data class ClusterNode(
  val pubkey: String,
  val gossip: String? = null,
  val tpu: String? = null,
  val tpuQuic: String? = null,
  val rpc: String? = null,
  val version: String? = null,
  val featureSet: Long? = null,
  val shredVersion: Int? = null
) {
  companion object {
    fun fromJson(json: JsonObject): ClusterNode = ClusterNode(
      pubkey = json.reqString("pubkey"),
      gossip = json["gossip"]?.takeUnless { it is JsonNull }?.asString(),
      tpu = json["tpu"]?.takeUnless { it is JsonNull }?.asString(),
      tpuQuic = json["tpuQuic"]?.takeUnless { it is JsonNull }?.asString(),
      rpc = json["rpc"]?.takeUnless { it is JsonNull }?.asString(),
      version = json["version"]?.takeUnless { it is JsonNull }?.asString(),
      featureSet = json["featureSet"]?.asLongOrNull(),
      shredVersion = json["shredVersion"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.int }
    )
  }
}

/**
 * Performance sample from getRecentPerformanceSamples.
 */
@Serializable
data class PerformanceSample(
  val slot: Long,
  val numTransactions: Long,
  val numSlots: Long,
  val samplePeriodSecs: Int,
  val numNonVoteTransactions: Long? = null
) {
  companion object {
    fun fromJson(json: JsonObject): PerformanceSample = PerformanceSample(
      slot = json.reqLong("slot"),
      numTransactions = json.reqLong("numTransactions"),
      numSlots = json.reqLong("numSlots"),
      samplePeriodSecs = json.reqInt("samplePeriodSecs"),
      numNonVoteTransactions = json["numNonVoteTransactions"]?.asLongOrNull()
    )
  }
}

/**
 * Token account balance from getTokenAccountBalance.
 */
@Serializable
data class TokenAccountBalance(
  val amount: String,
  val decimals: Int,
  val uiAmount: Double?,
  val uiAmountString: String?
) {
  companion object {
    fun fromJson(json: JsonObject): TokenAccountBalance {
      val value = json["value"]?.jsonObject ?: json
      return TokenAccountBalance(
        amount = value.reqString("amount"),
        decimals = value.reqInt("decimals"),
        uiAmount = value["uiAmount"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull,
        uiAmountString = value["uiAmountString"]?.takeUnless { it is JsonNull }?.asString()
      )
    }
  }
}

/**
 * Largest account entry from getLargestAccounts.
 */
@Serializable
data class LargestAccount(
  val address: String,
  val lamports: Long
) {
  companion object {
    fun fromJson(json: JsonObject): LargestAccount = LargestAccount(
      address = json.reqString("address"),
      lamports = json.reqLong("lamports")
    )
  }
}

/**
 * Vote account info from getVoteAccounts.
 */
@Serializable
data class VoteAccountInfo(
  val votePubkey: String,
  val nodePubkey: String,
  val activatedStake: Long,
  val epochVoteAccount: Boolean,
  val commission: Int,
  val lastVote: Long,
  val rootSlot: Long? = null
) {
  companion object {
    fun fromJson(json: JsonObject): VoteAccountInfo = VoteAccountInfo(
      votePubkey = json.reqString("votePubkey"),
      nodePubkey = json.reqString("nodePubkey"),
      activatedStake = json.reqLong("activatedStake"),
      epochVoteAccount = json.reqBoolean("epochVoteAccount"),
      commission = json.reqInt("commission"),
      lastVote = json.reqLong("lastVote"),
      rootSlot = json["rootSlot"]?.asLongOrNull()
    )
  }
}

/**
 * Grouped vote accounts result from getVoteAccounts.
 */
data class VoteAccounts(
  val current: List<VoteAccountInfo>,
  val delinquent: List<VoteAccountInfo>
) {
  companion object {
    fun fromJson(json: JsonObject): VoteAccounts = VoteAccounts(
      current = json.reqArray("current").map { VoteAccountInfo.fromJson(it.jsonObject) },
      delinquent = json.reqArray("delinquent").map { VoteAccountInfo.fromJson(it.jsonObject) }
    )
  }
}

/**
 * Block commitment from getBlockCommitment.
 */
data class BlockCommitment(
  val commitment: List<Long>?,
  val totalStake: Long
) {
  companion object {
    fun fromJson(json: JsonObject): BlockCommitment = BlockCommitment(
      commitment = json["commitment"]?.takeUnless { it is JsonNull }?.jsonArray?.map { it.asLong() },
      totalStake = json.reqLong("totalStake")
    )
  }
}

/**
 * Token largest account entry from getTokenLargestAccounts.
 */
@Serializable
data class TokenLargestAccount(
  val address: String,
  val amount: String,
  val decimals: Int,
  val uiAmount: Double?,
  val uiAmountString: String?
) {
  companion object {
    fun fromJson(json: JsonObject): TokenLargestAccount = TokenLargestAccount(
      address = json.reqString("address"),
      amount = json.reqString("amount"),
      decimals = json.reqInt("decimals"),
      uiAmount = json["uiAmount"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull,
      uiAmountString = json["uiAmountString"]?.takeUnless { it is JsonNull }?.asString()
    )
  }
}

/**
 * Signature status with typed fields.
 */
data class SignatureStatusInfo(
  val slot: Long,
  val confirmations: Long?,
  val err: JsonElement?,
  val confirmationStatus: String?
) {
  val isConfirmed: Boolean get() = confirmationStatus == "confirmed" || confirmationStatus == "finalized"
  val isFinalized: Boolean get() = confirmationStatus == "finalized"
  val hasError: Boolean get() = err != null && err !is JsonNull

  companion object {
    fun fromJson(json: JsonObject): SignatureStatusInfo = SignatureStatusInfo(
      slot = json.reqLong("slot"),
      confirmations = json["confirmations"]?.asLongOrNull(),
      err = json["err"],
      confirmationStatus = json["confirmationStatus"]?.takeUnless { it is JsonNull }?.asString()
    )
  }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// JSON EXTENSION HELPERS
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

internal fun JsonElement.asLong(): Long =
  jsonPrimitive.long

internal fun JsonElement.asLongOrNull(): Long? =
  try { jsonPrimitive.longOrNull } catch (_: Exception) { null }

internal fun JsonElement.asString(): String =
  jsonPrimitive.content

/**
 * Strictly require a field on a JSON-RPC response object. Throws a
 * descriptive [RpcException] when the field is absent or null, instead of
 * the opaque NullPointerException produced by `obj["x"]!!`.
 */
internal fun JsonObject.req(field: String): JsonElement {
  val v = this[field]
  if (v == null || v is JsonNull) {
    throw RpcException("Malformed RPC response: missing required field '$field'")
  }
  return v
}

internal fun JsonObject.reqObject(field: String): JsonObject = req(field).jsonObject
internal fun JsonObject.reqArray(field: String): JsonArray = req(field).jsonArray
internal fun JsonObject.reqLong(field: String): Long = req(field).jsonPrimitive.long
internal fun JsonObject.reqInt(field: String): Int = req(field).jsonPrimitive.int
internal fun JsonObject.reqDouble(field: String): Double = req(field).jsonPrimitive.double
internal fun JsonObject.reqBoolean(field: String): Boolean = req(field).jsonPrimitive.boolean
internal fun JsonObject.reqString(field: String): String = req(field).jsonPrimitive.content
