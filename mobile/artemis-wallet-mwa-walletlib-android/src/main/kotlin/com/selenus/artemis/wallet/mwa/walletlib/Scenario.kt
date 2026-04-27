package com.selenus.artemis.wallet.mwa.walletlib

import kotlinx.coroutines.flow.StateFlow

/**
 * Wallet-side counterpart of the dApp's MWA association object.
 *
 * One scenario corresponds to one association URI: the wallet receives
 * the URI through `Intent.ACTION_VIEW`, parses it, builds a scenario,
 * and calls [start]. The scenario opens the transport, performs the
 * HELLO handshake, then dispatches every JSON-RPC frame the dApp sends
 * through the registered [Callbacks].
 *
 * Every callback that yields a typed request (`onAuthorizeRequest`,
 * `onSignTransactionsRequest`, …) is `suspend` so the wallet UI can
 * await user confirmation without blocking the dispatch loop. The
 * matching `Request.completeWith*` calls return control to the
 * scenario, which serializes the response and pushes it back over the
 * wire.
 */
interface Scenario {
    /**
     * Lifecycle state observable from any thread. Always at least one
     * emission (the initial state) so consumers can collect with
     * `Flow.first()` semantics.
     */
    val state: StateFlow<ScenarioState>

    /**
     * Stable identifier for this scenario instance, set after [start]
     * succeeds. Useful for log correlation and for routing UI events
     * to a specific session when the wallet handles overlapping
     * scenarios.
     */
    val sessionId: String?

    /**
     * SEC1 uncompressed bytes of the dApp's association P-256 public
     * key. Same value as [AssociationUri.associationPublicKey] for the
     * URI this scenario was built from; surfaced here so callers that
     * already discarded the URI do not have to re-parse it.
     */
    val associationPublicKey: ByteArray

    /**
     * Open the transport, run the HELLO handshake, and start
     * dispatching JSON-RPC frames into [callbacks]. Returns the
     * generated [sessionId] on success; throws
     * [MwaAssociationException] / [MwaHandshakeException] on failure
     * so the caller can branch on the failure stage.
     */
    suspend fun start(callbacks: Callbacks): String

    /**
     * Tear down the transport and release any background coroutines.
     * Idempotent; calling twice is a no-op.
     */
    suspend fun close()

    /**
     * Lifecycle and request callbacks. Every method has a default
     * empty body so wallets can override only what they need.
     *
     * The `on*Request` methods are `suspend` so the wallet's UI flow
     * (typically a Composable + ViewModel) can await user confirmation
     * inline instead of stashing the request and resuming it from
     * another coroutine. They MUST eventually call one of the request's
     * `completeWith*` methods; failing to do so leaves the dApp's
     * JSON-RPC future hanging until its own timeout. The dispatcher
     * tears the request down on scenario close, so blocked UI flows do
     * not leak.
     */
    interface Callbacks {
        /** Transport bound and ready, no client connected yet. */
        fun onScenarioReady() {}
        /** First client connected and the HELLO handshake completed. */
        fun onScenarioServingClients() {}
        /** Last client disconnected. */
        fun onScenarioServingComplete() {}
        /** Final teardown reached after [Scenario.close] returned. */
        fun onScenarioComplete() {}
        /** Any unrecoverable error inside the scenario. */
        fun onScenarioError(t: Throwable) {}
        /** Resources released. Always last in the lifecycle. */
        fun onScenarioTeardownComplete() {}

        /** Fresh authorize from a dApp identity. Reach for the user. */
        suspend fun onAuthorizeRequest(request: AuthorizeRequest)
        /** Existing authorization being refreshed. May be auto-approved. */
        suspend fun onReauthorizeRequest(request: ReauthorizeRequest)
        /** Sign N transactions without sending. Show the user a per-tx confirm. */
        suspend fun onSignTransactionsRequest(request: SignTransactionsRequest)
        /** Sign N opaque messages. Show the user a per-message confirm. */
        suspend fun onSignMessagesRequest(request: SignMessagesRequest)
        /** Sign N transactions and broadcast them. Implies confirm + submit. */
        suspend fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest)

        /** dApp explicitly revoked an auth token. UI should clear it. */
        fun onDeauthorizedEvent(event: DeauthorizedEvent) {}

        /**
         * Local-scenario-only signal: no client has connected within
         * [MobileWalletAdapterConfig.noConnectionWarningTimeoutMs] of
         * [Scenario.start]. The wallet UI typically renders a hint
         * about disabling battery optimisation.
         */
        fun onLowPowerAndNoConnection() {}
    }
}

/**
 * Lifecycle state for a [Scenario].
 *
 * Sealed (not enum) so [Closed] and [Failed] can carry typed
 * descriptions without forcing every consumer through an `Any`-typed
 * payload getter.
 */
sealed class ScenarioState {
    object NotStarted : ScenarioState()
    object Starting : ScenarioState()
    object Ready : ScenarioState()
    object ServingClients : ScenarioState()
    object ServingComplete : ScenarioState()
    data class Closed(val reason: String? = null) : ScenarioState()
    data class Failed(val error: Throwable) : ScenarioState()
}
