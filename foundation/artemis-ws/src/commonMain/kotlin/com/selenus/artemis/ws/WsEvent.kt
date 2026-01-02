package com.selenus.artemis.ws

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class WsEvent {
  data object Connected : WsEvent()
  data class Disconnected(val reason: String) : WsEvent()
  data class Reconnecting(val attempt: Int, val inMs: Long) : WsEvent()
  data object GaveUp : WsEvent()
  data class Heartbeat(val atMs: Long) : WsEvent()

  data class Subscribed(val key: String, val subscriptionId: Long) : WsEvent()

  /**
   * Normal notification. Result schema depends on the subscription method.
   */
  data class Notification(
    val key: String?,
    val subscriptionId: Long,
    val method: String,
    val result: JsonElement?,
    val isSampled: Boolean = false
  ) : WsEvent()

  /**
   * Emitted when the client detects notification pressure. This helps games and wallets
   * switch UI modes or relax noisy subscriptions.
   */
  data class Backpressure(
    val key: String?,
    val dropped: Int,
    val windowMs: Long
  ) : WsEvent()

  data class Error(val message: String, val payload: JsonObject) : WsEvent()
  data class Raw(val text: String) : WsEvent()
}
