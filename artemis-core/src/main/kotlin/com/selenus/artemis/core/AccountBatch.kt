package com.selenus.artemis.core

/**
 * AccountBatch
 *
 * Utility helpers for common Solana app patterns:
 * - chunked getMultipleAccounts batches
 * - dedupe and preserve order
 *
 * This is intentionally small and portable. It improves reliability on mobile networks.
 */
object AccountBatch {
  fun <T> chunk(list: List<T>, size: Int): List<List<T>> {
    if (size <= 0) throw IllegalArgumentException("size")
    if (list.isEmpty()) return emptyList()
    val out = ArrayList<List<T>>()
    var i = 0
    while (i < list.size) {
      val end = minOf(i + size, list.size)
      out.add(list.subList(i, end))
      i = end
    }
    return out
  }

  fun <T> dedupePreserveOrder(list: List<T>): List<T> {
    val seen = LinkedHashSet<T>()
    for (x in list) seen.add(x)
    return seen.toList()
  }
}
