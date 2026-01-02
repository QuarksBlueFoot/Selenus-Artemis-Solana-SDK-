package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * ArcanaFlowV2 - Next-generation mobile gaming transaction orchestrator
 * 
 * Design principles:
 * - Frame-based batching with deterministic timing
 * - Local-first with eventual blockchain consistency
 * - Predictive action queuing for lag compensation
 * - Input buffering with rollback support
 * - Session state management for multiplayer sync
 * 
 * This is an original implementation for mobile-first Solana gaming.
 */
class ArcanaFlowV2(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val frameWindowMs: Long = 50L,          // 20 FPS game tick
        val maxActionsPerFrame: Int = 8,
        val inputBufferSizeFrames: Int = 3,     // Rollback buffer
        val enablePrediction: Boolean = true,
        val maxPredictionFrames: Int = 4,
        val syncIntervalMs: Long = 1000L,       // State sync interval
        val enableActionMerging: Boolean = true
    )

    /**
     * Game action intent - represents player input before blockchain submission
     */
    data class GameAction(
        val id: String = UUID.randomUUID().toString(),
        val playerId: Pubkey,
        val actionType: ActionType,
        val payload: ByteArray,
        val priority: Priority = Priority.NORMAL,
        val timestampMs: Long = System.currentTimeMillis(),
        val predictedOutcome: ByteArray? = null  // For optimistic UI
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GameAction) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    enum class ActionType {
        MOVE,           // Player movement
        ATTACK,         // Combat action
        INTERACT,       // Object interaction
        ITEM_USE,       // Inventory action
        SKILL,          // Ability activation
        TRADE,          // P2P trade
        CUSTOM          // Game-specific
    }

    enum class Priority(val value: Int) {
        LOW(1),         // Background sync
        NORMAL(2),      // Standard actions
        HIGH(3),        // Combat actions
        CRITICAL(4)     // Must-land actions
    }

    /**
     * Game frame - collection of actions for a single blockchain transaction
     */
    data class GameFrame(
        val frameId: Long,
        val createdAtMs: Long,
        val actions: List<GameAction>,
        val stateHash: ByteArray,               // For determinism verification
        val predictedStateHash: ByteArray?,     // Predicted next state
        val instructions: List<Instruction>
    ) {
        val isEmpty: Boolean get() = actions.isEmpty()
        val actionCount: Int get() = actions.size

        /**
         * Compute frame hash for deterministic verification
         */
        fun computeHash(): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(frameId.toBigInteger().toByteArray())
            digest.update(stateHash)
            actions.forEach { action ->
                digest.update(action.id.toByteArray())
                digest.update(action.payload)
            }
            return digest.digest()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GameFrame) return false
            return frameId == other.frameId
        }

        override fun hashCode(): Int = frameId.hashCode()
    }

    /**
     * Frame result after blockchain submission
     */
    sealed class FrameResult {
        data class Success(
            val frame: GameFrame,
            val signature: String,
            val slot: Long,
            val latencyMs: Long
        ) : FrameResult()

        data class Failure(
            val frame: GameFrame,
            val error: FrameError,
            val retryable: Boolean
        ) : FrameResult()

        data class Rollback(
            val frame: GameFrame,
            val reason: String,
            val correctedStateHash: ByteArray
        ) : FrameResult()
    }

    sealed class FrameError {
        data class NetworkError(val message: String) : FrameError()
        data class ValidationError(val message: String) : FrameError()
        data class StateConflict(val expectedHash: ByteArray, val actualHash: ByteArray) : FrameError()
        data class Timeout(val durationMs: Long) : FrameError()
        data class RateLimited(val retryAfterMs: Long) : FrameError()
    }

    /**
     * Session state for multiplayer sync
     */
    data class SessionState(
        val sessionId: String,
        val frameCounter: Long,
        val lastConfirmedFrame: Long,
        val pendingFrames: List<GameFrame>,
        val stateHash: ByteArray,
        val participants: Set<Pubkey>
    )

    // Internal state
    private val frameCounter = AtomicLong(0L)
    private val actionQueue = MutableSharedFlow<GameAction>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val pendingActions = mutableListOf<GameAction>()
    private val actionMutex = Mutex()
    private val inputBuffer = ArrayDeque<GameFrame>()
    private var currentStateHash = ByteArray(32)
    private var sessionState: SessionState? = null

    // Observable streams
    private val _frames = MutableSharedFlow<GameFrame>(
        replay = 1,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frames: SharedFlow<GameFrame> = _frames.asSharedFlow()

    private val _results = MutableSharedFlow<FrameResult>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val results: SharedFlow<FrameResult> = _results.asSharedFlow()

    private val _stateChanges = MutableStateFlow(currentStateHash.clone())
    val stateChanges: StateFlow<ByteArray> = _stateChanges.asStateFlow()

    private var frameJob: Job? = null
    private var syncJob: Job? = null
    private var instructionBuilder: (List<GameAction>) -> List<Instruction> = { emptyList() }

    /**
     * Configure instruction builder for converting actions to instructions
     */
    fun setInstructionBuilder(builder: (List<GameAction>) -> List<Instruction>) {
        instructionBuilder = builder
    }

    /**
     * Start the frame processing loop
     */
    fun start() {
        if (frameJob?.isActive == true) return

        frameJob = scope.launch {
            while (isActive) {
                delay(config.frameWindowMs)
                processFrame()
            }
        }

        if (config.syncIntervalMs > 0) {
            syncJob = scope.launch {
                while (isActive) {
                    delay(config.syncIntervalMs)
                    syncState()
                }
            }
        }
    }

    /**
     * Stop processing
     */
    fun stop() {
        frameJob?.cancel()
        syncJob?.cancel()
        frameJob = null
        syncJob = null
    }

    /**
     * Queue a game action for processing
     */
    suspend fun enqueue(action: GameAction) {
        actionMutex.withLock {
            pendingActions.add(action)
            // Sort by priority for next frame
            pendingActions.sortByDescending { it.priority.value }
        }
    }

    /**
     * Queue multiple actions atomically
     */
    suspend fun enqueueBatch(actions: List<GameAction>) {
        actionMutex.withLock {
            pendingActions.addAll(actions)
            pendingActions.sortByDescending { it.priority.value }
        }
    }

    /**
     * Get predicted outcome for an action (for optimistic UI)
     */
    fun predictOutcome(action: GameAction): ByteArray? {
        if (!config.enablePrediction) return null
        // Simple hash-based prediction - actual games would implement game logic
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(currentStateHash)
        digest.update(action.payload)
        return digest.digest()
    }

    /**
     * Request rollback to a specific frame
     */
    suspend fun rollbackToFrame(frameId: Long) {
        actionMutex.withLock {
            val targetIdx = inputBuffer.indexOfFirst { it.frameId == frameId }
            if (targetIdx >= 0) {
                // Remove frames after target
                while (inputBuffer.size > targetIdx + 1) {
                    inputBuffer.removeLast()
                }
                // Restore state
                inputBuffer.lastOrNull()?.let { frame ->
                    currentStateHash = frame.stateHash.clone()
                    _stateChanges.value = currentStateHash.clone()
                }
            }
        }
    }

    /**
     * Get current session state for multiplayer sync
     */
    fun getSessionState(): SessionState? = sessionState

    /**
     * Join a multiplayer session
     */
    suspend fun joinSession(sessionId: String, participants: Set<Pubkey>): SessionState {
        val state = SessionState(
            sessionId = sessionId,
            frameCounter = frameCounter.get(),
            lastConfirmedFrame = 0L,
            pendingFrames = emptyList(),
            stateHash = currentStateHash.clone(),
            participants = participants
        )
        sessionState = state
        return state
    }

    /**
     * Leave current session
     */
    fun leaveSession() {
        sessionState = null
    }

    private suspend fun processFrame() {
        val actionsToProcess = actionMutex.withLock {
            val batch = pendingActions.take(config.maxActionsPerFrame)
            pendingActions.removeAll(batch.toSet())
            batch
        }

        if (actionsToProcess.isEmpty()) return

        val mergedActions = if (config.enableActionMerging) {
            mergeActions(actionsToProcess)
        } else {
            actionsToProcess
        }

        val frameId = frameCounter.incrementAndGet()
        val instructions = instructionBuilder(mergedActions)
        val predictedState = if (config.enablePrediction) {
            computePredictedState(mergedActions)
        } else {
            null
        }

        val frame = GameFrame(
            frameId = frameId,
            createdAtMs = System.currentTimeMillis(),
            actions = mergedActions,
            stateHash = currentStateHash.clone(),
            predictedStateHash = predictedState,
            instructions = instructions
        )

        // Maintain input buffer for rollback
        actionMutex.withLock {
            inputBuffer.addLast(frame)
            while (inputBuffer.size > config.inputBufferSizeFrames) {
                inputBuffer.removeFirst()
            }
        }

        // Update state with prediction
        if (predictedState != null) {
            currentStateHash = predictedState.clone()
            _stateChanges.value = currentStateHash.clone()
        }

        _frames.tryEmit(frame)
    }

    private fun mergeActions(actions: List<GameAction>): List<GameAction> {
        // Group by player and action type, keep latest of each
        return actions
            .groupBy { it.playerId to it.actionType }
            .map { (_, group) ->
                // For same player + action type, merge payloads or take latest
                group.maxByOrNull { it.timestampMs } ?: group.first()
            }
    }

    private fun computePredictedState(actions: List<GameAction>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(currentStateHash)
        actions.forEach { action ->
            digest.update(action.actionType.name.toByteArray())
            digest.update(action.payload)
        }
        return digest.digest()
    }

    private suspend fun syncState() {
        // Emit current state for external sync mechanisms
        _stateChanges.emit(currentStateHash.clone())
    }

    /**
     * Report frame result for adaptive optimization
     */
    suspend fun reportResult(result: FrameResult) {
        when (result) {
            is FrameResult.Success -> {
                sessionState?.let { state ->
                    sessionState = state.copy(
                        lastConfirmedFrame = result.frame.frameId,
                        pendingFrames = state.pendingFrames.filter { it.frameId > result.frame.frameId }
                    )
                }
            }
            is FrameResult.Rollback -> {
                currentStateHash = result.correctedStateHash.clone()
                _stateChanges.value = currentStateHash.clone()
            }
            is FrameResult.Failure -> {
                // Keep in pending for retry
            }
        }
        _results.tryEmit(result)
    }
}

/**
 * DSL builder for ArcanaFlowV2 configuration
 */
@DslMarker
annotation class ArcanaFlowDsl

@ArcanaFlowDsl
class ArcanaFlowV2Builder {
    private var config = ArcanaFlowV2.Config()
    private var scope: CoroutineScope? = null
    private var instructionBuilder: ((List<ArcanaFlowV2.GameAction>) -> List<Instruction>)? = null

    fun frameWindow(ms: Long) {
        config = config.copy(frameWindowMs = ms)
    }

    fun maxActionsPerFrame(count: Int) {
        config = config.copy(maxActionsPerFrame = count)
    }

    fun inputBuffer(frames: Int) {
        config = config.copy(inputBufferSizeFrames = frames)
    }

    fun prediction(enabled: Boolean, maxFrames: Int = 4) {
        config = config.copy(enablePrediction = enabled, maxPredictionFrames = maxFrames)
    }

    fun syncInterval(ms: Long) {
        config = config.copy(syncIntervalMs = ms)
    }

    fun actionMerging(enabled: Boolean) {
        config = config.copy(enableActionMerging = enabled)
    }

    fun scope(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun instructions(builder: (List<ArcanaFlowV2.GameAction>) -> List<Instruction>) {
        instructionBuilder = builder
    }

    fun build(): ArcanaFlowV2 {
        val flow = ArcanaFlowV2(
            scope = scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob()),
            config = config
        )
        instructionBuilder?.let { flow.setInstructionBuilder(it) }
        return flow
    }
}

/**
 * Create ArcanaFlowV2 with DSL
 */
fun arcanaFlow(block: ArcanaFlowV2Builder.() -> Unit): ArcanaFlowV2 {
    return ArcanaFlowV2Builder().apply(block).build()
}
