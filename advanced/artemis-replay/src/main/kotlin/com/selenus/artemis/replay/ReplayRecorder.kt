package com.selenus.artemis.replay

import com.selenus.artemis.tx.Instruction
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deterministic Replay Recorder
 *
 * Records frame metadata and optionally signatures so teams can:
 * - reproduce bugs
 * - debug desync
 * - build anti cheat telemetry
 *
 * It intentionally records minimal information by default.
 * Apps can attach more metadata through meta fields.
 */
class ReplayRecorder(
  private val json: Json = Json { prettyPrint = true }
) {
  private val frames = CopyOnWriteArrayList<RecordedFrame>()

  fun recordFrame(createdAtMs: Long, instructions: List<Instruction>, meta: Map<String, String> = emptyMap()) {
    frames.add(
      RecordedFrame(
        createdAtMs = createdAtMs,
        instructionCount = instructions.size,
        meta = meta
      )
    )
  }

  fun attachSignature(index: Int, signature: String, recentBlockhash: String?) {
    val current = frames[index]
    frames[index] = current.copy(signature = signature, recentBlockhash = recentBlockhash)
  }

  fun snapshot(): ReplaySession = ReplaySession(frames = frames.toList())

  fun writeTo(file: File) {
    file.writeText(json.encodeToString(ReplaySession.serializer(), snapshot()))
  }
}
