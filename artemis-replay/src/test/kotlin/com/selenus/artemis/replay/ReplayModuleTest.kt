package com.selenus.artemis.replay

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for artemis-replay module.
 * Tests ReplayRecorder, ReplayPlayer, and replay models.
 */
class ReplayModuleTest {

    private val json = Json { prettyPrint = true }

    // ===== RecordedFrame Tests =====

    @Test
    fun testRecordedFrameCreation() {
        val frame = RecordedFrame(
            createdAtMs = System.currentTimeMillis(),
            instructionCount = 3
        )
        
        assertTrue(frame.createdAtMs > 0)
        assertEquals(3, frame.instructionCount)
        assertEquals(null, frame.signature)
        assertEquals(null, frame.recentBlockhash)
        assertTrue(frame.meta.isEmpty())
    }

    @Test
    fun testRecordedFrameWithSignature() {
        val frame = RecordedFrame(
            createdAtMs = 1234567890000L,
            instructionCount = 2,
            signature = "abc123def456",
            recentBlockhash = "blockhash123"
        )
        
        assertEquals("abc123def456", frame.signature)
        assertEquals("blockhash123", frame.recentBlockhash)
    }

    @Test
    fun testRecordedFrameWithMeta() {
        val meta = mapOf(
            "action" to "attack",
            "target" to "enemy-1",
            "damage" to "50"
        )
        
        val frame = RecordedFrame(
            createdAtMs = System.currentTimeMillis(),
            instructionCount = 1,
            meta = meta
        )
        
        assertEquals(3, frame.meta.size)
        assertEquals("attack", frame.meta["action"])
    }

    // ===== ReplaySession Tests =====

    @Test
    fun testReplaySessionCreation() {
        val frames = listOf(
            RecordedFrame(1000L, 1),
            RecordedFrame(2000L, 2),
            RecordedFrame(3000L, 1)
        )
        
        val session = ReplaySession(version = 1, frames = frames)
        
        assertEquals(1, session.version)
        assertEquals(3, session.frames.size)
    }

    @Test
    fun testReplaySessionDefaultVersion() {
        val session = ReplaySession(frames = emptyList())
        
        assertEquals(1, session.version)
    }

    @Test
    fun testReplaySessionEmpty() {
        val session = ReplaySession(frames = emptyList())
        
        assertTrue(session.frames.isEmpty())
    }

    // ===== ReplayRecorder Tests =====

    @Test
    fun testReplayRecorderCreation() {
        val recorder = ReplayRecorder()
        
        assertNotNull(recorder)
    }

    @Test
    fun testReplayRecorderWithCustomJson() {
        val customJson = Json { prettyPrint = false }
        val recorder = ReplayRecorder(customJson)
        
        assertNotNull(recorder)
    }

    @Test
    fun testReplayRecorderRecordFrame() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        val instructions = listOf(
            Instruction(programId, emptyList(), byteArrayOf())
        )
        
        recorder.recordFrame(System.currentTimeMillis(), instructions)
        
        val session = recorder.snapshot()
        assertEquals(1, session.frames.size)
        assertEquals(1, session.frames[0].instructionCount)
    }

    @Test
    fun testReplayRecorderMultipleFrames() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        
        repeat(5) { i ->
            val instructions = (0..i).map {
                Instruction(programId, emptyList(), byteArrayOf())
            }
            recorder.recordFrame(System.currentTimeMillis() + i * 100, instructions)
        }
        
        val session = recorder.snapshot()
        
        assertEquals(5, session.frames.size)
        assertEquals(1, session.frames[0].instructionCount)
        assertEquals(5, session.frames[4].instructionCount)
    }

    @Test
    fun testReplayRecorderRecordWithMeta() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        val instructions = listOf(
            Instruction(programId, emptyList(), byteArrayOf())
        )
        val meta = mapOf("action" to "move", "direction" to "north")
        
        recorder.recordFrame(System.currentTimeMillis(), instructions, meta)
        
        val session = recorder.snapshot()
        assertEquals("move", session.frames[0].meta["action"])
    }

    @Test
    fun testReplayRecorderAttachSignature() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        val instructions = listOf(
            Instruction(programId, emptyList(), byteArrayOf())
        )
        
        recorder.recordFrame(System.currentTimeMillis(), instructions)
        recorder.attachSignature(0, "sig123", "blockhash456")
        
        val session = recorder.snapshot()
        assertEquals("sig123", session.frames[0].signature)
        assertEquals("blockhash456", session.frames[0].recentBlockhash)
    }

    @Test
    fun testReplayRecorderSnapshot() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        
        recorder.recordFrame(1000L, listOf(Instruction(programId, emptyList(), byteArrayOf())))
        recorder.recordFrame(2000L, listOf(Instruction(programId, emptyList(), byteArrayOf())))
        
        val snapshot = recorder.snapshot()
        
        assertEquals(2, snapshot.frames.size)
        assertEquals(1, snapshot.version)
    }

    // ===== ReplayPlayer Tests =====

    @Test
    fun testReplayPlayerCreation() {
        val player = ReplayPlayer()
        assertNotNull(player)
    }

    // ===== Serialization Tests =====

    @Test
    fun testRecordedFrameSerialization() {
        val frame = RecordedFrame(
            createdAtMs = 1234567890L,
            instructionCount = 2,
            signature = "sig",
            recentBlockhash = "hash",
            meta = mapOf("key" to "value")
        )
        
        val serialized = json.encodeToString(RecordedFrame.serializer(), frame)
        val deserialized = json.decodeFromString(RecordedFrame.serializer(), serialized)
        
        assertEquals(frame.createdAtMs, deserialized.createdAtMs)
        assertEquals(frame.instructionCount, deserialized.instructionCount)
        assertEquals(frame.signature, deserialized.signature)
    }

    @Test
    fun testReplaySessionSerialization() {
        val session = ReplaySession(
            version = 1,
            frames = listOf(
                RecordedFrame(1000L, 1),
                RecordedFrame(2000L, 2)
            )
        )
        
        val serialized = json.encodeToString(ReplaySession.serializer(), session)
        val deserialized = json.decodeFromString(ReplaySession.serializer(), serialized)
        
        assertEquals(session.version, deserialized.version)
        assertEquals(session.frames.size, deserialized.frames.size)
    }

    // ===== Timing Tests =====

    @Test
    fun testFrameTimingOrder() {
        val recorder = ReplayRecorder()
        val programId = Pubkey(ByteArray(32))
        val instructions = listOf(Instruction(programId, emptyList(), byteArrayOf()))
        
        val times = listOf(1000L, 2000L, 3000L, 4000L)
        times.forEach { recorder.recordFrame(it, instructions) }
        
        val session = recorder.snapshot()
        
        for (i in 0 until session.frames.size - 1) {
            assertTrue(session.frames[i].createdAtMs < session.frames[i + 1].createdAtMs)
        }
    }
}
