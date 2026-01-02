package com.selenus.artemis.rpc

import kotlin.math.min
import kotlin.random.Random

data class RpcClientConfig(
  val maxAttempts: Int = 3,
  val baseBackoffMs: Long = 150,
  val maxBackoffMs: Long = 2_000,
  val jitterRatio: Double = 0.2,
  val retryOnHttp429: Boolean = true,
  val retryOnHttp5xx: Boolean = true,
  val retryOnTimeout: Boolean = true
)

interface BackoffStrategy {
  fun backoffMs(attempt: Int, config: RpcClientConfig): Long
}

object ExponentialJitterBackoff : BackoffStrategy {
  override fun backoffMs(attempt: Int, config: RpcClientConfig): Long {
    val exp = config.baseBackoffMs * (1L shl maxOf(0, attempt - 1))
    val capped = min(exp, config.maxBackoffMs)
    val jitter = (capped * config.jitterRatio).toLong()
    val delta = if (jitter <= 0) 0L else Random.nextLong(0, jitter + 1)
    return maxOf(0L, capped - delta)
  }
}
