package com.selenus.artemis.rpc

import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import com.selenus.artemis.runtime.PlatformBase64
import com.selenus.artemis.tx.Transaction

/**
 * RpcApi
 *
 * A thin, explicit Solana JSON-RPC wrapper with the methods most used by mobile apps.
 */
open class RpcApi(private val client: RpcClient) {

  private val json = Json { ignoreUnknownKeys = true }

  // Compatibility shim for solana-kt users who expect an .api property
  val api: RpcApi get() = this

  suspend fun callRaw(method: String, params: JsonElement? = null): JsonObject {
    return client.call(method, params)
  }


  suspend fun getLatestBlockhash(commitment: String = "finalized"): BlockhashResult {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    val res = client.call("getLatestBlockhash", params).resultObj()
    val value = res.reqObject("value")
    return BlockhashResult(
      blockhash = value.reqString("blockhash"),
      lastValidBlockHeight = value.reqLong("lastValidBlockHeight")
    )
  }

  @Deprecated("Use getLatestBlockhash")
  suspend fun getRecentBlockhash(commitment: String = "finalized"): BlockhashResult {
    return getLatestBlockhash(commitment)
  }

  suspend fun getBalance(pubkeyBase58: String, commitment: String = "confirmed"): BalanceResult {
    val params = buildJsonArray {
      add(JsonPrimitive(pubkeyBase58))
      add(buildJsonObject { put("commitment", commitment) })
    }
    val res = client.call("getBalance", params).resultObj()
    val v = res.reqLong("value")
    return BalanceResult(v)
  }

  suspend fun getAccountInfo(pubkeyBase58: String, commitment: String = "confirmed", encoding: String = "base64"): JsonObject {
    val params = buildJsonArray {
      add(JsonPrimitive(pubkeyBase58))
      add(buildJsonObject {
        put("encoding", encoding)
        put("commitment", commitment)
      })
    }
    return client.call("getAccountInfo", params).resultObj().jsonObject
  }

  suspend fun getAccountInfoBase64(pubkeyBase58: String, commitment: String = "confirmed"): ByteArray? {
    val res = getAccountInfo(pubkeyBase58, commitment, encoding = "base64")
    val value = res["value"]
    if (value == null || value is JsonNull) return null
    val obj = value.jsonObject
    val dataArr = obj["data"]?.jsonArray ?: return null
    if (dataArr.isEmpty()) return null
    val b64 = dataArr[0].jsonPrimitive.content
    return PlatformBase64.decode(b64)
  }

  /**
   * Get account info with typed response.
   * 
   * @param pubkeyBase58 The account public key in base58
   * @param commitment The commitment level
   * @return AccountInfo with owner, lamports, data, etc.
   */
  suspend fun getAccountInfoParsed(pubkeyBase58: String, commitment: String = "confirmed"): AccountInfo? {
    val json = getAccountInfo(pubkeyBase58, commitment, encoding = "base64")
    return AccountInfo.fromJson(json)
  }

  /**
   * Get token account info with typed response.
   * 
   * @param pubkeyBase58 The token account public key in base58
   * @param commitment The commitment level
   * @return TokenAccountInfo with mint, owner, amount, etc.
   */
  suspend fun getTokenAccountInfoParsed(pubkeyBase58: String, commitment: String = "confirmed"): TokenAccountInfo? {
    val json = getAccountInfo(pubkeyBase58, commitment, encoding = "jsonParsed")
    return TokenAccountInfo.fromParsedJson(json)
  }

  /**
   * Get mint info with typed response.
   * 
   * @param mintBase58 The mint public key in base58
   * @param commitment The commitment level
   * @return MintInfo with authority, supply, decimals, etc.
   */
  suspend fun getMintInfoParsed(mintBase58: String, commitment: String = "confirmed"): MintInfo? {
    val json = getAccountInfo(mintBase58, commitment, encoding = "jsonParsed")
    return MintInfo.fromParsedJson(json)
  }

  suspend fun getMultipleAccounts(pubkeys: List<String>, commitment: String = "confirmed", encoding: String = "base64"): JsonObject {
    val params = buildJsonArray {
      add(JsonArray(pubkeys.map { JsonPrimitive(it) }))
      add(buildJsonObject {
        put("encoding", encoding)
        put("commitment", commitment)
      })
    }
    return client.call("getMultipleAccounts", params).resultObj().jsonObject
  }

  suspend fun getTokenAccountsByOwner(owner: String, mint: String? = null, programId: String? = null, commitment: String = "confirmed"): JsonObject {
    val filter = buildJsonObject {
      if (mint != null) put("mint", mint)
      if (programId != null) put("programId", programId)
    }
    val params = buildJsonArray {
      add(JsonPrimitive(owner))
      add(filter)
      add(buildJsonObject {
        put("encoding", "jsonParsed")
        put("commitment", commitment)
      })
    }
    return client.call("getTokenAccountsByOwner", params).resultObj().jsonObject
  }

  suspend fun getProgramAccounts(programId: String, commitment: String = "confirmed", encoding: String = "base64", filters: JsonArray? = null): JsonArray {
    val cfg = buildJsonObject {
      put("encoding", encoding)
      put("commitment", commitment)
      if (filters != null) put("filters", filters)
    }
    val params = buildJsonArray {
      add(JsonPrimitive(programId))
      add(cfg)
    }
    return client.call("getProgramAccounts", params).resultArr()
  }

  suspend fun simulateTransaction(base64Tx: String, sigVerify: Boolean = false, replaceRecentBlockhash: Boolean = false, commitment: String = "processed"): JsonObject {
    val params = buildJsonArray {
      add(JsonPrimitive(base64Tx))
      add(buildJsonObject {
        put("encoding", "base64")
        put("sigVerify", sigVerify)
        put("replaceRecentBlockhash", replaceRecentBlockhash)
        put("commitment", commitment)
      })
    }
    return client.call("simulateTransaction", params).resultObj().jsonObject
  }

  // Compatibility overload for clients passing ByteArray directly
  suspend fun simulateTransaction(txBytes: ByteArray, sigVerify: Boolean = false, replaceRecentBlockhash: Boolean = false, commitment: String = "processed"): JsonObject {
    val b64 = PlatformBase64.encode(txBytes)
    return simulateTransaction(b64, sigVerify, replaceRecentBlockhash, commitment)
  }

  // Compatibility overload for solana-kt users passing Transaction object
  suspend fun simulateTransaction(transaction: Transaction, sigVerify: Boolean = false, replaceRecentBlockhash: Boolean = false, commitment: String = "processed"): JsonObject {
    return simulateTransaction(transaction.serialize(), sigVerify, replaceRecentBlockhash, commitment)
  }

  /**
   * Simulate a transaction with typed response.
   * Matches sol4k Connection.simulateTransaction() return type.
   */
  suspend fun simulateTransactionTyped(base64Tx: String, sigVerify: Boolean = false, replaceRecentBlockhash: Boolean = false, commitment: String = "processed"): TransactionSimulationResult {
    val result = simulateTransaction(base64Tx, sigVerify, replaceRecentBlockhash, commitment)
    val value = result["value"]?.jsonObject ?: throw RpcException("Missing value in simulateTransaction response")
    return TransactionSimulationResult(
      err = value["err"],
      logs = value["logs"]?.jsonArray?.map { it.jsonPrimitive.content },
      accounts = value["accounts"],
      unitsConsumed = value["unitsConsumed"]?.jsonPrimitive?.longOrNull,
      returnData = value["returnData"]
    )
  }

  suspend fun sendTransaction(base64Tx: String, skipPreflight: Boolean = false, maxRetries: Int? = null, preflightCommitment: String = "processed"): String {
    val params = buildJsonArray {
      add(JsonPrimitive(base64Tx))
      add(buildJsonObject {
        put("encoding", "base64")
        put("skipPreflight", skipPreflight)
        put("preflightCommitment", preflightCommitment)
        if (maxRetries != null) put("maxRetries", maxRetries)
      })
    }
    val res = client.call("sendTransaction", params)
    return res["result"]?.jsonPrimitive?.content ?: throw RpcException("Missing result")
  }

  // Compatibility overload for clients passing ByteArray directly
  suspend fun sendTransaction(txBytes: ByteArray, skipPreflight: Boolean = false, maxRetries: Int? = null, preflightCommitment: String = "processed"): String {
    return sendRawTransaction(txBytes, skipPreflight, maxRetries, preflightCommitment)
  }

  suspend fun sendRawTransaction(txBytes: ByteArray, skipPreflight: Boolean = false, maxRetries: Int? = null, preflightCommitment: String = "processed"): String {
    val b64 = PlatformBase64.encode(txBytes)
    return sendTransaction(b64, skipPreflight, maxRetries, preflightCommitment)
  }

  // Compatibility overload for solana-kt users passing Transaction object
  suspend fun sendTransaction(transaction: Transaction, skipPreflight: Boolean = false, maxRetries: Int? = null, preflightCommitment: String = "processed"): String {
    return sendTransaction(transaction.serialize(), skipPreflight, maxRetries, preflightCommitment)
  }

  suspend fun getSignatureStatuses(signatures: List<String>, searchTransactionHistory: Boolean = true): JsonObject {
    val params = buildJsonArray {
      add(JsonArray(signatures.map { JsonPrimitive(it) }))
      add(buildJsonObject { put("searchTransactionHistory", searchTransactionHistory) })
    }
    return client.call("getSignatureStatuses", params).resultObj().jsonObject
  }

  // Convenience overload for single signature
  suspend fun getSignatureStatus(signature: String, searchTransactionHistory: Boolean = true): JsonObject? {
    val res = getSignatureStatuses(listOf(signature), searchTransactionHistory)
    val value = res["value"]?.jsonArray
    val item = value?.getOrNull(0)
    if (item == null || item is JsonNull) return null
    return item.jsonObject
  }

  suspend fun getTransaction(signature: String, commitment: String = "confirmed", encoding: String = "jsonParsed", maxSupportedTransactionVersion: Int = 0): JsonObject {
    val params = buildJsonArray {
      add(JsonPrimitive(signature))
      add(buildJsonObject {
        put("encoding", encoding)
        put("commitment", commitment)
        put("maxSupportedTransactionVersion", maxSupportedTransactionVersion)
      })
    }
    return client.call("getTransaction", params).resultObj().jsonObject
  }

  @Deprecated("Use getTransaction")
  suspend fun getConfirmedTransaction(signature: String, commitment: String = "confirmed"): JsonObject {
    return getTransaction(signature, commitment)
  }

  suspend fun getRecentPrioritizationFees(addresses: List<String>? = null): JsonArray {
    val params = if (addresses == null) null else buildJsonArray { add(JsonArray(addresses.map { JsonPrimitive(it) })) }
    return client.call("getRecentPrioritizationFees", params).resultArr()
  }

  suspend fun getFeeForMessage(base64Message: String, commitment: String = "processed"): Long {
    val params = buildJsonArray {
      add(JsonPrimitive(base64Message))
      add(buildJsonObject { put("commitment", commitment) })
    }
    val res = client.call("getFeeForMessage", params).resultObj()
    val v = res["value"]
    if (v == null || v is JsonNull) return 0L
    return v.jsonPrimitive.long
  }

  suspend fun getHealth(): String {
    val res = client.call("getHealth", null)
    return res.reqString("result")
  }

  suspend fun getSlot(commitment: String = "finalized"): Long {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getSlot", params).reqLong("result")
  }

  suspend fun getBlockHeight(commitment: String = "finalized"): Long {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getBlockHeight", params).reqLong("result")
  }

  suspend fun getAddressLookupTable(address: String, commitment: String = "finalized"): AddressLookupTableResult {
    val dataBytes = getAccountInfoBase64(address, commitment) ?: return AddressLookupTableResult(null)
    val table = parseAddressLookupTable(dataBytes)
    return AddressLookupTableResult(table)
  }

  suspend fun getSignaturesForAddress(
  address: String,
  limit: Int = 1000,
  before: String? = null,
  until: String? = null,
  commitment: String = "confirmed"
): JsonArray {
  val cfg = buildJsonObject {
    put("limit", limit)
    put("commitment", commitment)
    if (before != null) put("before", before)
    if (until != null) put("until", until)
  }
  val params = buildJsonArray {
    add(JsonPrimitive(address))
    add(cfg)
  }
  return client.call("getSignaturesForAddress", params).resultArr()
}

@Deprecated("Use getSignaturesForAddress")
suspend fun getConfirmedSignaturesForAddress2(address: String, limit: Int = 1000, before: String? = null, until: String? = null, commitment: String = "confirmed"): JsonArray {
  return getSignaturesForAddress(address, limit, before, until, commitment)
}

  suspend fun getBlock(
  slot: Long,
  commitment: String = "confirmed",
  encoding: String = "json",
  maxSupportedTransactionVersion: Int = 0,
  transactionDetails: String = "full",
  rewards: Boolean = false
): JsonObject {
  val cfg = buildJsonObject {
    put("commitment", commitment)
    put("encoding", encoding)
    put("maxSupportedTransactionVersion", maxSupportedTransactionVersion)
    put("transactionDetails", transactionDetails)
    put("rewards", rewards)
  }
  val params = buildJsonArray {
    add(JsonPrimitive(slot))
    add(cfg)
  }
  return client.call("getBlock", params).resultObj().jsonObject
}

  suspend fun getBlocks(startSlot: Long, endSlot: Long? = null, commitment: String = "confirmed"): JsonArray {
  val params = buildJsonArray {
    add(JsonPrimitive(startSlot))
    if (endSlot != null) add(JsonPrimitive(endSlot))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("getBlocks", params).resultArr()
}

  suspend fun getBlockTime(slot: Long): Long? {
  val params = buildJsonArray { add(JsonPrimitive(slot)) }
  val res = client.call("getBlockTime", params)
  val r = res["result"]
  if (r == null || r is JsonNull) return null
  return r.jsonPrimitive.long
}

  suspend fun getEpochInfo(commitment: String = "finalized"): JsonObject {
  val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
  return client.call("getEpochInfo", params).resultObj().jsonObject
}

  /**
   * Get epoch info with typed response.
   * Matches sol4k Connection.getEpochInfo() return type.
   */
  suspend fun getEpochInfoTyped(commitment: String = "finalized"): EpochInfo {
    val json = getEpochInfo(commitment)
    return EpochInfo.fromJson(json)
  }

  suspend fun getEpochSchedule(): JsonObject {
  return client.call("getEpochSchedule", null).resultObj().jsonObject
}

  suspend fun getFirstAvailableBlock(): Long {
  return client.call("getFirstAvailableBlock", null).reqLong("result")
}

  suspend fun getGenesisHash(): String {
  return client.call("getGenesisHash", null).reqString("result")
}

  suspend fun getIdentity(): String {
  val r = client.call("getIdentity", null).resultObj()
  return r.reqString("identity")
}

  suspend fun getInflationGovernor(commitment: String = "finalized"): JsonObject {
  val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
  return client.call("getInflationGovernor", params).resultObj().jsonObject
}

  suspend fun getInflationRate(): JsonObject {
  return client.call("getInflationRate", null).resultObj().jsonObject
}

  suspend fun getLargestAccounts(commitment: String = "confirmed"): JsonObject {
  val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
  return client.call("getLargestAccounts", params).resultObj().jsonObject
}

  suspend fun getLeaderSchedule(epoch: Long? = null, identity: String? = null): JsonObject {
  val params = buildJsonArray {
    if (epoch != null) add(JsonPrimitive(epoch))
    val cfg = buildJsonObject {
      if (identity != null) put("identity", identity)
    }
    if (cfg.isNotEmpty()) add(cfg)
  }
  return client.call("getLeaderSchedule", if (params.isEmpty()) null else params).resultObj().jsonObject
}

  suspend fun getMinimumBalanceForRentExemption(dataLength: Long, commitment: String = "confirmed"): Long {
  val params = buildJsonArray {
    add(JsonPrimitive(dataLength))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("getMinimumBalanceForRentExemption", params).reqLong("result")
}

  suspend fun getSlotLeaders(startSlot: Long, limit: Int): JsonArray {
  val params = buildJsonArray {
    add(JsonPrimitive(startSlot))
    add(JsonPrimitive(limit))
  }
  return client.call("getSlotLeaders", params).resultArr()
}

  suspend fun getSupply(commitment: String = "confirmed"): JsonObject {
  val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
  return client.call("getSupply", params).resultObj().jsonObject
}

  suspend fun getTokenLargestAccounts(mint: String, commitment: String = "confirmed"): JsonObject {
  val params = buildJsonArray {
    add(JsonPrimitive(mint))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("getTokenLargestAccounts", params).resultObj().jsonObject
}

  suspend fun getTokenSupply(mint: String, commitment: String = "confirmed"): JsonObject {
  val params = buildJsonArray {
    add(JsonPrimitive(mint))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("getTokenSupply", params).resultObj().jsonObject
}

  /**
   * Get token supply with typed response.
   */
  suspend fun getTokenSupplyTyped(mint: String, commitment: String = "confirmed"): TokenSupply {
    val json = getTokenSupply(mint, commitment)
    val value = json["value"]?.jsonObject ?: throw RpcException("Missing value in getTokenSupply response")
    return TokenSupply(
      amount = value.reqString("amount"),
      decimals = value.reqInt("decimals"),
      uiAmount = value["uiAmount"]?.jsonPrimitive?.doubleOrNull,
      uiAmountString = value["uiAmountString"]?.jsonPrimitive?.content
    )
  }

  suspend fun requestAirdrop(pubkey: String, lamports: Long, commitment: String = "confirmed"): String {
  val params = buildJsonArray {
    add(JsonPrimitive(pubkey))
    add(JsonPrimitive(lamports))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("requestAirdrop", params).reqString("result")
}

  suspend fun getVersion(): JsonObject {
  return client.call("getVersion", null).resultObj().jsonObject
}

  /**
   * Get version with typed response.
   */
  suspend fun getVersionTyped(): VersionInfo {
    val json = getVersion()
    return VersionInfo.fromJson(json)
  }

suspend fun getVoteAccounts(commitment: String = "confirmed"): JsonObject {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getVoteAccounts", params).resultObj().jsonObject
  }

  suspend fun getClusterNodes(): JsonArray {
    return client.call("getClusterNodes", null).resultArr()
  }

  suspend fun getRecentPerformanceSamples(limit: Int = 10): JsonArray {
    val params = buildJsonArray { add(JsonPrimitive(limit)) }
    return client.call("getRecentPerformanceSamples", params).resultArr()
  }

  // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  // TYPED RESPONSE WRAPPERS
  // Return Kotlin data classes instead of raw JsonObject/JsonArray.
  // Provides sol4k-level type safety for all major RPC methods.
  // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

  /**
   * Get epoch schedule with typed response.
   */
  suspend fun getEpochScheduleTyped(): EpochSchedule {
    val json = getEpochSchedule()
    return EpochSchedule.fromJson(json)
  }

  /**
   * Get inflation governor with typed response.
   */
  suspend fun getInflationGovernorTyped(commitment: String = "finalized"): InflationGovernor {
    val json = getInflationGovernor(commitment)
    return InflationGovernor.fromJson(json)
  }

  /**
   * Get inflation rate with typed response.
   */
  suspend fun getInflationRateTyped(): InflationRate {
    val json = getInflationRate()
    return InflationRate.fromJson(json)
  }

  /**
   * Get supply with typed response.
   */
  suspend fun getSupplyTyped(commitment: String = "confirmed"): Supply {
    val json = getSupply(commitment)
    return Supply.fromJson(json)
  }

  /**
   * Get stake activation with typed response.
   */
  suspend fun getStakeActivationTyped(stakeAccount: String, epoch: Long? = null, commitment: String = "confirmed"): StakeActivation {
    val json = getStakeActivation(stakeAccount, epoch, commitment)
    return StakeActivation.fromJson(json)
  }

  /**
   * Get cluster nodes with typed response.
   */
  suspend fun getClusterNodesTyped(): List<ClusterNode> {
    val json = getClusterNodes()
    return json.map { ClusterNode.fromJson(it.jsonObject) }
  }

  /**
   * Get recent performance samples with typed response.
   */
  suspend fun getRecentPerformanceSamplesTyped(limit: Int = 10): List<PerformanceSample> {
    val json = getRecentPerformanceSamples(limit)
    return json.map { PerformanceSample.fromJson(it.jsonObject) }
  }

  /**
   * Get token account balance with typed response.
   */
  suspend fun getTokenAccountBalanceTyped(account: String, commitment: String = "confirmed"): TokenAccountBalance {
    val json = getTokenAccountBalance(account, commitment)
    return TokenAccountBalance.fromJson(json)
  }

  /**
   * Get largest accounts with typed response.
   */
  suspend fun getLargestAccountsTyped(commitment: String = "confirmed"): List<LargestAccount> {
    val json = getLargestAccounts(commitment)
    val value = json["value"]?.jsonArray ?: throw RpcException("Missing value in getLargestAccounts response")
    return value.map { LargestAccount.fromJson(it.jsonObject) }
  }

  /**
   * Get vote accounts with typed response.
   */
  suspend fun getVoteAccountsTyped(commitment: String = "confirmed"): VoteAccounts {
    val json = getVoteAccounts(commitment)
    return VoteAccounts.fromJson(json)
  }

  /**
   * Get block commitment with typed response.
   */
  suspend fun getBlockCommitmentTyped(slot: Long): BlockCommitment {
    val json = getBlockCommitment(slot)
    return BlockCommitment.fromJson(json)
  }

  /**
   * Get token largest accounts with typed response.
   */
  suspend fun getTokenLargestAccountsTyped(mint: String, commitment: String = "confirmed"): List<TokenLargestAccount> {
    val json = getTokenLargestAccounts(mint, commitment)
    val value = json["value"]?.jsonArray ?: throw RpcException("Missing value in getTokenLargestAccounts response")
    return value.map { TokenLargestAccount.fromJson(it.jsonObject) }
  }

  /**
   * Get signature statuses with typed response.
   */
  suspend fun getSignatureStatusesTyped(signatures: List<String>, searchTransactionHistory: Boolean = true): List<SignatureStatusInfo?> {
    val json = getSignatureStatuses(signatures, searchTransactionHistory)
    val value = json["value"]?.jsonArray ?: throw RpcException("Missing value in getSignatureStatuses response")
    return value.map { elem ->
      if (elem is JsonNull) null else SignatureStatusInfo.fromJson(elem.jsonObject)
    }
  }

  /**
   * Get signatures for address with typed response.
   */
  suspend fun getSignaturesForAddressTyped(
    address: String,
    limit: Int = 1000,
    before: String? = null,
    until: String? = null,
    commitment: String = "confirmed"
  ): List<SignatureInfo> {
    val json = getSignaturesForAddress(address, limit, before, until, commitment)
    return json.map { elem ->
      val obj = elem.jsonObject
      SignatureInfo(
        signature = obj.reqString("signature"),
        slot = obj.reqLong("slot"),
        err = obj["err"],
        memo = obj["memo"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
        blockTime = obj["blockTime"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
        confirmationStatus = obj["confirmationStatus"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
      )
    }
  }

  /**
   * Get recent prioritization fees with typed response.
   */
  suspend fun getRecentPrioritizationFeesTyped(addresses: List<String>? = null): List<PrioritizationFee> {
    val json = getRecentPrioritizationFees(addresses)
    return json.map { elem ->
      val obj = elem.jsonObject
      PrioritizationFee(
        slot = obj.reqLong("slot"),
        prioritizationFee = obj.reqLong("prioritizationFee")
      )
    }
  }


  /**
   * confirmTransaction
   *
   * Mobile-friendly confirmation helper that polls getSignatureStatuses.
   */
  suspend fun confirmTransaction(
    signature: String,
    maxAttempts: Int = 30,
    sleepMs: Long = 500,
    requireConfirmationStatus: String = "confirmed"
  ): Boolean {
    var attempt = 0
    while (attempt < maxAttempts) {
      attempt += 1
      val st = getSignatureStatuses(listOf(signature), searchTransactionHistory = true)
      val value = st["value"]?.jsonArray
      val item = value?.getOrNull(0)
      if (item != null && item !is JsonNull) {
        val obj = item.jsonObject
        val err = obj["err"]
        if (err != null && err !is JsonNull) return false
        val status = obj["confirmationStatus"]?.jsonPrimitive?.content
        if (status != null) {
          // finalized > confirmed > processed
          if (status == "finalized") return true
          if (status == "confirmed" && (requireConfirmationStatus == "confirmed" || requireConfirmationStatus == "processed")) return true
          if (status == "processed" && requireConfirmationStatus == "processed") return true
        }
      }
      delay(sleepMs)
    }
    return false
  }

  suspend fun sendAndConfirmRawTransaction(
    txBytes: ByteArray,
    skipPreflight: Boolean = false,
    maxRetries: Int? = null,
    preflightCommitment: String = "processed",
    confirmCommitment: String = "confirmed"
  ): String {
    val sig = sendRawTransaction(txBytes, skipPreflight, maxRetries, preflightCommitment)
    val ok = confirmTransaction(sig, requireConfirmationStatus = confirmCommitment)
    if (!ok) throw RpcException("transaction_not_confirmed")
    return sig
  }


  suspend fun getFees(commitment: String = "confirmed"): JsonObject {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getFees", params).resultObj().jsonObject
  }

  suspend fun getFeeCalculatorForBlockhash(blockhash: String, commitment: String = "confirmed"): JsonObject {
    val params = buildJsonArray {
      add(JsonPrimitive(blockhash))
      add(buildJsonObject { put("commitment", commitment) })
    }
    return client.call("getFeeCalculatorForBlockhash", params).resultObj().jsonObject
  }

  suspend fun getMultipleAccountsBase64(pubkeys: List<String>, commitment: String = "confirmed"): List<ByteArray?> {
    val res = getMultipleAccounts(pubkeys, commitment = commitment, encoding = "base64")
    val value = res["value"]?.jsonArray ?: return emptyList()
    return value.map { v ->
      if (v is JsonNull) null else {
        val dataArr = v.jsonObject["data"]?.jsonArray ?: return@map null
        val b64 = dataArr.getOrNull(0)?.jsonPrimitive?.content ?: return@map null
        PlatformBase64.decode(b64)
      }
    }
  }

  suspend fun getTokenAccountBalance(account: String, commitment: String = "confirmed"): JsonObject {
    val params = buildJsonArray {
      add(JsonPrimitive(account))
      add(buildJsonObject { put("commitment", commitment) })
    }
    return client.call("getTokenAccountBalance", params).resultObj().jsonObject
  }

  suspend fun getTokenAccountsByDelegate(delegate: String, mint: String? = null, programId: String? = null, commitment: String = "confirmed"): JsonObject {
    val filter = buildJsonObject {
      if (mint != null) put("mint", mint)
      if (programId != null) put("programId", programId)
    }
    val params = buildJsonArray {
      add(JsonPrimitive(delegate))
      add(filter)
      add(buildJsonObject {
        put("encoding", "jsonParsed")
        put("commitment", commitment)
      })
    }
    return client.call("getTokenAccountsByDelegate", params).resultObj().jsonObject
  }

  suspend fun getTokenAccountsByOwnerBase64(owner: String, mint: String? = null, programId: String? = null, commitment: String = "confirmed"): JsonObject {
    val filter = buildJsonObject {
      if (mint != null) put("mint", mint)
      if (programId != null) put("programId", programId)
    }
    val params = buildJsonArray {
      add(JsonPrimitive(owner))
      add(filter)
      add(buildJsonObject {
        put("encoding", "base64")
        put("commitment", commitment)
      })
    }
    return client.call("getTokenAccountsByOwner", params).resultObj().jsonObject
  }

  suspend fun getStakeActivation(stakeAccount: String, epoch: Long? = null, commitment: String = "confirmed"): JsonObject {
    val cfg = buildJsonObject {
      put("commitment", commitment)
      if (epoch != null) put("epoch", epoch)
    }
    val params = buildJsonArray {
      add(JsonPrimitive(stakeAccount))
      add(cfg)
    }
    return client.call("getStakeActivation", params).resultObj().jsonObject
  }

  suspend fun getLargestAccountsFilter(filter: String = "circulating", commitment: String = "confirmed"): JsonObject {
    val params = buildJsonArray {
      add(buildJsonObject {
        put("filter", filter)
        put("commitment", commitment)
      })
    }
    return client.call("getLargestAccounts", params).resultObj().jsonObject
  }

  suspend fun getSupplyWithExcludeNonCirculating(commitment: String = "confirmed", excludeNonCirculatingAccountsList: Boolean = false): JsonObject {
    val params = buildJsonArray {
      add(buildJsonObject {
        put("commitment", commitment)
        put("excludeNonCirculatingAccountsList", excludeNonCirculatingAccountsList)
      })
    }
    return client.call("getSupply", params).resultObj().jsonObject
  }

  suspend fun getTransactionCount(commitment: String = "finalized"): Long {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getTransactionCount", params).reqLong("result")
  }

  suspend fun getMinimumLedgerSlot(): Long {
    return client.call("minimumLedgerSlot", null).reqLong("result")
  }

  suspend fun getMaxRetransmitSlot(): Long {
    return client.call("getMaxRetransmitSlot", null).reqLong("result")
  }

  suspend fun getMaxShredInsertSlot(): Long {
    return client.call("getMaxShredInsertSlot", null).reqLong("result")
  }

  suspend fun getBlockCommitment(slot: Long): JsonObject {
    val params = buildJsonArray { add(JsonPrimitive(slot)) }
    return client.call("getBlockCommitment", params).resultObj().jsonObject
  }

  suspend fun getRecentPrioritizationFeesFull(addresses: List<String>? = null): JsonArray {
    return getRecentPrioritizationFees(addresses)
  }

  suspend fun isBlockhashValid(blockhash: String, commitment: String = "confirmed"): Boolean {
    val params = buildJsonArray {
      add(JsonPrimitive(blockhash))
      add(buildJsonObject { put("commitment", commitment) })
    }
    val res = client.call("isBlockhashValid", params).resultObj()
    return res.reqBoolean("value")
  }

  // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
  // JSON-RPC BATCH API
  // Send multiple RPC calls in a single HTTP request.
  // No other Kotlin Solana SDK provides this â€” Artemis is first-to-market.
  // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

  /**
   * Execute multiple RPC calls in a single HTTP round-trip.
   *
   * This uses JSON-RPC batch mode to send an array of requests and receive
   * an array of responses, dramatically reducing latency for multi-query
   * workflows (portfolio screens, dashboard hydration, etc.).
   *
   * ```kotlin
   * val (balance, slot, health) = connection.batch {
   *     add("getBalance", buildJsonArray { add(JsonPrimitive(pubkey)); add(buildJsonObject { put("commitment", "confirmed") }) })
   *     add("getSlot")
   *     add("getHealth")
   * }
   * // balance, slot, health are JsonObject responses
   * ```
   *
   * @param builder Lambda that adds requests to the batch
   * @return List of JSON-RPC responses in the same order as requests
   */
  suspend fun batch(builder: BatchRequestBuilder.() -> Unit): List<JsonObject> {
    val b = BatchRequestBuilder()
    b.builder()
    return client.callBatch(b.requests)
  }

  /**
   * Builder for JSON-RPC batch requests.
   */
  class BatchRequestBuilder {
    internal val requests = mutableListOf<Pair<String, JsonElement?>>()

    /** Add a raw RPC method call to the batch. */
    fun add(method: String, params: JsonElement? = null) {
      requests.add(method to params)
    }

    /** Add a getBalance call. */
    fun getBalance(pubkey: String, commitment: String = "confirmed") {
      add("getBalance", buildJsonArray {
        add(JsonPrimitive(pubkey))
        add(buildJsonObject { put("commitment", commitment) })
      })
    }

    /** Add a getAccountInfo call. */
    fun getAccountInfo(pubkey: String, commitment: String = "confirmed", encoding: String = "base64") {
      add("getAccountInfo", buildJsonArray {
        add(JsonPrimitive(pubkey))
        add(buildJsonObject { put("encoding", encoding); put("commitment", commitment) })
      })
    }

    /** Add a getSlot call. */
    fun getSlot(commitment: String = "finalized") {
      add("getSlot", buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) })
    }

    /** Add a getBlockHeight call. */
    fun getBlockHeight(commitment: String = "finalized") {
      add("getBlockHeight", buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) })
    }

    /** Add a getLatestBlockhash call. */
    fun getLatestBlockhash(commitment: String = "finalized") {
      add("getLatestBlockhash", buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) })
    }

    /** Add a getTokenAccountBalance call. */
    fun getTokenAccountBalance(account: String, commitment: String = "confirmed") {
      add("getTokenAccountBalance", buildJsonArray {
        add(JsonPrimitive(account))
        add(buildJsonObject { put("commitment", commitment) })
      })
    }

    /** Add a getMinimumBalanceForRentExemption call. */
    fun getMinimumBalanceForRentExemption(dataLength: Long, commitment: String = "confirmed") {
      add("getMinimumBalanceForRentExemption", buildJsonArray {
        add(JsonPrimitive(dataLength))
        add(buildJsonObject { put("commitment", commitment) })
      })
    }

    /** Add a getSignatureStatuses call. */
    fun getSignatureStatuses(signatures: List<String>, searchTransactionHistory: Boolean = true) {
      add("getSignatureStatuses", buildJsonArray {
        add(JsonArray(signatures.map { JsonPrimitive(it) }))
        add(buildJsonObject { put("searchTransactionHistory", searchTransactionHistory) })
      })
    }

    /** Add a getHealth call. */
    fun getHealth() { add("getHealth") }

    /** Add a getVersion call. */
    fun getVersion() { add("getVersion") }

    /** Add a getEpochInfo call. */
    fun getEpochInfo(commitment: String = "finalized") {
      add("getEpochInfo", buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) })
    }

    /** Add a getTransactionCount call. */
    fun getTransactionCount(commitment: String = "finalized") {
      add("getTransactionCount", buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) })
    }
  }

  private fun JsonObject.resultObj(): JsonObject {
    return this["result"]?.jsonObject ?: throw RpcException("Missing result")
  }

  private fun JsonObject.resultArr(): JsonArray {
    return this["result"]?.jsonArray ?: throw RpcException("Missing result")
  }
}

internal fun parseAddressLookupTable(dataBytes: ByteArray): AddressLookupTable? {
    try {
        var offset = 0
        fun remaining() = dataBytes.size - offset
        fun readByte(): Int {
            if (remaining() < 1) return -1
            return dataBytes[offset++].toInt() and 0xFF
        }
        fun readIntLE(): Int {
            if (remaining() < 4) throw IndexOutOfBoundsException()
            val v = (dataBytes[offset].toInt() and 0xFF) or
                    ((dataBytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((dataBytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((dataBytes[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return v
        }
        fun readLongLE(): Long {
            if (remaining() < 8) throw IndexOutOfBoundsException()
            val v = (dataBytes[offset].toLong() and 0xFF) or
                    ((dataBytes[offset + 1].toLong() and 0xFF) shl 8) or
                    ((dataBytes[offset + 2].toLong() and 0xFF) shl 16) or
                    ((dataBytes[offset + 3].toLong() and 0xFF) shl 24) or
                    ((dataBytes[offset + 4].toLong() and 0xFF) shl 32) or
                    ((dataBytes[offset + 5].toLong() and 0xFF) shl 40) or
                    ((dataBytes[offset + 6].toLong() and 0xFF) shl 48) or
                    ((dataBytes[offset + 7].toLong() and 0xFF) shl 56)
            offset += 8
            return v
        }
        fun readBytes(n: Int): ByteArray {
            if (remaining() < n) throw IndexOutOfBoundsException()
            val result = dataBytes.copyOfRange(offset, offset + n)
            offset += n
            return result
        }

        // 1. Check Type (u32) - 1 is LookupTable
        if (remaining() < 4) return null
        val type = readIntLE()
        if (type != 1) return null

        // 2. deactivationSlot (u64)
        val deactivationSlot = readLongLE()

        // 3. lastExtendedSlot (u64)
        val lastExtendedSlot = readLongLE()

        // 4. lastExtendedSlotStartIndex (u8)
        val lastExtendedSlotStartIndex = readByte()

        // 5. Authority (Option<Pubkey>)
        val hasAuthority = readByte() == 1
        var authority: String? = null
        if (hasAuthority) {
             authority = com.selenus.artemis.runtime.Base58.encode(readBytes(32))
        }

        // 6. Addresses (Vec<Pubkey> -> u64 len + items)
        val len = readLongLE().toInt()
        if (remaining() < len * 32) return null

        val addresses = ArrayList<String>(len)
        for (i in 0 until len) {
            addresses.add(com.selenus.artemis.runtime.Base58.encode(readBytes(32)))
        }

        return AddressLookupTable(
            deactivationSlot,
            lastExtendedSlot,
            lastExtendedSlotStartIndex,
            authority,
            addresses
        )

    } catch (e: Exception) {
        return null
    }
}
