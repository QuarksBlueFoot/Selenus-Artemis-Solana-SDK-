package com.selenus.artemis.errors

import com.selenus.artemis.logging.Log

object ErrorMappers {

  private val log = Log.get("ErrorMappers")

  fun mapRpc(
    t: Throwable,
    logs: List<String> = emptyList(),
    rpcCode: Int? = null,
    data: String? = null
  ): ArtemisError {
    val decoded = SolanaErrorDecoder.decodeThrowable(t, logs = logs, rpcCode = rpcCode, data = data)
    return when (decoded.category) {
      ArtemisErrorCategory.RATE_LIMIT -> ArtemisError.RateLimited(t)
      ArtemisErrorCategory.NETWORK -> ArtemisError.NetworkTimeout(t)
      ArtemisErrorCategory.NODE_HEALTH -> ArtemisError.NodeUnhealthy(t)
      ArtemisErrorCategory.BLOCKHASH -> {
        val msg = decoded.message.lowercase()
        if (msg.contains("not found")) ArtemisError.BlockhashNotFound(t) else ArtemisError.BlockhashExpired(t)
      }
      ArtemisErrorCategory.FUNDS -> ArtemisError.InsufficientFunds(t)
      ArtemisErrorCategory.PROGRAM -> ArtemisError.ProgramError(decoded, t)
      ArtemisErrorCategory.SIMULATION -> ArtemisError.SimulationFailed(decoded.logs, t, decoded)
      ArtemisErrorCategory.TRANSACTION -> ArtemisError.TransactionRejected(t)
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
