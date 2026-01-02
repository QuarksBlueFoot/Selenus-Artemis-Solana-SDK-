package com.selenus.artemis.errors

import com.selenus.artemis.logging.Log

object ErrorMappers {

  private val log = Log.get("ErrorMappers")

  fun mapRpc(t: Throwable): ArtemisError {
    val msg = (t.message ?: "").lowercase()
    return when {
      msg.contains("429") || msg.contains("rate limit") -> ArtemisError.RateLimited(t)
      msg.contains("timeout") -> ArtemisError.NetworkTimeout(t)
      msg.contains("unhealthy") || msg.contains("gethealth") -> ArtemisError.NodeUnhealthy(t)
      msg.contains("blockhash not found") -> ArtemisError.BlockhashNotFound(t)
      msg.contains("blockhash") && msg.contains("expired") -> ArtemisError.BlockhashExpired(t)
      msg.contains("insufficient funds") -> ArtemisError.InsufficientFunds(t)
      msg.contains("transaction was not confirmed") || msg.contains("rejected") -> ArtemisError.TransactionRejected(t)
      else -> {
        log.warn("unmapped rpc error: ${t.message}")
        ArtemisError.Unknown(t)
      }
    }
  }

  fun mapWallet(t: Throwable): ArtemisError {
    val msg = (t.message ?: "").lowercase()
    return when {
      msg.contains("user") && (msg.contains("reject") || msg.contains("denied") || msg.contains("cancel")) ->
        ArtemisError.UserRejected(t)
      msg.contains("wallet") && msg.contains("unavailable") -> ArtemisError.WalletUnavailable(t)
      else -> ArtemisError.Unknown(t)
    }
  }
}
