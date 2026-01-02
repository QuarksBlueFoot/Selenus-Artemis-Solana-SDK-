package com.selenus.artemis.ws

/**
 * Handle returned by subscribe calls.
 *
 * Call close to unsubscribe and remove from resubscribe registry.
 */
class SubscriptionHandle internal constructor(
  internal val key: String,
  private val closeFn: suspend (SubscriptionHandle) -> Unit
) {
  suspend fun close() = closeFn(this)
}
