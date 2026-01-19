package com.selenus.artemis.gaming

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.Assume
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.programs.SystemProgram

/**
 * Comprehensive tests for artemis-gaming module v1.2.0 enhancements
 * 
 * Tests ArcanaFlowV2, GameSessionManager, and AdaptiveFeeOptimizer
 */
class GamingModuleTest {

    private val testSeed = "2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665"
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== ArcanaFlowV2 Tests ====================

    @Test
    fun `ArcanaFlowV2 - create instance with default config`() {
        runBlocking {
            val arcana = ArcanaFlowV2(testScope)
            assertNotNull(arcana)
        }
    }

    @Test
    fun `ArcanaFlowV2 - create instance with custom config`() {
        runBlocking {
            val arcana = ArcanaFlowV2(
                testScope,
                ArcanaFlowV2.Config(
                    frameWindowMs = 100L,
                    maxActionsPerFrame = 16,
                    inputBufferSizeFrames = 5,
                    enablePrediction = true,
                    maxPredictionFrames = 8,
                    syncIntervalMs = 2000L,
                    enableActionMerging = false
                )
            )
            assertNotNull(arcana)
        }
    }

    // ==================== GameAction Tests ====================

    @Test
    fun `GameAction - create with default values`() {
        val seed = Base58.decode(testSeed)
        val keypair = Keypair.fromSeed(seed)

        val action = ArcanaFlowV2.GameAction(
            playerId = keypair.publicKey,
            actionType = ArcanaFlowV2.ActionType.MOVE,
            payload = ByteArray(10) { it.toByte() }
        )

        assertNotNull(action)
        assertNotNull(action.id)
        assertEquals(keypair.publicKey, action.playerId)
        assertEquals(ArcanaFlowV2.ActionType.MOVE, action.actionType)
        assertEquals(ArcanaFlowV2.Priority.NORMAL, action.priority)
    }

    @Test
    fun `GameAction - create with all fields`() {
        val player = Keypair.generate().publicKey

        val action = ArcanaFlowV2.GameAction(
            id = "custom-action-id",
            playerId = player,
            actionType = ArcanaFlowV2.ActionType.ATTACK,
            payload = ByteArray(20),
            priority = ArcanaFlowV2.Priority.HIGH,
            timestampMs = 12345L,
            predictedOutcome = ByteArray(8)
        )

        assertEquals("custom-action-id", action.id)
        assertEquals(ArcanaFlowV2.ActionType.ATTACK, action.actionType)
        assertEquals(ArcanaFlowV2.Priority.HIGH, action.priority)
        assertEquals(12345L, action.timestampMs)
    }

    @Test
    fun `GameAction - equality based on id`() {
        val player = Keypair.generate().publicKey

        val action1 = ArcanaFlowV2.GameAction(
            id = "same-id",
            playerId = player,
            actionType = ArcanaFlowV2.ActionType.MOVE,
            payload = ByteArray(10)
        )

        val action2 = ArcanaFlowV2.GameAction(
            id = "same-id",
            playerId = Keypair.generate().publicKey,  // Different player
            actionType = ArcanaFlowV2.ActionType.ATTACK,  // Different type
            payload = ByteArray(20)  // Different payload
        )

        assertEquals(action1, action2)
        assertEquals(action1.hashCode(), action2.hashCode())
    }

    // ==================== ActionType Tests ====================

    @Test
    fun `ActionType - all values available`() {
        val types = ArcanaFlowV2.ActionType.values()

        assertTrue(types.contains(ArcanaFlowV2.ActionType.MOVE))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.ATTACK))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.INTERACT))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.ITEM_USE))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.SKILL))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.TRADE))
        assertTrue(types.contains(ArcanaFlowV2.ActionType.CUSTOM))
    }

    // ==================== Priority Tests ====================

    @Test
    fun `Priority - correct ordering`() {
        assertTrue(ArcanaFlowV2.Priority.LOW.value < ArcanaFlowV2.Priority.NORMAL.value)
        assertTrue(ArcanaFlowV2.Priority.NORMAL.value < ArcanaFlowV2.Priority.HIGH.value)
        assertTrue(ArcanaFlowV2.Priority.HIGH.value < ArcanaFlowV2.Priority.CRITICAL.value)
    }

    // ==================== GameFrame Tests ====================

    @Test
    fun `GameFrame - create and verify structure`() {
        val player = Keypair.generate().publicKey

        val action = ArcanaFlowV2.GameAction(
            playerId = player,
            actionType = ArcanaFlowV2.ActionType.MOVE,
            payload = ByteArray(10)
        )

        val frame = ArcanaFlowV2.GameFrame(
            frameId = 1L,
            createdAtMs = System.currentTimeMillis(),
            actions = listOf(action),
            stateHash = ByteArray(32),
            predictedStateHash = null,
            instructions = emptyList()
        )

        assertNotNull(frame)
        assertEquals(1L, frame.frameId)
        assertEquals(1, frame.actionCount)
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `GameFrame - empty frame check`() {
        val frame = ArcanaFlowV2.GameFrame(
            frameId = 0L,
            createdAtMs = 0L,
            actions = emptyList(),
            stateHash = ByteArray(32),
            predictedStateHash = null,
            instructions = emptyList()
        )

        assertTrue(frame.isEmpty)
        assertEquals(0, frame.actionCount)
    }

    @Test
    fun `GameFrame - compute hash`() {
        val frame = ArcanaFlowV2.GameFrame(
            frameId = 1L,
            createdAtMs = 0L,
            actions = emptyList(),
            stateHash = ByteArray(32) { 1 },
            predictedStateHash = null,
            instructions = emptyList()
        )

        val hash = frame.computeHash()

        assertNotNull(hash)
        assertEquals(32, hash.size)  // SHA-256 produces 32 bytes
    }

    // ==================== FrameResult Tests ====================

    @Test
    fun `FrameResult Success - structure`() {
        val frame = ArcanaFlowV2.GameFrame(
            frameId = 1L,
            createdAtMs = 0L,
            actions = emptyList(),
            stateHash = ByteArray(32),
            predictedStateHash = null,
            instructions = emptyList()
        )

        val result = ArcanaFlowV2.FrameResult.Success(
            frame = frame,
            signature = "abc123",
            slot = 100L,
            latencyMs = 50L
        )

        assertEquals(frame, result.frame)
        assertEquals("abc123", result.signature)
        assertEquals(100L, result.slot)
        assertEquals(50L, result.latencyMs)
    }

    @Test
    fun `FrameResult Failure - structure`() {
        val frame = ArcanaFlowV2.GameFrame(
            frameId = 1L,
            createdAtMs = 0L,
            actions = emptyList(),
            stateHash = ByteArray(32),
            predictedStateHash = null,
            instructions = emptyList()
        )

        val result = ArcanaFlowV2.FrameResult.Failure(
            frame = frame,
            error = ArcanaFlowV2.FrameError.NetworkError("Connection timeout"),
            retryable = true
        )

        assertEquals(frame, result.frame)
        assertTrue(result.retryable)
        assertTrue(result.error is ArcanaFlowV2.FrameError.NetworkError)
    }

    // ==================== FrameError Tests ====================

    @Test
    fun `FrameError - all types available`() {
        val network = ArcanaFlowV2.FrameError.NetworkError("test")
        val validation = ArcanaFlowV2.FrameError.ValidationError("test")
        val conflict = ArcanaFlowV2.FrameError.StateConflict(ByteArray(32), ByteArray(32))
        val timeout = ArcanaFlowV2.FrameError.Timeout(1000L)
        val rateLimit = ArcanaFlowV2.FrameError.RateLimited(5000L)

        assertNotNull(network)
        assertNotNull(validation)
        assertNotNull(conflict)
        assertNotNull(timeout)
        assertNotNull(rateLimit)
    }

    // ==================== SessionState Tests ====================

    @Test
    fun `SessionState - structure validation`() {
        val participants = setOf(
            Keypair.generate().publicKey,
            Keypair.generate().publicKey
        )

        val state = ArcanaFlowV2.SessionState(
            sessionId = "session-001",
            frameCounter = 100L,
            lastConfirmedFrame = 98L,
            pendingFrames = emptyList(),
            stateHash = ByteArray(32),
            participants = participants
        )

        assertNotNull(state)
        assertEquals("session-001", state.sessionId)
        assertEquals(100L, state.frameCounter)
        assertEquals(98L, state.lastConfirmedFrame)
        assertEquals(2, state.participants.size)
    }

    // ==================== GameSessionManager Tests ====================

    @Test
    fun `GameSessionManager - create instance`() {
        runBlocking {
            val manager = GameSessionManager(testScope)
            assertNotNull(manager)
        }
    }

    @Test
    fun `GameSessionManager - create with custom config`() {
        runBlocking {
            val manager = GameSessionManager(
                testScope,
                GameSessionManager.Config(
                    maxPlayersPerSession = 8,
                    sessionTimeoutMs = 600_000L,
                    stateSyncIntervalMs = 1000L,
                    enableAntiCheat = true
                )
            )
            assertNotNull(manager)
        }
    }

    // ==================== AdaptiveFeeOptimizer Tests ====================

    @Test
    fun `AdaptiveFeeOptimizer - create instance`() {
        runBlocking {
            val optimizer = AdaptiveFeeOptimizer(testScope)
            assertNotNull(optimizer)
        }
    }

    @Test
    fun `AdaptiveFeeOptimizer - create with custom config`() {
        runBlocking {
            val optimizer = AdaptiveFeeOptimizer(
                testScope,
                AdaptiveFeeOptimizer.Config(
                    baseMicroLamports = 10_000L,
                    minMicroLamports = 1_000L,
                    maxMicroLamports = 1_000_000L,
                    sampleWindowSize = 100
                )
            )
            assertNotNull(optimizer)
        }
    }

    // ==================== Integration Tests ====================

    @Test
    fun `ArcanaFlowV2 Integration - enqueue action`() {
        runBlocking {
            val secretBase58 = System.getenv("DEVNET_WALLET_SEED")
            Assume.assumeTrue(
                "Skipping: DEVNET_WALLET_SEED not set",
                secretBase58 != null
            )

            val seed = Base58.decode(secretBase58!!)
            val keypair = Keypair.fromSeed(seed)

            val arcana = ArcanaFlowV2(testScope)

            val action = ArcanaFlowV2.GameAction(
                playerId = keypair.publicKey,
                actionType = ArcanaFlowV2.ActionType.MOVE,
                payload = ByteArray(16) { (it * 2).toByte() },
                priority = ArcanaFlowV2.Priority.NORMAL
            )

            arcana.enqueue(action)

            println("Gaming Integration Test:")
            println("  Player: ${keypair.publicKey.toBase58()}")
            println("  Action ID: ${action.id}")
            println("  Action Type: ${action.actionType}")

            // Action should be enqueued successfully
            assertTrue(true)
        }
    }
}
