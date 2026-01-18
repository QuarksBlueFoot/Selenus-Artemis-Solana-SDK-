package com.selenus.artemis.gaming

import kotlinx.coroutines.CoroutineScope
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * PriorityFeeOracle
 *
 * Adaptive advisor for compute unit price (microLamports).
 *
 * v22 upgrades:
 * - rolling window samples per (programId, tier)
 * - fast bump on failures/timeouts
 * - slow decay when healthy
 * - separate keys per cluster label (optional)
 *
 * This oracle is transport-agnostic. You feed it outcomes from your send pipeline.
 */
class PriorityFeeOracle(
  private val scope: CoroutineScope,
  private val baseMicroLamports: Int = 100,
  private val minMicroLamports: Int = 50,
  private val maxMicroLamports: Int = 20_000,
  private val windowSize: Int = 40
) {

  data class Key(
    val cluster: String,
    val programId: String,
    val tier: ComputeBudgetPresets.Tier
  )

  enum class Outcome {
    CONFIRMED,
    TIMEOUT,
    DROPPED
  }

  private data class Sample(val latencyMs: Long, val outcome: Outcome, val price: Int)

  private data class State(
    var suggested: Int,
    val samples: ArrayDeque<Sample> = ArrayDeque()
  )

  private val states = ConcurrentHashMap<Key, State>()

  fun recordOutcome(
    programId: String,
    tier: ComputeBudgetPresets.Tier,
    latencyMs: Long,
    outcome: Outcome,
    usedMicroLamports: Int,
    cluster: String = "default"
  ) {
    val k = Key(cluster, programId, tier)
    val st = states.getOrPut(k) { State(suggested = baseMicroLamports) }

    if (st.samples.size >= windowSize) st.samples.removeFirst()
    st.samples.addLast(Sample(latencyMs, outcome, usedMicroLamports))

    st.suggested = computeSuggestion(st, tier)
  }

  fun suggest(programId: String, tier: ComputeBudgetPresets.Tier, cluster: String = "default"): Int {
    val st = states[Key(cluster, programId, tier)] ?: return baseMicroLamports
    return st.suggested
  }

  private fun computeSuggestion(st: State, tier: ComputeBudgetPresets.Tier): Int {
    if (st.samples.isEmpty()) return baseMicroLamports

    var ok = 0
    var bad = 0
    val latencies = ArrayList<Long>(st.samples.size)
    var lastUsed = baseMicroLamports

    for (s in st.samples) {
      lastUsed = s.price
      when (s.outcome) {
        Outcome.CONFIRMED -> ok++
        Outcome.TIMEOUT, Outcome.DROPPED -> bad++
      }
      latencies.add(s.latencyMs)
    }

    latencies.sort()
    val p80 = latencies[(latencies.size - 1) * 80 / 100].toDouble()
    val health = ok.toDouble() / max(1, ok + bad).toDouble()

    // baseline scales by tier
    val tierBase = when (tier) {
      ComputeBudgetPresets.Tier.ARCADE -> baseMicroLamports
      ComputeBudgetPresets.Tier.COMPETITIVE -> (baseMicroLamports * 1.8).toInt()
      ComputeBudgetPresets.Tier.BOSS_FIGHT -> (baseMicroLamports * 3.0).toInt()
    }

    // if p80 is high or health is low, bump aggressively
    val latencyFactor = (p80 / 800.0).coerceIn(0.5, 6.0)
    val healthPenalty = (1.0 - health).coerceIn(0.0, 1.0)

    var target = (tierBase * latencyFactor * (1.0 + 3.0 * healthPenalty)).toInt()

    // fast bump if the most recent sample was a failure
    val last = st.samples.last()
    if (last.outcome != Outcome.CONFIRMED) {
      target = max(target, (lastUsed * 1.35).toInt())
    } else {
      // slow decay when healthy
      target = min(target, max(minMicroLamports, (st.suggested * 0.95).toInt()))
    }

    target = min(maxMicroLamports, max(minMicroLamports, target))

    // smooth changes, but allow bump faster than decay
    val alpha = if (target > st.suggested) 0.35 else 0.15
    return (st.suggested.toDouble() * (1.0 - alpha) + target.toDouble() * alpha).toInt()
  }
}
