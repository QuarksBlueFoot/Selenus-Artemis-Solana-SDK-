package com.selenus.artemis.gaming

import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * ArcanaFlow
 *
 * A mobile-first "action lane" for Solana games.
 *
 * Idea:
 * - You submit game actions locally as intent objects (instructions).
 * - The lane batches actions into a short frame window.
 * - It emits a deterministic frame bundle for signing and sending.
 * - If network is slow, the game still progresses locally, and resync happens via snapshots.
 *
 * This is not an L2, not a mixer, and not a custody trick.
 * It is a practical batching and resync primitive tuned for games.
 */
class ArcanaFlow(
  private val scope: CoroutineScope,
  private val frameWindowMs: Long = 60
) {

  data class Frame(
    val createdAtMs: Long,
    val instructions: List<Instruction>
  )

  private val q = ArrayDeque<Instruction>()
  private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 32)
  val frames: SharedFlow<Frame> = _frames

  private var job: Job? = null

  fun start() {
    if (job?.isActive == true) return
    job = scope.launch {
      while (true) {
        delay(frameWindowMs)
        val batch = ArrayList<Instruction>()
        synchronized(q) {
          while (q.isNotEmpty()) batch.add(q.removeFirst())
        }
        if (batch.isNotEmpty()) {
          _frames.tryEmit(Frame(System.currentTimeMillis(), batch))
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  fun enqueue(ix: Instruction) {
    synchronized(q) { q.addLast(ix) }
  }
}
