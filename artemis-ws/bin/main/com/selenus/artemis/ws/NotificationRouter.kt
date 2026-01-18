package com.selenus.artemis.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.JsonElement

/**
 * Routes raw notifications into WsEvent.Notification with sampling and backpressure awareness.
 */
internal class NotificationRouter(
  private val scope: CoroutineScope,
  private val emit: suspend (WsEvent) -> Unit,
  private val policy: () -> NotificationPolicy
) {

  data class RawNotif(
    val key: String?,
    val subscriptionId: Long,
    val method: String,
    val result: JsonElement?
  )

  private val ch = Channel<RawNotif>(capacity = Channel.BUFFERED)
  private var job: Job? = null

  // latest per key for sampling
  private val latest = LinkedHashMap<String, RawNotif>()

  // backpressure counters per key
  private var droppedTotal = 0
  private val droppedByKey = LinkedHashMap<String, Int>()

  @Suppress("DEPRECATION")
  @OptIn(ExperimentalCoroutinesApi::class)
  fun start() {
    if (job?.isActive == true) return
    job = scope.launch {
      while (true) {
        val p = policy()
        // flush sampled latest values every sampleWindow
        val sampleDelay = p.sampleWindowMs

        select<Unit> {
          ch.onReceive { n ->
            val key = n.key
            if (key != null && !p.isCritical(key)) {
              // sample: keep only latest
              latest[key] = n
            } else {
              emit(WsEvent.Notification(key, n.subscriptionId, n.method, n.result, isSampled = false))
            }

            // If channel buffer is pressured, we will detect via trySend failures upstream.
          }

          // periodic flush for sampled keys
          onTimeout(sampleDelay) {
            if (latest.isNotEmpty()) {
              val snap = ArrayList(latest.values)
              latest.clear()
              for (n in snap) {
                emit(WsEvent.Notification(n.key, n.subscriptionId, n.method, n.result, isSampled = true))
              }
            }
          }
        }

        // emit backpressure summary periodically
        val bpWindow = p.backpressureWindowMs
        if (bpWindow > 0) {
          delay(0) // allow cooperative
          // backpressure events are emitted by parent when drops happen, but we also emit a heartbeat summary if needed
          if (droppedTotal > 0) {
            // best-effort aggregate, unknown key
            emit(WsEvent.Backpressure(null, droppedTotal, bpWindow))
            droppedTotal = 0
            droppedByKey.clear()
          }
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
    latest.clear()
    droppedTotal = 0
    droppedByKey.clear()
  }

  fun tryOffer(n: RawNotif): Boolean = ch.trySend(n).isSuccess

  fun noteDrop(key: String?) {
    droppedTotal += 1
    if (key != null) droppedByKey[key] = (droppedByKey[key] ?: 0) + 1
  }
}
