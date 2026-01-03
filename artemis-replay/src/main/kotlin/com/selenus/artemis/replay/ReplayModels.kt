package com.selenus.artemis.replay

import kotlinx.serialization.Serializable

@Serializable
data class RecordedFrame(
  val createdAtMs: Long,
  val instructionCount: Int,
  val signature: String? = null,
  val recentBlockhash: String? = null,
  val meta: Map<String, String> = emptyMap()
)

@Serializable
data class ReplaySession(
  val version: Int = 1,
  val frames: List<RecordedFrame>
)
