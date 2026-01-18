package com.selenus.artemis.rpc

import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import java.util.Base64
import com.selenus.artemis.tx.Transaction

/**
 * RpcApi
 *
 * A thin, explicit Solana JSON-RPC wrapper with the methods most used by mobile apps.
 */
open class RpcApi(private val client: JsonRpcClient) {

  private val json = Json { ignoreUnknownKeys = true }

  // Compatibility shim for solana-kt users who expect an .api property
  val api: RpcApi get() = this

  suspend fun callRaw(method: String, params: JsonElement? = null): JsonObject {
    return client.call(method, params)
  }


  suspend fun getLatestBlockhash(commitment: String = "finalized"): BlockhashResult {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    val res = client.call("getLatestBlockhash", params).resultObj()
    val value = res["value"]!!.jsonObject
    return BlockhashResult(
      blockhash = value["blockhash"]!!.jsonPrimitive.content,
      lastValidBlockHeight = value["lastValidBlockHeight"]!!.jsonPrimitive.long
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
    val v = res["value"]!!.jsonPrimitive.long
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
    return Base64.getDecoder().decode(b64)
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
    val b64 = Base64.getEncoder().encodeToString(txBytes)
    return simulateTransaction(b64, sigVerify, replaceRecentBlockhash, commitment)
  }

  // Compatibility overload for solana-kt users passing Transaction object
  suspend fun simulateTransaction(transaction: Transaction, sigVerify: Boolean = false, replaceRecentBlockhash: Boolean = false, commitment: String = "processed"): JsonObject {
    return simulateTransaction(transaction.serialize(), sigVerify, replaceRecentBlockhash, commitment)
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
    val b64 = Base64.getEncoder().encodeToString(txBytes)
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
    return res["result"]!!.jsonPrimitive.content
  }

  suspend fun getSlot(commitment: String = "finalized"): Long {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getSlot", params)["result"]!!.jsonPrimitive.long
  }

  suspend fun getBlockHeight(commitment: String = "finalized"): Long {
    val params = buildJsonArray { add(buildJsonObject { put("commitment", commitment) }) }
    return client.call("getBlockHeight", params)["result"]!!.jsonPrimitive.long
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

  suspend fun getEpochSchedule(): JsonObject {
  return client.call("getEpochSchedule", null).resultObj().jsonObject
}

  suspend fun getFirstAvailableBlock(): Long {
  return client.call("getFirstAvailableBlock", null)["result"]!!.jsonPrimitive.long
}

  suspend fun getGenesisHash(): String {
  return client.call("getGenesisHash", null)["result"]!!.jsonPrimitive.content
}

  suspend fun getIdentity(): String {
  val r = client.call("getIdentity", null).resultObj()
  return r["identity"]!!.jsonPrimitive.content
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
  return client.call("getMinimumBalanceForRentExemption", params)["result"]!!.jsonPrimitive.long
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

  suspend fun requestAirdrop(pubkey: String, lamports: Long, commitment: String = "confirmed"): String {
  val params = buildJsonArray {
    add(JsonPrimitive(pubkey))
    add(JsonPrimitive(lamports))
    add(buildJsonObject { put("commitment", commitment) })
  }
  return client.call("requestAirdrop", params)["result"]!!.jsonPrimitive.content
}

  suspend fun getVersion(): JsonObject {
  return client.call("getVersion", null).resultObj().jsonObject
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
        Base64.getDecoder().decode(b64)
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
    return client.call("getTransactionCount", params)["result"]!!.jsonPrimitive.long
  }

  suspend fun getMinimumLedgerSlot(): Long {
    return client.call("minimumLedgerSlot", null)["result"]!!.jsonPrimitive.long
  }

  suspend fun getMaxRetransmitSlot(): Long {
    return client.call("getMaxRetransmitSlot", null)["result"]!!.jsonPrimitive.long
  }

  suspend fun getMaxShredInsertSlot(): Long {
    return client.call("getMaxShredInsertSlot", null)["result"]!!.jsonPrimitive.long
  }

  suspend fun getSlotCommitment(slot: Long): JsonObject {
    val params = buildJsonArray { add(JsonPrimitive(slot)) }
    return client.call("getSlotCommitment", params).resultObj().jsonObject
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
    return res["value"]!!.jsonPrimitive.boolean
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
        val buffer = java.nio.ByteBuffer.wrap(dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // 1. Check Type (u32) - 1 is LookupTable
        if (buffer.remaining() < 4) return null
        val type = buffer.getInt()
        if (type != 1) return null

        // 2. deactivationSlot (u64)
        if (buffer.remaining() < 8) return null
        val deactivationSlot = buffer.getLong()

        // 3. lastExtendedSlot (u64)
        if (buffer.remaining() < 8) return null
        val lastExtendedSlot = buffer.getLong()

        // 4. lastExtendedSlotStartIndex (u8)
        if (buffer.remaining() < 1) return null
        val lastExtendedSlotStartIndex = buffer.get().toInt() and 0xFF

        // 5. Authority (Option<Pubkey>)
        if (buffer.remaining() < 1) return null
        val hasAuthority = buffer.get().toInt() == 1
        var authority: String? = null
        if (hasAuthority) {
             if (buffer.remaining() < 32) return null
             val authBytes = ByteArray(32)
             buffer.get(authBytes)
             authority = com.selenus.artemis.runtime.Base58.encode(authBytes)
        }

        // 6. Addresses (Vec<Pubkey> -> u64 len + items)
        if (buffer.remaining() < 8) return null
        // Note: Some legacy versions might vary, but assuming current layout
        val len = buffer.getLong().toInt() 

        if (buffer.remaining() < len * 32) return null

        val addresses = ArrayList<String>(len)
        val addrBytes = ByteArray(32)
        for (i in 0 until len) {
            buffer.get(addrBytes)
            addresses.add(com.selenus.artemis.runtime.Base58.encode(addrBytes))
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
