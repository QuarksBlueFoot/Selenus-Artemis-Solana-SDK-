package com.selenus.artemis.ws

/**
 * Controls how notifications are emitted when the consumer is slower than the producer.
 *
 * Default behavior:
 * - critical keys are emitted best-effort
 * - non critical keys can be sampled to the latest value within a window
 */
data class NotificationPolicy(
  val sampleWindowMs: Long = 250,
  val backpressureWindowMs: Long = 1_000,
  val criticalKeyPrefixes: Set<String> = setOf("sig:", "acct:"),
  val maxPendingNotifications: Int = 512
) {
  fun isCritical(key: String?): Boolean {
    if (key == null) return false
    return criticalKeyPrefixes.any { key.startsWith(it) }
  }
}
