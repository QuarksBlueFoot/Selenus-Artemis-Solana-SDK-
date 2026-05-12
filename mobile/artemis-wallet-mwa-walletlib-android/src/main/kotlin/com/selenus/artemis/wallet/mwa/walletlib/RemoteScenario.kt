package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote-association scenario using a WSS reflector.
 *
 * The reflector changes only the transport role: the wallet is an outbound WSS
 * client, but it remains the MWA JSON-RPC server after APP_PING and HELLO.
 */
class RemoteScenario internal constructor(
    private val associationUri: AssociationUri.Remote,
    private val config: MobileWalletAdapterConfig,
    private val authRepository: AuthRepository,
    private val transportFactory: suspend (AssociationUri.Remote) -> WalletTransport,
    private val supportedVersions: List<AssociationUri.ProtocolVersion> = listOf(
        AssociationUri.ProtocolVersion.V1,
        AssociationUri.ProtocolVersion.LEGACY
    )
) : Scenario {

    constructor(
        associationUri: AssociationUri.Remote,
        config: MobileWalletAdapterConfig = MobileWalletAdapterConfig(),
        authRepository: AuthRepository = InMemoryAuthRepository(
            AuthIssuerConfig(name = "Artemis Wallet")
        )
    ) : this(
        associationUri = associationUri,
        config = config,
        authRepository = authRepository,
        transportFactory = { remote -> ReflectorWebSocketClient().connect(remote) }
    )

    private val _state = MutableStateFlow<ScenarioState>(ScenarioState.NotStarted)
    override val state: StateFlow<ScenarioState> = _state.asStateFlow()

    @Volatile
    private var _sessionId: String? = null
    override val sessionId: String? get() = _sessionId

    override val associationPublicKey: ByteArray
        get() = associationUri.associationPublicKey

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var server: WalletMwaServer? = null
    private var transport: WalletTransport? = null

    override suspend fun start(callbacks: Scenario.Callbacks): String {
        check(started.compareAndSet(false, true)) { "Scenario.start() already invoked" }
        authRepository.start()
        _state.value = ScenarioState.Starting

        val transport = try {
            connectWithRetry()
        } catch (e: Throwable) {
            _state.value = ScenarioState.Failed(e)
            callbacks.onScenarioError(e)
            safeStopRepository()
            throw e
        }

        this.transport = transport
        _state.value = ScenarioState.Ready
        callbacks.onScenarioReady()

        val handshake = try {
            WalletSideHandshake.perform(
                transport = transport,
                associationPublicKey = associationUri.associationPublicKey,
                supportedVersions = supportedVersions,
                dappAdvertisedVersions = associationUri.protocolVersions
            )
        } catch (e: Throwable) {
            transport.close(1002, "handshake failed")
            _state.value = ScenarioState.Failed(e)
            callbacks.onScenarioError(e)
            safeStopRepository()
            throw e
        }

        val sessionId = UUID.randomUUID().toString()
        _sessionId = sessionId
        val server = WalletMwaServer(
            transport = transport,
            cipher = handshake.cipher,
            callbacks = callbacks,
            config = config,
            authRepository = authRepository,
            identityResolver = DefaultIdentityResolver(authRepository),
            initialRecvSeq = handshake.initialRecvSeq,
            initialSendSeq = handshake.initialSendSeq,
            parentJob = scope.coroutineContext[Job]
        )
        this.server = server
        server.start()
        _state.value = ScenarioState.ServingClients
        callbacks.onScenarioServingClients()
        return sessionId
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            server?.close("scenario close")
        } finally {
            try { transport?.close(1000, "scenario close") } catch (_: Throwable) {}
            safeStopRepository()
            scope.cancel(CancellationException("scenario close"))
            if (_state.value !is ScenarioState.Failed) {
                _state.value = ScenarioState.Closed("scenario close")
            }
        }
    }

    private suspend fun connectWithRetry(): WalletTransport {
        var lastError: Throwable? = null
        for (attempt in 0 until CONNECT_MAX_ATTEMPTS) {
            if (closed.get()) throw CancellationException("scenario close")
            try {
                return transportFactory(associationUri)
            } catch (e: Throwable) {
                lastError = e
                if (attempt == CONNECT_MAX_ATTEMPTS - 1) break
                delay(CONNECT_BACKOFF_MS[attempt.coerceAtMost(CONNECT_BACKOFF_MS.lastIndex)])
            }
        }
        throw MwaAssociationException("Unable to connect to reflector", lastError)
    }

    private suspend fun safeStopRepository() {
        try { authRepository.stop() } catch (_: Throwable) {}
    }

    companion object {
        const val CONNECT_MAX_ATTEMPTS: Int = 34
        internal val CONNECT_BACKOFF_MS: LongArray = longArrayOf(150, 150, 200, 500, 500, 750, 750, 1_000)

        @JvmStatic
        internal fun forTransport(
            associationUri: AssociationUri.Remote,
            config: MobileWalletAdapterConfig,
            authRepository: AuthRepository,
            transport: WalletTransport,
            supportedVersions: List<AssociationUri.ProtocolVersion> = listOf(
                AssociationUri.ProtocolVersion.V1,
                AssociationUri.ProtocolVersion.LEGACY
            )
        ): RemoteScenario = RemoteScenario(
            associationUri = associationUri,
            config = config,
            authRepository = authRepository,
            transportFactory = { transport },
            supportedVersions = supportedVersions
        )
    }
}