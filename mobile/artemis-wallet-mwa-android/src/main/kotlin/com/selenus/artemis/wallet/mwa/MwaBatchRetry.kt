package com.selenus.artemis.wallet.mwa

/**
 * MwaBatchRetry
 *
 * Simple retry helper for wallet batch operations.
 * This is intentionally small and predictable. RPC already handles retry separately.
 */
object MwaBatchRetry {

  suspend fun <T> withRetry(
    maxAttempts: Int = 2,
    block: suspend () -> T
  ): T {
    var attempt = 1
    var last: Throwable? = null
    while (attempt <= maxAttempts) {
      try {
        return block()
      } catch (t: Throwable) {
        last = t
        if (attempt >= maxAttempts) break
        attempt += 1
      }
    }
    throw (last ?: IllegalStateException("mwa_retry_failed"))
  }
}
