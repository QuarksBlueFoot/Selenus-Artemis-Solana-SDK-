package com.selenus.artemis.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * RpcFacade
 *
 * Organizes RpcApi into focused surfaces so apps do not scroll through a 500-line file.
 */
class RpcFacade(private val api: RpcApi) {
  val core = Core(api)
  val blocks = Blocks(api)
  val tokens = Tokens(api)
  val stake = Stake(api)

  class Core(private val a: RpcApi) {
    fun getLatestBlockhash(commitment: String = "finalized") = a.getLatestBlockhash(commitment)
    fun getBalance(pubkeyBase58: String, commitment: String = "confirmed") = a.getBalance(pubkeyBase58, commitment)
    fun getAccountInfo(pubkeyBase58: String, commitment: String = "confirmed", encoding: String = "base64") =
      a.getAccountInfo(pubkeyBase58, commitment, encoding)
    fun getMultipleAccounts(pubkeys: List<String>, commitment: String = "confirmed", encoding: String = "base64") =
      a.getMultipleAccounts(pubkeys, commitment, encoding)
    fun sendRawTransaction(txBytes: ByteArray, skipPreflight: Boolean = false, maxRetries: Int? = null) =
      a.sendRawTransaction(txBytes, skipPreflight, maxRetries)
    fun confirmTransaction(signature: String, maxAttempts: Int = 30, sleepMs: Long = 500) =
      a.confirmTransaction(signature, maxAttempts, sleepMs)
    fun callRaw(method: String, params: kotlinx.serialization.json.JsonElement? = null): JsonObject =
      a.callRaw(method, params)
  }

  class Blocks(private val a: RpcApi) {
    fun getSignaturesForAddress(address: String, limit: Int = 1000, before: String? = null, until: String? = null) =
      a.getSignaturesForAddress(address, limit, before, until)
    fun getBlock(slot: Long) = a.getBlock(slot)
    fun getBlocks(startSlot: Long, endSlot: Long? = null): JsonArray = a.getBlocks(startSlot, endSlot)
    fun getBlockTime(slot: Long) = a.getBlockTime(slot)
  }

  class Tokens(private val a: RpcApi) {
    fun getTokenAccountsByOwner(owner: String, mint: String? = null, programId: String? = null) =
      a.getTokenAccountsByOwner(owner, mint, programId)
    fun getTokenAccountsByOwnerBase64(owner: String, mint: String? = null, programId: String? = null) =
      a.getTokenAccountsByOwnerBase64(owner, mint, programId)
    fun getTokenAccountBalance(account: String) = a.getTokenAccountBalance(account)
    fun getTokenSupply(mint: String) = a.getTokenSupply(mint)
    fun getTokenLargestAccounts(mint: String) = a.getTokenLargestAccounts(mint)
  }

  class Stake(private val a: RpcApi) {
    fun getStakeActivation(stakeAccount: String, epoch: Long? = null) = a.getStakeActivation(stakeAccount, epoch)
    fun getVoteAccounts() = a.getVoteAccounts()
  }
}
