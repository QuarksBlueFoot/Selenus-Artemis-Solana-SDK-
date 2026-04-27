package com.selenus.artemis.wallet.mwa.walletlib

import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Entry-point Activity wallets register for the `solana-wallet:`
 * scheme. Subclass this in the wallet app and override
 * [onAssociationParsed] (or [scenarioCallbacks]) to drive the wallet
 * UI from the typed [MwaUiRequest] flow.
 *
 * A consuming wallet adds something like the snippet below to its
 * AndroidManifest.xml:
 *
 * ```
 * <activity
 *     android:name=".MyWalletMwaActivity"
 *     android:exported="true"
 *     android:launchMode="singleTask"
 *     android:taskAffinity=""
 *     android:excludeFromRecents="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="solana-wallet" />
 *     </intent-filter>
 * </activity>
 * ```
 *
 * The Activity reads the inbound URI in [onCreate], parses it through
 * [AssociationUri.parse], and exposes a [MwaUiRequest] flow the
 * wallet's UI layer collects to render confirmation prompts. The
 * Activity does not start the [LocalScenario] itself — that's left to
 * the subclass so wallets that need to gate behind an unlock screen
 * can do so before the scenario opens the transport.
 */
abstract class MwaWalletActivity : ComponentActivity() {

    private val _requests = MutableSharedFlow<MwaUiRequest>(
        replay = 0,
        extraBufferCapacity = 16
    )

    /**
     * Stream of typed UI events derived from the JSON-RPC dispatch
     * loop. Wallet UIs collect this and render a per-event prompt.
     */
    val requests: SharedFlow<MwaUiRequest> = _requests.asSharedFlow()

    /**
     * Parsed association URI, set after [onCreate] succeeds and the
     * URI was valid. `null` when the Activity was launched without a
     * `solana-wallet:` URI (e.g. by mistake) or when parsing failed.
     */
    @Volatile
    var associationUri: AssociationUri? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data == null) {
            onAssociationFailed(MwaAssociationException("missing intent data"))
            return
        }
        val parsed = try {
            AssociationUri.parse(data)
        } catch (e: MwaAssociationException) {
            onAssociationFailed(e)
            return
        }
        associationUri = parsed
        onAssociationParsed(parsed)
    }

    /**
     * Hook invoked once the inbound URI parsed cleanly. Default
     * implementation is empty: subclasses override to start their
     * [LocalScenario] (or an equivalent) and feed the wallet UI.
     *
     * Subclasses that want to use the [requests] flow can do so by
     * launching a coroutine in [androidx.lifecycle.lifecycleScope]
     * that listens for [Scenario.Callbacks] and emits matching
     * [MwaUiRequest] events through [emitRequest].
     */
    protected open fun onAssociationParsed(uri: AssociationUri) {}

    /**
     * Hook invoked when the inbound URI is missing or malformed.
     * Default behavior closes the Activity. Wallets that want to
     * surface a typed error UI override this and call `finish()` from
     * their own flow.
     */
    protected open fun onAssociationFailed(error: MwaAssociationException) {
        finish()
    }

    /**
     * Push a typed event onto the [requests] flow. Used by
     * [scenarioCallbacks] to bridge the [Scenario.Callbacks] interface
     * onto a Kotlin [SharedFlow]. Marked `protected` so subclasses can
     * also surface synthetic events for unit tests.
     */
    protected fun emitRequest(event: MwaUiRequest) {
        // tryEmit drops on a buffered miss instead of suspending —
        // acceptable here because the SharedFlow's bounded buffer is
        // sized for normal request rates and a dropped event would
        // leak as a hung dApp future. We log on overflow so the
        // wallet's diagnostic view can call out the dropped event.
        if (!_requests.tryEmit(event)) {
            activityScope.launch { _requests.emit(event) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    /**
     * Lifecycle-bound coroutine scope tied to this Activity.
     *
     * Independent of androidx.lifecycle's `lifecycleScope` extension
     * property because that extension is in `lifecycle-runtime-ktx`,
     * which would force a transitive dependency on this module just
     * to bridge `tryEmit` overflows. The scope is cancelled in
     * [onDestroy] so emit-overflow handlers do not outlive the
     * Activity.
     */
    private val activityScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Reusable [Scenario.Callbacks] that funnels every wallet-side
     * request onto [requests]. Subclasses pass this to
     * [LocalScenario.start] when the default flow-based UX is enough.
     */
    protected open val scenarioCallbacks: Scenario.Callbacks = object : Scenario.Callbacks {
        override suspend fun onAuthorizeRequest(request: AuthorizeRequest) {
            emitRequest(MwaUiRequest.Authorize(request))
        }
        override suspend fun onReauthorizeRequest(request: ReauthorizeRequest) {
            emitRequest(MwaUiRequest.Reauthorize(request))
        }
        override suspend fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            emitRequest(MwaUiRequest.SignTransactions(request))
        }
        override suspend fun onSignMessagesRequest(request: SignMessagesRequest) {
            emitRequest(MwaUiRequest.SignMessages(request))
        }
        override suspend fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            emitRequest(MwaUiRequest.SignAndSendTransactions(request))
        }
        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            emitRequest(MwaUiRequest.Deauthorized(event))
        }
        override fun onLowPowerAndNoConnection() {
            emitRequest(MwaUiRequest.LowPowerAndNoConnection)
        }
    }

}

/**
 * Typed UI event surfaced by [MwaWalletActivity.requests].
 *
 * Wallet UIs `when`-branch on the variants and render a prompt per
 * variant. Each variant carries the underlying request object so the
 * wallet's UI can call the request's `completeWith*` methods directly.
 */
sealed class MwaUiRequest {
    data class Authorize(val request: AuthorizeRequest) : MwaUiRequest()
    data class Reauthorize(val request: ReauthorizeRequest) : MwaUiRequest()
    data class SignTransactions(val request: SignTransactionsRequest) : MwaUiRequest()
    data class SignMessages(val request: SignMessagesRequest) : MwaUiRequest()
    data class SignAndSendTransactions(val request: SignAndSendTransactionsRequest) : MwaUiRequest()
    data class Deauthorized(val event: DeauthorizedEvent) : MwaUiRequest()
    /** Local-scenario only: dApp port did not connect within the warning window. */
    object LowPowerAndNoConnection : MwaUiRequest()
}
