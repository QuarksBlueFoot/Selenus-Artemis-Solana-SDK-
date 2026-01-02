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
      slotsPerEpoch = json["slotsPerEpoch"]!!.asLong(),
      leaderScheduleSlotOffset = json["leaderScheduleSlotOffset"]!!.asLong(),
      warmup = json["warmup"]!!.jsonPrimitive.boolean,
      firstNormalEpoch = json["firstNormalEpoch"]!!.asLong(),
      firstNormalSlot = json["firstNormalSlot"]!!.asLong()
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
      initial = json["initial"]!!.jsonPrimitive.double,
      terminal = json["terminal"]!!.jsonPrimitive.double,
      taper = json["taper"]!!.jsonPrimitive.double,
      foundation = json["foundation"]!!.jsonPrimitive.double,
      foundationTerm = json["foundationTerm"]!!.jsonPrimitive.double
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
      total = json["total"]!!.jsonPrimitive.double,
      validator = json["validator"]!!.jsonPrimitive.double,
      foundation = json["foundation"]!!.jsonPrimitive.double,
      epoch = json["epoch"]!!.asLong()
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
        total = value["total"]!!.asLong(),
        circulating = value["circulating"]!!.asLong(),
        nonCirculating = value["nonCirculating"]!!.asLong(),
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
      state = json["state"]!!.asString(),
      active = json["active"]!!.asLong(),
      inactive = json["inactive"]!!.asLong()
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
      pubkey = json["pubkey"]!!.asString(),
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
      slot = json["slot"]!!.asLong(),
      numTransactions = json["numTransactions"]!!.asLong(),
      numSlots = json["numSlots"]!!.asLong(),
      samplePeriodSecs = json["samplePeriodSecs"]!!.jsonPrimitive.int,
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
        amount = value["amount"]!!.asString(),
        decimals = value["decimals"]!!.jsonPrimitive.int,
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
      address = json["address"]!!.asString(),
      lamports = json["lamports"]!!.asLong()
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
      votePubkey = json["votePubkey"]!!.asString(),
      nodePubkey = json["nodePubkey"]!!.asString(),
      activatedStake = json["activatedStake"]!!.asLong(),
      epochVoteAccount = json["epochVoteAccount"]!!.jsonPrimitive.boolean,
      commission = json["commission"]!!.jsonPrimitive.int,
      lastVote = json["lastVote"]!!.asLong(),
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
      current = json["current"]!!.jsonArray.map { VoteAccountInfo.fromJson(it.jsonObject) },
      delinquent = json["delinquent"]!!.jsonArray.map { VoteAccountInfo.fromJson(it.jsonObject) }
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
      totalStake = json["totalStake"]!!.asLong()
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
      address = json["address"]!!.asString(),
      amount = json["amount"]!!.asString(),
      decimals = json["decimals"]!!.jsonPrimitive.int,
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
      slot = json["slot"]!!.asLong(),
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
