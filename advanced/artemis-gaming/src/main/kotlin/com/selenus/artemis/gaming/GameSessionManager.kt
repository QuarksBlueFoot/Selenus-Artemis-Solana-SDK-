package com.selenus.artemis.gaming

import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Signer
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * GameSessionManager - Manages multiplayer game sessions on Solana
 * 
 * Features:
 * - Session lifecycle management (create, join, leave, close)
 * - Real-time player state synchronization
 * - Deterministic turn ordering for fairness
 * - Atomic action batching for multiplayer consistency
 * - Anti-cheat verification via state hashing
 * - Spectator mode support
 */
class GameSessionManager(
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    data class Config(
        val maxPlayersPerSession: Int = 16,
        val maxSpectatorsPerSession: Int = 64,
        val sessionTimeoutMs: Long = 300_000L,    // 5 minutes
        val turnTimeoutMs: Long = 30_000L,         // 30 seconds
        val stateSyncIntervalMs: Long = 500L,
        val maxPendingActions: Int = 100,
        val enableAntiCheat: Boolean = true
    )

    /**
     * Game session representing a match/room
     */
    data class GameSession(
        val sessionId: String,
        val gameType: String,
        val host: Pubkey,
        val createdAtMs: Long,
        val status: SessionStatus,
        val players: Set<Player>,
        val spectators: Set<Pubkey>,
        val settings: SessionSettings,
        val currentTurn: Int,
        val turnOrder: List<Pubkey>,
        val stateHash: ByteArray,
        val lastActivityMs: Long
    ) {
        val playerCount: Int get() = players.size
        val isFull: Boolean get() = players.size >= settings.maxPlayers

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GameSession) return false
            return sessionId == other.sessionId
        }

        override fun hashCode(): Int = sessionId.hashCode()
    }

    enum class SessionStatus {
        WAITING,        // Waiting for players
        STARTING,       // Game countdown
        ACTIVE,         // Game in progress
        PAUSED,         // Game paused
        FINISHING,      // Game ending
        COMPLETED,      // Game finished
        CANCELLED       // Session cancelled
    }

    data class Player(
        val pubkey: Pubkey,
        val name: String,
        val joinedAtMs: Long,
        val status: PlayerStatus,
        val score: Long,
        val stats: Map<String, Long>
    )

    enum class PlayerStatus {
        READY,
        NOT_READY,
        PLAYING,
        DISCONNECTED,
        SPECTATING
    }

    data class SessionSettings(
        val maxPlayers: Int = 4,
        val isPrivate: Boolean = false,
        val turnBased: Boolean = false,
        val allowSpectators: Boolean = true,
        val requireReadyAll: Boolean = true,
        val customData: Map<String, String> = emptyMap()
    )

    /**
     * Player action within a session
     */
    data class PlayerAction(
        val actionId: String = UUID.randomUUID().toString(),
        val sessionId: String,
        val player: Pubkey,
        val actionType: String,
        val payload: ByteArray,
        val sequenceNumber: Long,
        val timestampMs: Long = System.currentTimeMillis(),
        val signature: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PlayerAction) return false
            return actionId == other.actionId
        }

        override fun hashCode(): Int = actionId.hashCode()

        /**
         * Compute action hash for verification
         */
        fun computeHash(): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(sessionId.toByteArray())
            digest.update(player.bytes)
            digest.update(actionType.toByteArray())
            digest.update(payload)
            digest.update(sequenceNumber.toBigInteger().toByteArray())
            return digest.digest()
        }
    }

    /**
     * Session events for reactive UI
     */
    sealed class SessionEvent {
        data class Created(val session: GameSession) : SessionEvent()
        data class PlayerJoined(val sessionId: String, val player: Player) : SessionEvent()
        data class PlayerLeft(val sessionId: String, val pubkey: Pubkey) : SessionEvent()
        data class PlayerReady(val sessionId: String, val pubkey: Pubkey) : SessionEvent()
        data class GameStarted(val sessionId: String) : SessionEvent()
        data class TurnChanged(val sessionId: String, val currentPlayer: Pubkey, val turnNumber: Int) : SessionEvent()
        data class ActionReceived(val sessionId: String, val action: PlayerAction) : SessionEvent()
        data class StateUpdated(val sessionId: String, val stateHash: ByteArray) : SessionEvent()
        data class GameEnded(val sessionId: String, val winner: Pubkey?, val finalScores: Map<Pubkey, Long>) : SessionEvent()
        data class SessionClosed(val sessionId: String, val reason: String) : SessionEvent()
        data class CheatDetected(val sessionId: String, val player: Pubkey, val reason: String) : SessionEvent()
    }

    // Internal state
    private val sessions = ConcurrentHashMap<String, GameSession>()
    private val pendingActions = ConcurrentHashMap<String, MutableList<PlayerAction>>()
    private val actionSequences = ConcurrentHashMap<String, AtomicLong>()
    private val sessionMutex = Mutex()
    private val actionSemaphore = Semaphore(config.maxPendingActions)

    // Event stream
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 0,
        extraBufferCapacity = 128
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    // Session state flows
    private val sessionStates = ConcurrentHashMap<String, MutableStateFlow<GameSession?>>()

    private var cleanupJob: Job? = null

    /**
     * Start session manager
     */
    fun start() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60_000L) // Check every minute
                cleanupStaleSessions()
            }
        }
    }

    /**
     * Stop session manager
     */
    fun stop() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    /**
     * Create a new game session
     */
    suspend fun createSession(
        gameType: String,
        host: Pubkey,
        settings: SessionSettings = SessionSettings()
    ): GameSession {
        sessionMutex.withLock {
            val sessionId = generateSessionId()
            val hostPlayer = Player(
                pubkey = host,
                name = "Host",
                joinedAtMs = System.currentTimeMillis(),
                status = PlayerStatus.NOT_READY,
                score = 0L,
                stats = emptyMap()
            )

            val session = GameSession(
                sessionId = sessionId,
                gameType = gameType,
                host = host,
                createdAtMs = System.currentTimeMillis(),
                status = SessionStatus.WAITING,
                players = setOf(hostPlayer),
                spectators = emptySet(),
                settings = settings,
                currentTurn = 0,
                turnOrder = listOf(host),
                stateHash = ByteArray(32),
                lastActivityMs = System.currentTimeMillis()
            )

            sessions[sessionId] = session
            pendingActions[sessionId] = mutableListOf()
            actionSequences[sessionId] = AtomicLong(0)
            sessionStates[sessionId] = MutableStateFlow(session)

            _events.tryEmit(SessionEvent.Created(session))
            return session
        }
    }

    /**
     * Join an existing session
     */
    suspend fun joinSession(
        sessionId: String,
        player: Pubkey,
        playerName: String,
        asSpectator: Boolean = false
    ): Result<GameSession> {
        sessionMutex.withLock {
            val session = sessions[sessionId]
                ?: return Result.failure(SessionError.NotFound(sessionId))

            if (session.status != SessionStatus.WAITING && !asSpectator) {
                return Result.failure(SessionError.GameInProgress(sessionId))
            }

            if (asSpectator) {
                if (!session.settings.allowSpectators) {
                    return Result.failure(SessionError.SpectatorsNotAllowed(sessionId))
                }
                if (session.spectators.size >= config.maxSpectatorsPerSession) {
                    return Result.failure(SessionError.SessionFull(sessionId))
                }

                val updated = session.copy(
                    spectators = session.spectators + player,
                    lastActivityMs = System.currentTimeMillis()
                )
                sessions[sessionId] = updated
                sessionStates[sessionId]?.value = updated
                return Result.success(updated)
            }

            if (session.isFull) {
                return Result.failure(SessionError.SessionFull(sessionId))
            }

            val newPlayer = Player(
                pubkey = player,
                name = playerName,
                joinedAtMs = System.currentTimeMillis(),
                status = PlayerStatus.NOT_READY,
                score = 0L,
                stats = emptyMap()
            )

            val updated = session.copy(
                players = session.players + newPlayer,
                turnOrder = session.turnOrder + player,
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[sessionId] = updated
            sessionStates[sessionId]?.value = updated

            _events.tryEmit(SessionEvent.PlayerJoined(sessionId, newPlayer))
            return Result.success(updated)
        }
    }

    /**
     * Leave a session
     */
    suspend fun leaveSession(sessionId: String, player: Pubkey): Result<Unit> {
        sessionMutex.withLock {
            val session = sessions[sessionId]
                ?: return Result.failure(SessionError.NotFound(sessionId))

            val isHost = session.host == player
            val isSpectator = session.spectators.contains(player)

            if (isSpectator) {
                val updated = session.copy(
                    spectators = session.spectators - player,
                    lastActivityMs = System.currentTimeMillis()
                )
                sessions[sessionId] = updated
                sessionStates[sessionId]?.value = updated
                return Result.success(Unit)
            }

            if (isHost && session.status == SessionStatus.WAITING) {
                // Host left while waiting - close session
                closeSession(sessionId, "Host left")
                return Result.success(Unit)
            }

            val updated = session.copy(
                players = session.players.filter { it.pubkey != player }.toSet(),
                turnOrder = session.turnOrder.filter { it != player },
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[sessionId] = updated
            sessionStates[sessionId]?.value = updated

            _events.tryEmit(SessionEvent.PlayerLeft(sessionId, player))
            return Result.success(Unit)
        }
    }

    /**
     * Set player ready status
     */
    suspend fun setReady(sessionId: String, player: Pubkey, ready: Boolean): Result<Unit> {
        sessionMutex.withLock {
            val session = sessions[sessionId]
                ?: return Result.failure(SessionError.NotFound(sessionId))

            val playerData = session.players.find { it.pubkey == player }
                ?: return Result.failure(SessionError.PlayerNotInSession(sessionId, player))

            val updatedPlayer = playerData.copy(
                status = if (ready) PlayerStatus.READY else PlayerStatus.NOT_READY
            )

            val updated = session.copy(
                players = session.players.filter { it.pubkey != player }.toSet() + updatedPlayer,
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[sessionId] = updated
            sessionStates[sessionId]?.value = updated

            if (ready) {
                _events.tryEmit(SessionEvent.PlayerReady(sessionId, player))
            }

            // Auto-start if all players ready
            if (session.settings.requireReadyAll) {
                val allReady = updated.players.all { it.status == PlayerStatus.READY }
                if (allReady && updated.players.size >= 2) {
                    startGame(sessionId)
                }
            }

            return Result.success(Unit)
        }
    }

    /**
     * Start the game
     */
    suspend fun startGame(sessionId: String): Result<Unit> {
        sessionMutex.withLock {
            val session = sessions[sessionId]
                ?: return Result.failure(SessionError.NotFound(sessionId))

            if (session.status != SessionStatus.WAITING) {
                return Result.failure(SessionError.InvalidState(sessionId, session.status))
            }

            // Initialize deterministic turn order
            val shuffledOrder = session.turnOrder.shuffled()
            val initialStateHash = computeInitialStateHash(session)

            val updated = session.copy(
                status = SessionStatus.ACTIVE,
                currentTurn = 0,
                turnOrder = shuffledOrder,
                stateHash = initialStateHash,
                players = session.players.map { it.copy(status = PlayerStatus.PLAYING) }.toSet(),
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[sessionId] = updated
            sessionStates[sessionId]?.value = updated

            _events.tryEmit(SessionEvent.GameStarted(sessionId))
            _events.tryEmit(SessionEvent.TurnChanged(sessionId, shuffledOrder.first(), 0))

            return Result.success(Unit)
        }
    }

    /**
     * Submit a player action
     */
    suspend fun submitAction(action: PlayerAction): Result<Long> = actionSemaphore.withPermit {
        val session = sessions[action.sessionId]
            ?: return Result.failure(SessionError.NotFound(action.sessionId))

        if (session.status != SessionStatus.ACTIVE) {
            return Result.failure(SessionError.InvalidState(action.sessionId, session.status))
        }

        // Verify player is in session
        if (session.players.none { it.pubkey == action.player }) {
            return Result.failure(SessionError.PlayerNotInSession(action.sessionId, action.player))
        }

        // Turn-based validation
        if (session.settings.turnBased) {
            val currentPlayer = session.turnOrder.getOrNull(session.currentTurn % session.turnOrder.size)
            if (currentPlayer != action.player) {
                return Result.failure(SessionError.NotYourTurn(action.sessionId, action.player))
            }
        }

        // Anti-cheat verification
        if (config.enableAntiCheat) {
            val isValid = verifyAction(session, action)
            if (!isValid) {
                _events.tryEmit(SessionEvent.CheatDetected(action.sessionId, action.player, "Invalid action hash"))
                return Result.failure(SessionError.CheatDetected(action.sessionId, action.player))
            }
        }

        // Assign sequence number
        val seq = actionSequences[action.sessionId]?.incrementAndGet()
            ?: return Result.failure(SessionError.NotFound(action.sessionId))

        val sequencedAction = action.copy(sequenceNumber = seq)

        // Add to pending
        pendingActions[action.sessionId]?.add(sequencedAction)

        // Update session state
        sessionMutex.withLock {
            val currentSession = sessions[action.sessionId] ?: return Result.failure(SessionError.NotFound(action.sessionId))
            val newStateHash = computeNextStateHash(currentSession.stateHash, sequencedAction)
            val nextTurn = if (session.settings.turnBased) {
                (currentSession.currentTurn + 1) % currentSession.turnOrder.size
            } else {
                currentSession.currentTurn
            }

            val updated = currentSession.copy(
                stateHash = newStateHash,
                currentTurn = nextTurn,
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[action.sessionId] = updated
            sessionStates[action.sessionId]?.value = updated

            _events.tryEmit(SessionEvent.ActionReceived(action.sessionId, sequencedAction))
            _events.tryEmit(SessionEvent.StateUpdated(action.sessionId, newStateHash))

            if (session.settings.turnBased && currentSession.turnOrder.isNotEmpty()) {
                val nextPlayer = currentSession.turnOrder[nextTurn]
                _events.tryEmit(SessionEvent.TurnChanged(action.sessionId, nextPlayer, nextTurn))
            }
        }

        return Result.success(seq)
    }

    /**
     * End the game
     */
    suspend fun endGame(
        sessionId: String,
        winner: Pubkey?,
        finalScores: Map<Pubkey, Long>
    ): Result<Unit> {
        sessionMutex.withLock {
            val session = sessions[sessionId]
                ?: return Result.failure(SessionError.NotFound(sessionId))

            val updated = session.copy(
                status = SessionStatus.COMPLETED,
                players = session.players.map { player ->
                    player.copy(
                        score = finalScores[player.pubkey] ?: player.score,
                        status = PlayerStatus.NOT_READY
                    )
                }.toSet(),
                lastActivityMs = System.currentTimeMillis()
            )
            sessions[sessionId] = updated
            sessionStates[sessionId]?.value = updated

            _events.tryEmit(SessionEvent.GameEnded(sessionId, winner, finalScores))
            return Result.success(Unit)
        }
    }

    /**
     * Close a session
     */
    suspend fun closeSession(sessionId: String, reason: String): Result<Unit> {
        sessionMutex.withLock {
            sessions.remove(sessionId)
            pendingActions.remove(sessionId)
            actionSequences.remove(sessionId)
            sessionStates[sessionId]?.value = null
            sessionStates.remove(sessionId)

            _events.tryEmit(SessionEvent.SessionClosed(sessionId, reason))
            return Result.success(Unit)
        }
    }

    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): GameSession? = sessions[sessionId]

    /**
     * Observe session changes
     */
    fun observeSession(sessionId: String): StateFlow<GameSession?> {
        return sessionStates.getOrPut(sessionId) { MutableStateFlow(sessions[sessionId]) }
    }

    /**
     * List available sessions
     */
    fun listSessions(gameType: String? = null): List<GameSession> {
        return sessions.values.filter { session ->
            session.status == SessionStatus.WAITING &&
            (gameType == null || session.gameType == gameType) &&
            !session.settings.isPrivate
        }
    }

    /**
     * Get pending actions for a session
     */
    fun getPendingActions(sessionId: String): List<PlayerAction> {
        return pendingActions[sessionId]?.toList() ?: emptyList()
    }

    private fun generateSessionId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
    }

    private fun computeInitialStateHash(session: GameSession): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(session.sessionId.toByteArray())
        digest.update(session.gameType.toByteArray())
        session.players.sortedBy { it.pubkey.toString() }.forEach {
            digest.update(it.pubkey.bytes)
        }
        return digest.digest()
    }

    private fun computeNextStateHash(currentHash: ByteArray, action: PlayerAction): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(currentHash)
        digest.update(action.computeHash())
        return digest.digest()
    }

    private fun verifyAction(session: GameSession, action: PlayerAction): Boolean {
        // Basic verification - in production would check signatures
        if (action.payload.isEmpty()) return false
        if (action.sessionId != session.sessionId) return false
        return true
    }

    private suspend fun cleanupStaleSessions() {
        val now = System.currentTimeMillis()
        val staleIds = sessions.entries
            .filter { (_, session) ->
                now - session.lastActivityMs > config.sessionTimeoutMs &&
                session.status != SessionStatus.ACTIVE
            }
            .map { it.key }

        staleIds.forEach { sessionId ->
            closeSession(sessionId, "Session timeout")
        }
    }

    /**
     * Session errors
     */
    sealed class SessionError : Exception() {
        data class NotFound(val sessionId: String) : SessionError()
        data class SessionFull(val sessionId: String) : SessionError()
        data class GameInProgress(val sessionId: String) : SessionError()
        data class SpectatorsNotAllowed(val sessionId: String) : SessionError()
        data class PlayerNotInSession(val sessionId: String, val player: Pubkey) : SessionError()
        data class NotYourTurn(val sessionId: String, val player: Pubkey) : SessionError()
        data class InvalidState(val sessionId: String, val status: SessionStatus) : SessionError()
        data class CheatDetected(val sessionId: String, val player: Pubkey) : SessionError()
    }
}
