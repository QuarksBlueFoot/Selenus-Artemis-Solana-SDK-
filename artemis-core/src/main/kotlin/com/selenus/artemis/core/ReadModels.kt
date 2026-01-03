package com.selenus.artemis.core

object ReadModels {

  interface Rpc {
    suspend fun getRecentPerformanceSamples(): List<Long>
  }

  interface BalanceRpc {
    suspend fun getBalance(lamportsOwner: String): Long
    suspend fun getLatestBlockhash(): String
  }

  data class WalletOverview(
    val owner: String,
    val balanceLamports: Long,
    val latestBlockhash: String
  )

  suspend fun walletOverview(rpc: BalanceRpc, owner: String): WalletOverview {
    val bal = rpc.getBalance(owner)
    val bh = rpc.getLatestBlockhash()
    return WalletOverview(owner = owner, balanceLamports = bal, latestBlockhash = bh)
  }

  data class TokenAccountSummary(
    val owner: String,
    val mint: String,
    val amountRaw: String,
    val decimals: Int
  )

  interface TokenRpc {
    suspend fun getTokenAccountsByOwner(owner: String): List<TokenAccountSummary>
  }

  data class NftSummary(
    val owner: String,
    val mint: String,
    val name: String?,
    val collection: String?,
    val isCompressed: Boolean
  )

  interface NftRpc {
    suspend fun getNftsByOwner(owner: String): List<NftSummary>
  }

  data class GameReadModel(
    val programId: String,
    val slot: Long,
    val accounts: Int
  )

  interface ProgramRpc {
    suspend fun getProgramReadModel(programId: String): GameReadModel
  }
}
