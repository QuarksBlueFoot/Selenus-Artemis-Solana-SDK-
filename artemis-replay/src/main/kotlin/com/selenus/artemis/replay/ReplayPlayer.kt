package com.selenus.artemis.replay

import kotlinx.serialization.json.Json
import java.io.File

/**
 * ReplayPlayer loads recorded sessions. Execution is app-specific.
 * This module focuses on deterministic playback ordering and metadata access.
 */
class ReplayPlayer(
  private val json: Json = Json { ignoreUnknownKeys = true }
) {
  fun load(file: File): ReplaySession {
    return json.decodeFromString(ReplaySession.serializer(), file.readText())
  }
}
