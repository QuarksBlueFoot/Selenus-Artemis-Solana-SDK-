package com.selenus.artemis.errors

sealed class ArtemisError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
  class NetworkTimeout(cause: Throwable? = null) : ArtemisError("network_timeout", cause)
  class NetworkUnavailable(cause: Throwable? = null) : ArtemisError("network_unavailable", cause)
  class RateLimited(cause: Throwable? = null) : ArtemisError("rate_limited", cause)
  class NodeUnhealthy(cause: Throwable? = null) : ArtemisError("node_unhealthy", cause)

  class BlockhashExpired(cause: Throwable? = null) : ArtemisError("blockhash_expired", cause)
  class BlockhashNotFound(cause: Throwable? = null) : ArtemisError("blockhash_not_found", cause)

  class SimulationFailed(val logs: List<String> = emptyList(), cause: Throwable? = null) :
    ArtemisError("simulation_failed", cause)

  class TransactionRejected(cause: Throwable? = null) : ArtemisError("transaction_rejected", cause)
  class InsufficientFunds(cause: Throwable? = null) : ArtemisError("insufficient_funds", cause)

  class UserRejected(cause: Throwable? = null) : ArtemisError("user_rejected", cause)
  class WalletUnavailable(cause: Throwable? = null) : ArtemisError("wallet_unavailable", cause)

  class Unknown(cause: Throwable? = null) : ArtemisError("unknown_error", cause)
}
