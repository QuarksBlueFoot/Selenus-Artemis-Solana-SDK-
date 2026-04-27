package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-association [Scenario] implementation.
 *
 * Lifecycle:
 *  1. `start(callbacks)` opens a WS client to `127.0.0.1:port`.
 *  2. The wallet runs the HELLO handshake; on success the cipher and
 *     negotiated protocol version are wired into the [WalletMwaServer]
 *     dispatcher.
 *  3. The dispatcher delivers typed requests to [callbacks] until
 *     either side closes the transport.
 *
 * The constructor takes a parsed [AssociationUri.Local] and a
 * [MobileWalletAdapterConfig] / [AuthRepository] pair. Wallets that
 * want to share the auth repo across multiple scenarios pass the same
 * instance; the in-memory default scopes per scenario.
 *
 * Tests can replace the transport via [transportFactory] to drive the
 * scenario over an in-process pipe instead of a real loopback socket.
 */
class LocalScenario internal constructor(
    private val associationUri: AssociationUri.Local,
    private val config: MobileWalletAdapterConfig,
    private val authRepository: AuthRepository,
    private val transportFactory: suspend (Int) -> WalletTransport,
    private val supportedVersions: List<AssociationUri.ProtocolVersion> = listOf(
        AssociationUri.ProtocolVersion.V1,
        AssociationUri.ProtocolVersion.LEGACY
    ),
    /**
     * Provider for the runtime "are we in low-power mode" check used
     * to gate the `noConnectionWarningTimeoutMs` warning.
     *
     * Upstream walletlib only fires `onLowPowerAndNoConnection` when
     * `PowerManager.isPowerSaveMode()` is true; firing it on a charged
     * device confuses users into thinking the wallet is buggy. We
     * default to a no-power-info-available stub (returns `false` so we
     * skip the warning) and let production wallets pass a real
     * provider that reads the system service.
     */
    private val powerConfigProvider: PowerConfigProvider = PowerConfigProvider.AlwaysHighPower
) : Scenario {

    /**
     * Public constructor that wires the production [WalletWebSocketClient]
     * implementation. Most callers go through this; tests use the
     * internal one above to swap in a paired in-memory transport.
     */
    constructor(
        associationUri: AssociationUri.Local,
        config: MobileWalletAdapterConfig = MobileWalletAdapterConfig(),
        authRepository: AuthRepository = InMemoryAuthRepository(
            AuthIssuerConfig(name = "Artemis Wallet")
        ),
        powerConfigProvider: PowerConfigProvider = PowerConfigProvider.AlwaysHighPower
    ) : this(
        associationUri = associationUri,
        config = config,
        authRepository = authRepository,
        transportFactory = { port -> WalletWebSocketClient().connect(port) },
        powerConfigProvider = powerConfigProvider
    )

    private val _state = MutableStateFlow<ScenarioState>(ScenarioState.NotStarted)
    override val state: StateFlow<ScenarioState> = _state.asStateFlow()

    @Volatile
    private var _sessionId: String? = null
    override val sessionId: String? get() = _sessionId

    override val associationPublicKey: ByteArray
        get() = associationUri.associationPublicKey

    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: WalletMwaServer? = null
    private var transport: WalletTransport? = null
    private val started = AtomicBoolean(false)

    override suspend fun start(callbacks: Scenario.Callbacks): String {
        check(started.compareAndSet(false, true)) { "Scenario.start() already invoked" }
        // Open AuthRepository resources before the first request can
        // land. Upstream walletlib calls `mAuthRepository.start()` on
        // session establishment so SQLite-backed impls can open the DB
        // exactly once. Idempotent default for in-memory impls.
        authRepository.start()
        _state.value = ScenarioState.Starting

        val warningJob = scope.launch {
            delay(config.noConnectionWarningTimeoutMs)
            // Upstream gate: only show the "battery optimisation may be
            // blocking us" hint when the device is actually in low-power
            // mode. On a charged device the connect just hasn't landed
            // yet — firing the warning would mislead the user.
            if (_state.value is ScenarioState.Starting && powerConfigProvider.isLowPowerMode()) {
                callbacks.onLowPowerAndNoConnection()
            }
        }

        val transport = try {
            transportFactory.invoke(associationUri.port)
        } catch (e: Throwable) {
            warningJob.cancel()
            _state.value = ScenarioState.Failed(e)
            callbacks.onScenarioError(e)
            throw e
        }
        warningJob.cancel()
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
            throw e
        }

        val sessionId = newSessionId()
        _sessionId = sessionId

        val server = WalletMwaServer(
            transport = transport,
            cipher = handshake.cipher,
            callbacks = wrapCallbacksWithLifecycle(callbacks),
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
            // Match upstream `mAuthRepository.stop()` on close: SQLite
            // impls release their DB handle here. Errors swallowed so a
            // misbehaving repo cannot leak a transport handle.
            try { authRepository.stop() } catch (_: Throwable) {}
            scope.cancel(java.util.concurrent.CancellationException("scenario close"))
            if (_state.value !is ScenarioState.Failed) {
                _state.value = ScenarioState.Closed("scenario close")
            }
        }
    }

    private fun wrapCallbacksWithLifecycle(inner: Scenario.Callbacks): Scenario.Callbacks {
        // Forward every callback verbatim. The scenario itself drives
        // the higher-level state transitions and the inner callbacks
        // never need to know about them.
        return inner
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    companion object {
        /**
         * Test-only factory that lets a unit test inject both ends of
         * an in-process transport pair. The provided [transport] is
         * the wallet end; the dApp end is supplied by the test via the
         * matching peer transport.
         */
        @JvmStatic
        internal fun forTransport(
            associationUri: AssociationUri.Local,
            config: MobileWalletAdapterConfig,
            authRepository: AuthRepository,
            transport: WalletTransport,
            supportedVersions: List<AssociationUri.ProtocolVersion> = listOf(
                AssociationUri.ProtocolVersion.V1,
                AssociationUri.ProtocolVersion.LEGACY
            )
        ): LocalScenario = LocalScenario(
            associationUri = associationUri,
            config = config,
            authRepository = authRepository,
            transportFactory = { transport },
            supportedVersions = supportedVersions
        )
    }
}
