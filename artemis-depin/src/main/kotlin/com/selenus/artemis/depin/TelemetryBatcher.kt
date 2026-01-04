package com.selenus.artemis.depin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * TelemetryBatcher
 *
 * A high-throughput batcher for DePIN sensor data.
 * Unlike gaming action batchers, this optimizes for compression and
 * delayed transmission (store-and-forward) rather than low latency.
 */
class TelemetryBatcher(
  private val scope: CoroutineScope,
  private val batchSize: Int = 50,
  private val flushIntervalMs: Long = 10_000
) {

  data class TelemetryPacket(
    val id: Int,
    val timestamp: Long,
    val data: Map<String, Any>
  )

  private val queue = Channel<TelemetryPacket>(Channel.UNLIMITED)
  private val _batches = MutableSharedFlow<List<TelemetryPacket>>()
  val batches: SharedFlow<List<TelemetryPacket>> = _batches

  private val packetId = AtomicInteger(0)

  fun start() {
    scope.launch {
      val buffer = ArrayList<TelemetryPacket>()
      while (true) {
        // Wait for flush interval or full batch
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < flushIntervalMs && buffer.size < batchSize) {
            val result = queue.tryReceive()
            if (result.isSuccess) {
                buffer.add(result.getOrThrow())
            } else {
                delay(100)
            }
        }

        if (buffer.isNotEmpty()) {
          _batches.emit(ArrayList(buffer))
          buffer.clear()
        }
      }
    }
  }

  fun submit(data: Map<String, Any>) {
    val packet = TelemetryPacket(
      id = packetId.getAndIncrement(),
      timestamp = System.currentTimeMillis(),
      data = data
    )
    queue.trySend(packet)
  }
}
