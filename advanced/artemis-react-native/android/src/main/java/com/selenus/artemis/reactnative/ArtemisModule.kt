/*
 * React Native bridge for the Artemis Solana SDK.
 *
 * Contract is object-shaped end to end: the JS layer never parses a
 * hand-rolled JSON string off the bridge. Every verb on this module
 * returns a WritableMap / WritableArray built from Artemis-native
 * types, which the TS wrapper consumes as plain objects. That keeps the
 * wire contract inspectable from the React Native debugger and removes
 * the double-encoding trap the prior revision had (stringify -> bridge
 * -> parse -> bridge -> stringify).
 *
 * Seed Vault auth tokens flow through as `String` everywhere. Upstream
 * `AuthToken` is opaque; we don't coerce to Long on the bridge so
 * decimal-overflow, reauthorize-mid-flow, and hex-style tokens all
 * survive the trip untouched.
 */
package com.selenus.artemis.reactnative

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.selenus.artemis.cnft.das.ArtemisDas
import com.selenus.artemis.cnft.das.CompositeDas
import com.selenus.artemis.cnft.das.HeliusDas
import com.selenus.artemis.cnft.das.RpcFallbackDas
import com.selenus.artemis.compute.ComputeBudgetPresets
import com.selenus.artemis.depin.DeviceIdentity
import com.selenus.artemis.gaming.MerkleDistributor
import com.selenus.artemis.programs.AssociatedToken
import com.selenus.artemis.programs.SystemProgram
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.RpcClientConfig
import com.selenus.artemis.runtime.Base58 as ArtemisBase58
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.runtime.PlatformCrypto
import com.selenus.artemis.runtime.PlatformEd25519
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.seedvault.SeedVaultManager
import com.selenus.artemis.solanapay.SolanaPayUri
import com.selenus.artemis.tx.Transaction
import com.selenus.artemis.ws.ConnectionState
import com.selenus.artemis.ws.RealtimeEngine
import com.selenus.artemis.wallet.Commitment
import com.selenus.artemis.wallet.SendTransactionOptions
import com.selenus.artemis.wallet.SignTxRequest
import com.selenus.artemis.wallet.mwa.InMemoryAuthTokenStore
import com.selenus.artemis.wallet.mwa.MwaWalletAdapter
import com.selenus.artemis.wallet.mwa.protocol.MwaAccount
import com.selenus.artemis.wallet.mwa.protocol.MwaCapabilities
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInPayload
import com.selenus.artemis.wallet.mwa.protocol.MwaSignInResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.Base64

class ArtemisModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mwaMutex = Mutex()

    @Volatile private var adapter: MwaWalletAdapter? = null
    @Volatile private var rpcClient: JsonRpcClient? = null
    @Volatile private var rpcApi: RpcApi? = null
    @Volatile private var rpcUrl: String = "https://api.mainnet-beta.solana.com"
    @Volatile private var wsUrl: String? = null
    @Volatile private var realtime: RealtimeEngine? = null
    @Volatile private var dasClient: ArtemisDas? = null
    @Volatile private var dasUrl: String? = null
    private val realtimeStateJob = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null)

    private val reactContextRef: ReactApplicationContext = reactContext

    private val seedVaultManager: SeedVaultManager = SeedVaultManager(reactContext)
    @Volatile private var seedVaultPromise: Promise? = null
    private val seedVaultRequestCode = 0x5D_56 // "SV"

    private val deviceIdentities = mutableMapOf<String, DeviceIdentity>()

    init {
        reactContext.addActivityEventListener(this)
    }

    private fun emitEvent(name: String, payload: WritableMap) {
        if (!reactContextRef.hasActiveCatalystInstance()) return
        reactContextRef
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(name, payload)
    }

    override fun getName(): String = "ArtemisModule"

    // ─── Lifecycle ────────────────────────────────────────────────────────

    override fun onActivityResult(
        activity: Activity?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode != seedVaultRequestCode) return
        val promise = seedVaultPromise ?: return
        seedVaultPromise = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            promise.reject("SEED_VAULT_CANCELLED", "User cancelled or operation failed")
            return
        }
        try {
            val parsed = seedVaultManager.parseAuthorizationResult(data)
            val map = Arguments.createMap()
            // Token is emitted as String; the Seed Vault provider path
            // accepts string tokens and reparses them strictly. See
            // SeedVaultManager.parseAuthTokenStrict.
            map.putString("authToken", parsed.authToken.toString())
            map.putDouble("accountId", parsed.accountId.toDouble())
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("SEED_VAULT_PARSE_ERROR", e.message, e)
        }
    }

    override fun onNewIntent(intent: Intent?) = Unit

    // ─── Initialization ───────────────────────────────────────────────────

    @ReactMethod
    fun initialize(
        identityUri: String,
        iconPath: String,
        identityName: String,
        chain: String
    ) {
        val activity = currentActivity ?: return
        adapter = MwaWalletAdapter(
            activity = activity,
            identityUri = Uri.parse(identityUri),
            iconPath = iconPath,
            identityName = identityName,
            chain = chain,
            authStore = InMemoryAuthTokenStore()
        )
    }

    @ReactMethod
    fun setRpcUrl(url: String) {
        rpcUrl = url
        val client = JsonRpcClient(RpcClientConfig(url))
        rpcClient = client
        rpcApi = RpcApi(client)
    }

    @ReactMethod
    fun setWsUrl(url: String) {
        wsUrl = url
    }

    @ReactMethod
    fun setDasUrl(url: String) {
        dasUrl = url
        dasClient = CompositeDas(
            primary = HeliusDas(rpcUrl = url),
            fallback = RpcFallbackDas(requireRpc())
        )
    }

    // ─── MWA 2.0 ──────────────────────────────────────────────────────────

    @ReactMethod
    fun connect(promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    a.connect()
                    promise.resolve(authorizationResultToMap(a))
                } catch (e: Exception) {
                    promise.reject("CONNECT_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun connectWithFeatures(features: ReadableArray?, addresses: ReadableArray?, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val featureList = features?.let { readableArrayOfStrings(it) }
                    val addressList = addresses?.let { arr ->
                        (0 until arr.size()).mapNotNull { i ->
                            arr.getString(i)?.let { Base64.getDecoder().decode(it) }
                        }
                    }
                    a.connectWithFeatures(requestedFeatures = featureList, addresses = addressList)
                    promise.resolve(authorizationResultToMap(a))
                } catch (e: Exception) {
                    promise.reject("CONNECT_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun connectWithSignIn(payload: ReadableMap, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val siws = parseSignInPayload(payload)
                    val siwsResult = a.connectWithSignIn(siws)

                    val map = authorizationResultToMap(a)
                    map.putMap("signInResult", signInResultToMap(siwsResult))
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("SIGN_IN_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun reauthorize(promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    a.reauthorize()
                    promise.resolve(authorizationResultToMap(a))
                } catch (e: Exception) {
                    promise.reject("REAUTHORIZE_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun deauthorize(promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    adapter?.deauthorize()
                    promise.resolve(null)
                } catch (e: Exception) {
                    promise.reject("DEAUTHORIZE_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun getCapabilities(promise: Promise) {
        scope.launch {
            try {
                val caps = requireAdapter().getMwaCapabilities()
                promise.resolve(capabilitiesToMap(caps))
            } catch (e: Exception) {
                promise.reject("CAPS_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun cloneAuthorization(promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val token = requireAdapter().cloneAuthorization()
                    promise.resolve(token)
                } catch (e: Exception) {
                    promise.reject("CLONE_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signTransaction(base64Tx: String, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val txBytes = Base64.getDecoder().decode(base64Tx)
                    // Transaction-signing path: `signMessages(list, request)`
                    // on MwaWalletAdapter maps to the wallet's
                    // `sign_transactions` RPC under the hood. It is the
                    // correct transaction-signing entry point despite the
                    // legacy method name. The earlier revision used
                    // `signArbitraryMessage`, which produced detached
                    // signatures instead of signed transaction bytes and
                    // broke downstream deserialize() calls.
                    @Suppress("DEPRECATION")
                    val signed = a.signMessages(
                        listOf(txBytes),
                        SignTxRequest(purpose = "signTransaction")
                    )
                    val first = signed.firstOrNull()
                        ?: error("Wallet returned no signed payload for signTransaction")
                    promise.resolve(Base64.getEncoder().encodeToString(first))
                } catch (e: Exception) {
                    promise.reject("SIGN_TX_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signTransactions(base64Txs: ReadableArray, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val txList = (0 until base64Txs.size()).mapNotNull { i ->
                        base64Txs.getString(i)?.let { Base64.getDecoder().decode(it) }
                    }
                    // See `signTransaction` above: signMessages is the
                    // transaction-batch signing entry point.
                    @Suppress("DEPRECATION")
                    val signed = a.signMessages(txList, SignTxRequest(purpose = "signTransactions"))
                    val out = Arguments.createArray()
                    signed.forEach { out.pushString(Base64.getEncoder().encodeToString(it)) }
                    promise.resolve(out)
                } catch (e: Exception) {
                    promise.reject("SIGN_TXS_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signAndSendTransaction(base64Tx: String, options: ReadableMap?, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val txBytes = Base64.getDecoder().decode(base64Tx)
                    val opts = parseSendTransactionOptions(options)
                    val result = a.signAndSendTransaction(txBytes, opts)
                    promise.resolve(sendResultToMap(result, index = 0))
                } catch (e: Exception) {
                    promise.reject("SIGN_SEND_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signAndSendTransactions(base64Txs: ReadableArray, options: ReadableMap?, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val txList = (0 until base64Txs.size()).mapNotNull { i ->
                        base64Txs.getString(i)?.let { Base64.getDecoder().decode(it) }
                    }
                    val opts = parseSendTransactionOptions(options)
                    val batch = a.signAndSendTransactions(txList, opts)

                    // Structured batch result. Callers can inspect
                    // every slot's success / failure / signed-not-
                    // broadcast state without guessing on an empty-string
                    // signature.
                    val results = Arguments.createArray()
                    batch.results.forEachIndexed { i, r -> results.pushMap(sendResultToMap(r, i)) }
                    val map = Arguments.createMap()
                    map.putArray("results", results)
                    map.putInt("successCount", batch.successCount)
                    map.putInt("failureCount", batch.failureCount)
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("SIGN_SEND_BATCH_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signMessage(base64Msg: String, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val msg = Base64.getDecoder().decode(base64Msg)
                    val sig = a.signArbitraryMessage(msg, SignTxRequest(purpose = "signMessage"))
                    promise.resolve(Base64.getEncoder().encodeToString(sig))
                } catch (e: Exception) {
                    promise.reject("SIGN_MSG_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signMessages(base64Msgs: ReadableArray, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val msgs = (0 until base64Msgs.size()).mapNotNull { i ->
                        base64Msgs.getString(i)?.let { Base64.getDecoder().decode(it) }
                    }
                    val sigs = msgs.map { msg ->
                        a.signArbitraryMessage(msg, SignTxRequest(purpose = "signMessages"))
                    }
                    val out = Arguments.createArray()
                    sigs.forEach { out.pushString(Base64.getEncoder().encodeToString(it)) }
                    promise.resolve(out)
                } catch (e: Exception) {
                    promise.reject("SIGN_MSGS_ERROR", e.message, e)
                }
            }
        }
    }

    @ReactMethod
    fun signMessagesDetached(base64Msgs: ReadableArray, promise: Promise) {
        scope.launch {
            mwaMutex.withLock {
                try {
                    val a = requireAdapter()
                    val msgs = (0 until base64Msgs.size()).mapNotNull { i ->
                        base64Msgs.getString(i)?.let { Base64.getDecoder().decode(it) }
                    }
                    val (originals, signatures) = a.signOffChainMessagesDetached(msgs)

                    val messagesArr = Arguments.createArray()
                    originals.forEach { messagesArr.pushString(Base64.getEncoder().encodeToString(it)) }
                    val sigsArr = Arguments.createArray()
                    signatures.forEach { sigsArr.pushString(Base64.getEncoder().encodeToString(it)) }

                    val map = Arguments.createMap()
                    map.putArray("messages", messagesArr)
                    map.putArray("signatures", sigsArr)
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("SIGN_MSGS_DET_ERROR", e.message, e)
                }
            }
        }
    }

    // ─── RPC ──────────────────────────────────────────────────────────────

    @ReactMethod
    fun getBalance(pubkeyStr: String, promise: Promise) {
        scope.launch {
            try {
                val client = requireRpc()
                val balance = client.getBalance(Pubkey.fromBase58(pubkeyStr))
                promise.resolve(balance.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getLatestBlockhash(promise: Promise) {
        scope.launch {
            try {
                val blockhash = requireRpc().getLatestBlockhash()
                promise.resolve(blockhash)
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    /**
     * RPC expansion. These wrap [RpcApi] so apps don't have to ship a
     * separate HTTP client alongside the bridge. Results come back as
     * JSON strings (the upstream RPC responses are already JSON);
     * callers parse with `JSON.parse`. Returning raw JSON avoids
     * double-serializing through the bridge for methods whose response
     * shapes vary by encoding and commitment.
     */

    @ReactMethod
    fun getAccountInfo(pubkeyStr: String, commitment: String?, encoding: String?, promise: Promise) {
        scope.launch {
            try {
                val json = requireRpcApi().getAccountInfo(
                    pubkeyBase58 = pubkeyStr,
                    commitment = commitment ?: "confirmed",
                    encoding = encoding ?: "base64"
                )
                promise.resolve(json.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getMultipleAccounts(pubkeys: ReadableArray, commitment: String?, promise: Promise) {
        scope.launch {
            try {
                val list = readableArrayOfStrings(pubkeys)
                val json = requireRpcApi().getMultipleAccounts(
                    pubkeys = list,
                    commitment = commitment ?: "confirmed",
                    encoding = "base64"
                )
                promise.resolve(json.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getTokenAccountsByOwner(
        ownerStr: String,
        mintStr: String?,
        programIdStr: String?,
        commitment: String?,
        promise: Promise
    ) {
        scope.launch {
            try {
                val json = requireRpcApi().getTokenAccountsByOwner(
                    owner = ownerStr,
                    mint = mintStr,
                    programId = programIdStr,
                    commitment = commitment ?: "confirmed"
                )
                promise.resolve(json.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun simulateTransaction(
        base64Tx: String,
        sigVerify: Boolean,
        replaceRecentBlockhash: Boolean,
        commitment: String?,
        promise: Promise
    ) {
        scope.launch {
            try {
                val json = requireRpcApi().simulateTransaction(
                    base64Tx = base64Tx,
                    sigVerify = sigVerify,
                    replaceRecentBlockhash = replaceRecentBlockhash,
                    commitment = commitment ?: "processed"
                )
                promise.resolve(json.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun sendRawTransaction(
        base64Tx: String,
        skipPreflight: Boolean,
        maxRetries: Double?,
        promise: Promise
    ) {
        scope.launch {
            try {
                val txBytes = Base64.getDecoder().decode(base64Tx)
                val sig = requireRpcApi().sendRawTransaction(
                    txBytes = txBytes,
                    skipPreflight = skipPreflight,
                    maxRetries = maxRetries?.toInt()
                )
                promise.resolve(sig)
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getSignatureStatuses(
        signatures: ReadableArray,
        searchTransactionHistory: Boolean,
        promise: Promise
    ) {
        scope.launch {
            try {
                val list = readableArrayOfStrings(signatures)
                val json = requireRpcApi().getSignatureStatuses(
                    signatures = list,
                    searchTransactionHistory = searchTransactionHistory
                )
                promise.resolve(json.toString())
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getSlot(commitment: String?, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(
                    requireRpcApi().getSlot(commitment ?: "confirmed").toString()
                )
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getBlockHeight(commitment: String?, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(
                    requireRpcApi().getBlockHeight(commitment ?: "confirmed").toString()
                )
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getMinimumBalanceForRentExemption(dataLength: Double, commitment: String?, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(
                    requireRpcApi().getMinimumBalanceForRentExemption(
                        dataLength = dataLength.toLong(),
                        commitment = commitment ?: "confirmed"
                    ).toString()
                )
            } catch (e: Exception) {
                promise.reject("RPC_ERROR", e.message, e)
            }
        }
    }

    // ─── Realtime (WebSocket subscriptions) ──────────────────────────────
    // The native [RealtimeEngine] owns reconnect + deterministic
    // resubscribe + endpoint rotation. The bridge relays every typed
    // notification back to JS through `DeviceEventEmitter` so a
    // subscribing component just wires a listener for the event name
    // the bridge returns from subscribeAccount / subscribeSignature.

    private fun ensureRealtime(): RealtimeEngine {
        realtime?.let { return it }
        val ws = wsUrl ?: derivedWsFromRpc(rpcUrl)
        val engine = RealtimeEngine(endpoints = listOf(ws))
        realtime = engine
        // One state-stream subscription per engine; previous subscription is
        // cancelled when the engine is replaced.
        realtimeStateJob.getAndSet(scope.launch {
            engine.state.collect { state ->
                val map = Arguments.createMap()
                map.putString("kind", state::class.simpleName ?: "Unknown")
                map.putDouble("epoch", state.epoch.toDouble())
                when (state) {
                    is ConnectionState.Connected -> {
                        map.putString("endpoint", state.endpoint)
                        map.putInt("subscriptions", state.subscriptions)
                    }
                    is ConnectionState.Connecting -> map.putString("endpoint", state.endpoint)
                    is ConnectionState.Reconnecting -> {
                        map.putString("endpoint", state.endpoint)
                        map.putInt("attempt", state.attempt)
                        map.putDouble("nextDelayMs", state.nextDelayMs.toDouble())
                        map.putString("reason", state.reason)
                    }
                    is ConnectionState.Closed -> map.putString("reason", state.reason)
                    else -> Unit
                }
                emitEvent("ArtemisRealtimeState", map)
            }
        })?.cancel()
        return engine
    }

    @ReactMethod
    fun realtimeConnect(promise: Promise) {
        scope.launch {
            try {
                ensureRealtime().connect()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("REALTIME_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun realtimeClose(promise: Promise) {
        try {
            realtimeStateJob.getAndSet(null)?.cancel()
            realtime?.close()
            realtime = null
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("REALTIME_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun subscribeAccount(pubkeyStr: String, commitment: String?, promise: Promise) {
        scope.launch {
            try {
                val engine = ensureRealtime()
                engine.connect()
                val eventName = "ArtemisAccount:$pubkeyStr"
                engine.subscribeAccount(pubkeyStr, commitment ?: "confirmed") { note ->
                    val map = Arguments.createMap()
                    map.putString("pubkey", note.pubkey)
                    map.putDouble("lamports", note.lamports.toDouble())
                    map.putDouble("slot", note.slot.toDouble())
                    note.data?.let { map.putString("data", it) }
                    note.owner?.let { map.putString("owner", it) }
                    emitEvent(eventName, map)
                }
                promise.resolve(eventName)
            } catch (e: Exception) {
                promise.reject("REALTIME_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun subscribeSignature(signature: String, commitment: String?, promise: Promise) {
        scope.launch {
            try {
                val engine = ensureRealtime()
                engine.connect()
                val eventName = "ArtemisSignature:$signature"
                engine.subscribeSignature(signature, commitment ?: "confirmed") { confirmed ->
                    val map = Arguments.createMap()
                    map.putString("signature", signature)
                    map.putBoolean("confirmed", confirmed)
                    emitEvent(eventName, map)
                }
                promise.resolve(eventName)
            } catch (e: Exception) {
                promise.reject("REALTIME_ERROR", e.message, e)
            }
        }
    }

    /**
     * React Native requires modules that emit events to implement
     * `addListener` / `removeListeners` so it can manage listener
     * lifecycle. No-op here; the native event emitter does the real
     * work.
     */
    @ReactMethod fun addListener(eventName: String) { /* no-op */ }
    @ReactMethod fun removeListeners(count: Double) { /* no-op */ }

    // ─── DAS (Digital Asset Standard) ────────────────────────────────────
    // Helius primary with RPC fallback. Callers choose the provider via
    // setDasUrl(); without a DAS URL configured, the fallback RPC path
    // serves getAssetsByOwner from SPL token accounts + Metaplex metadata.

    @ReactMethod
    fun dasAssetsByOwner(ownerStr: String, page: Double, limit: Double, promise: Promise) {
        scope.launch {
            try {
                val das = requireDas()
                val assets = das.assetsByOwner(
                    owner = Pubkey.fromBase58(ownerStr),
                    page = page.toInt().coerceAtLeast(1),
                    limit = limit.toInt().coerceAtLeast(1)
                )
                val arr = Arguments.createArray()
                assets.forEach { arr.pushMap(digitalAssetToMap(it)) }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("DAS_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun dasAsset(assetIdStr: String, promise: Promise) {
        scope.launch {
            try {
                val asset = requireDas().asset(assetIdStr)
                if (asset == null) promise.resolve(null)
                else promise.resolve(digitalAssetToMap(asset))
            } catch (e: Exception) {
                promise.reject("DAS_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun dasAssetsByCollection(collectionAddress: String, promise: Promise) {
        scope.launch {
            try {
                val assets = requireDas().assetsByCollection(collectionAddress)
                val arr = Arguments.createArray()
                assets.forEach { arr.pushMap(digitalAssetToMap(it)) }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("DAS_ERROR", e.message, e)
            }
        }
    }

    // ─── Compute budget helpers ──────────────────────────────────────────
    // Returns a base64-encoded serialized `SetComputeUnitLimit` /
    // `SetComputeUnitPrice` instruction so the JS layer can assemble a
    // transaction without re-implementing the compute-budget program.

    @ReactMethod
    fun computeBudgetSetUnitLimit(units: Double, promise: Promise) {
        try {
            val ix = ComputeBudgetPresets.setComputeUnitLimit(units.toInt())
            promise.resolve(encodeInstruction(ix))
        } catch (e: Exception) {
            promise.reject("COMPUTE_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun computeBudgetSetUnitPrice(microLamports: String, promise: Promise) {
        try {
            val ix = ComputeBudgetPresets.setComputeUnitPrice(microLamports.toLong())
            promise.resolve(encodeInstruction(ix))
        } catch (e: Exception) {
            promise.reject("COMPUTE_ERROR", e.message, e)
        }
    }

    // ─── PDA + ATA derivation ────────────────────────────────────────────

    @ReactMethod
    fun findProgramAddress(seedsB64: ReadableArray, programIdStr: String, promise: Promise) {
        try {
            val seeds = (0 until seedsB64.size()).mapNotNull { i ->
                seedsB64.getString(i)?.let { Base64.getDecoder().decode(it) }
            }
            val result = Pda.findProgramAddress(seeds, Pubkey.fromBase58(programIdStr))
            val map = Arguments.createMap()
            map.putString("address", result.address.toBase58())
            map.putInt("bump", result.bump.toInt() and 0xFF)
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("PDA_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getAssociatedTokenAddress(
        ownerStr: String,
        mintStr: String,
        tokenProgramStr: String?,
        promise: Promise
    ) {
        try {
            val owner = Pubkey.fromBase58(ownerStr)
            val mint = Pubkey.fromBase58(mintStr)
            val ata = if (tokenProgramStr != null) {
                AssociatedToken.address(owner, mint, Pubkey.fromBase58(tokenProgramStr))
            } else {
                AssociatedToken.address(owner, mint)
            }
            promise.resolve(ata.toBase58())
        } catch (e: Exception) {
            promise.reject("ATA_ERROR", e.message, e)
        }
    }

    // ─── System program helper ────────────────────────────────────────────

    @ReactMethod
    fun buildTransferTransaction(
        fromStr: String,
        toStr: String,
        lamports: String,
        blockhash: String,
        promise: Promise
    ) {
        try {
            val from = Pubkey.fromBase58(fromStr)
            val to = Pubkey.fromBase58(toStr)
            val amount = lamports.toLong()
            val ix = SystemProgram.transfer(from, to, amount)
            val tx = Transaction(from, blockhash, listOf(ix))
            val msg = tx.compileMessage()
            val bytes = msg.serialize()
            promise.resolve(Base64.getEncoder().encodeToString(bytes))
        } catch (e: Exception) {
            promise.reject("BUILD_TX_ERROR", e.message, e)
        }
    }

    // ─── DePIN ────────────────────────────────────────────────────────────

    @ReactMethod
    fun generateDeviceIdentity(promise: Promise) {
        try {
            val device = DeviceIdentity.generate()
            val pubkey = device.publicKey.toBase58()
            deviceIdentities[pubkey] = device
            promise.resolve(pubkey)
        } catch (e: Exception) {
            promise.reject("DEPIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun signLocationProof(
        devicePubkey: String,
        lat: Double,
        lng: Double,
        timestamp: Double,
        promise: Promise
    ) {
        try {
            val device = deviceIdentities[devicePubkey]
                ?: throw IllegalArgumentException("Device not found")
            val proof = device.createLocationProof(lat, lng, timestamp.toLong())
            promise.resolve(proof.signature)
        } catch (e: Exception) {
            promise.reject("DEPIN_ERROR", e.message, e)
        }
    }

    // ─── Solana Pay ───────────────────────────────────────────────────────

    @ReactMethod
    fun buildSolanaPayUri(
        recipientStr: String,
        amountStr: String,
        label: String,
        message: String,
        promise: Promise
    ) {
        try {
            // Upstream `Request.amount` is a plain string so percent-
            // encoding stays deterministic across BigDecimal scales and
            // trailing-zero variants. Validate as BigDecimal first to
            // reject malformed input before it hits the URI layer.
            val amountNormalized = BigDecimal(amountStr).toPlainString()
            val req = SolanaPayUri.Request(
                recipient = Pubkey.fromBase58(recipientStr),
                amount = amountNormalized,
                label = label,
                message = message
            )
            promise.resolve(SolanaPayUri.build(req))
        } catch (e: Exception) {
            promise.reject("SOLANA_PAY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun parseSolanaPayUri(uri: String, promise: Promise) {
        try {
            val req = SolanaPayUri.parse(uri)
            val map = Arguments.createMap()
            map.putString("recipient", req.recipient.toBase58())
            // Upstream Request fields are already plain strings; forward
            // them as-is so the JS side sees the exact on-chain value.
            req.amount?.let { map.putString("amount", it) }
            req.label?.let { map.putString("label", it) }
            req.message?.let { map.putString("message", it) }
            req.memo?.let { map.putString("memo", it) }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("SOLANA_PAY_ERROR", e.message, e)
        }
    }

    // ─── Seed Vault (Android wallet apps) ─────────────────────────────────

    @ReactMethod
    fun seedVaultAuthorize(purpose: String, promise: Promise) =
        launchSeedVaultIntent(promise) { it.buildAuthorizeIntent(parseSeedVaultPurpose(purpose)) }

    @ReactMethod
    fun seedVaultCreateSeed(purpose: String, promise: Promise) =
        launchSeedVaultIntent(promise) { it.buildCreateSeedIntent(parseSeedVaultPurpose(purpose)) }

    @ReactMethod
    fun seedVaultImportSeed(purpose: String, promise: Promise) =
        launchSeedVaultIntent(promise) { it.buildImportSeedIntent(parseSeedVaultPurpose(purpose)) }

    @ReactMethod
    fun seedVaultGetAccounts(authToken: String, promise: Promise) {
        scope.launch {
            try {
                seedVaultManager.connect()
                val accounts = seedVaultManager.getAccounts(authToken)
                val arr = Arguments.createArray()
                accounts.forEach { acc ->
                    val map = Arguments.createMap()
                    map.putDouble("id", acc.id.toDouble())
                    map.putString("name", acc.name)
                    map.putString("publicKey", acc.publicKey.toBase58())
                    acc.derivationPath?.let { map.putString("derivationPath", it) }
                    arr.pushMap(map)
                }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultSignMessages(authToken: String, base64Messages: ReadableArray, promise: Promise) {
        scope.launch {
            try {
                seedVaultManager.connect()
                val messages = readableArrayOfBase64Bytes(base64Messages)
                val sigs = seedVaultManager.signMessages(authToken, messages)
                promise.resolve(base64Array(sigs))
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultSignTransactions(authToken: String, base64Txs: ReadableArray, promise: Promise) {
        scope.launch {
            try {
                seedVaultManager.connect()
                val txs = readableArrayOfBase64Bytes(base64Txs)
                val sigs = seedVaultManager.signTransactions(authToken, txs)
                promise.resolve(base64Array(sigs))
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultRequestPublicKeys(
        authToken: String,
        derivationPaths: ReadableArray,
        promise: Promise
    ) {
        scope.launch {
            try {
                seedVaultManager.connect()
                val uris = (0 until derivationPaths.size()).mapNotNull { i ->
                    derivationPaths.getString(i)?.let { Uri.parse(it) }
                }
                val keys = seedVaultManager.requestPublicKeys(authToken, uris)
                val out = Arguments.createArray()
                keys.forEach { out.pushString(it.toBase58()) }
                promise.resolve(out)
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultSignWithDerivationPath(
        authToken: String,
        derivationPath: String,
        base64Payloads: ReadableArray,
        promise: Promise
    ) {
        scope.launch {
            try {
                seedVaultManager.connect()
                val payloads = readableArrayOfBase64Bytes(base64Payloads)
                val sigs = seedVaultManager.signWithDerivationPath(
                    authToken = authToken,
                    derivationPath = Uri.parse(derivationPath),
                    payloads = payloads
                )
                promise.resolve(base64Array(sigs))
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun seedVaultDeauthorize(authToken: String, promise: Promise) {
        scope.launch {
            try {
                seedVaultManager.connect()
                seedVaultManager.deauthorize(authToken)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("SEED_VAULT_ERROR", e.message, e)
            }
        }
    }

    // ─── Cross-platform crypto primitives ────────────────────────────────
    // These methods mirror the iOS Swift bridge so the JS `Base58` and
    // `Crypto` helpers in Base58.ts work identically on both platforms.
    // All byte data crosses the bridge as base64 strings so no
    // ArrayBuffer conversion is needed at the RN boundary.

    @ReactMethod
    fun base64ToBase58(base64: String, promise: Promise) {
        try {
            val bytes = Base64.getDecoder().decode(base64)
            promise.resolve(ArtemisBase58.encode(bytes))
        } catch (e: Exception) {
            promise.reject("BASE58_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun base58ToBase64(base58: String, promise: Promise) {
        try {
            val bytes = ArtemisBase58.decode(base58)
            promise.resolve(Base64.getEncoder().encodeToString(bytes))
        } catch (e: Exception) {
            promise.reject("BASE58_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isValidBase58(input: String, promise: Promise) {
        val ok = try {
            ArtemisBase58.decode(input); true
        } catch (_: Exception) {
            false
        }
        promise.resolve(ok)
    }

    @ReactMethod
    fun isValidSolanaPubkey(input: String, promise: Promise) {
        val ok = try {
            ArtemisBase58.decode(input).size == 32
        } catch (_: Exception) {
            false
        }
        promise.resolve(ok)
    }

    @ReactMethod
    fun isValidSolanaSignature(input: String, promise: Promise) {
        val ok = try {
            ArtemisBase58.decode(input).size == 64
        } catch (_: Exception) {
            false
        }
        promise.resolve(ok)
    }

    /**
     * Base58Check: payload + 4-byte double-SHA-256 checksum, Base58
     * encoded. Matches the iOS CryptoKit path byte-for-byte so round-
     * trips from one platform decode cleanly on the other.
     */
    @ReactMethod
    fun base58EncodeCheck(base64: String, promise: Promise) {
        try {
            val payload = Base64.getDecoder().decode(base64)
            val digest1 = PlatformCrypto.sha256(payload)
            val digest2 = PlatformCrypto.sha256(digest1)
            val checksum = digest2.copyOfRange(0, 4)
            promise.resolve(ArtemisBase58.encode(payload + checksum))
        } catch (e: Exception) {
            promise.reject("BASE58_CHECK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun base58DecodeCheck(input: String, promise: Promise) {
        try {
            val decoded = ArtemisBase58.decode(input)
            require(decoded.size >= 4) { "Base58Check input too short" }
            val payload = decoded.copyOfRange(0, decoded.size - 4)
            val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
            val digest1 = PlatformCrypto.sha256(payload)
            val digest2 = PlatformCrypto.sha256(digest1)
            val expected = digest2.copyOfRange(0, 4)
            require(checksum.contentEquals(expected)) { "Base58Check checksum mismatch" }
            promise.resolve(Base64.getEncoder().encodeToString(payload))
        } catch (e: Exception) {
            promise.reject("BASE58_CHECK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun sha256(base64Data: String, promise: Promise) {
        try {
            val data = Base64.getDecoder().decode(base64Data)
            promise.resolve(Base64.getEncoder().encodeToString(PlatformCrypto.sha256(data)))
        } catch (e: Exception) {
            promise.reject("SHA256_ERROR", e.message, e)
        }
    }

    /**
     * Crypto-layer keypair generator. Named `cryptoGenerateKeypair` so
     * the bridge doesn't collide with MWA's `signMessage` surface; JS
     * wraps both under the `Crypto` namespace in Base58.ts.
     */
    @ReactMethod
    fun cryptoGenerateKeypair(promise: Promise) {
        try {
            val kp = Keypair.generate()
            val seed = kp.secretKeyBytes() // 32-byte Ed25519 seed
            val map = Arguments.createMap()
            map.putString("publicKey", kp.publicKey.toBase58())
            map.putString("secretKey", ArtemisBase58.encode(seed))
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("CRYPTO_KEYGEN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun cryptoSign(messageBase64: String, secretKeyBase64: String, promise: Promise) {
        try {
            val msg = Base64.getDecoder().decode(messageBase64)
            val seed = Base64.getDecoder().decode(secretKeyBase64)
            require(seed.size == 32) { "Secret key must be a 32-byte Ed25519 seed" }
            val sig = PlatformEd25519.sign(seed, msg)
            promise.resolve(Base64.getEncoder().encodeToString(sig))
        } catch (e: Exception) {
            promise.reject("CRYPTO_SIGN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun cryptoVerify(
        signatureBase64: String,
        messageBase64: String,
        publicKeyBase64: String,
        promise: Promise
    ) {
        try {
            val sig = Base64.getDecoder().decode(signatureBase64)
            val msg = Base64.getDecoder().decode(messageBase64)
            val pk = Base64.getDecoder().decode(publicKeyBase64)
            promise.resolve(PlatformEd25519.verify(pk, sig, msg))
        } catch (e: Exception) {
            promise.reject("CRYPTO_VERIFY_ERROR", e.message, e)
        }
    }

    // ─── Gaming ───────────────────────────────────────────────────────────

    @ReactMethod
    fun verifyMerkleProof(
        proofArr: ReadableArray,
        rootStr: String,
        leafStr: String,
        promise: Promise
    ) {
        try {
            val proof = readableArrayOfBase64Bytes(proofArr)
            val root = Base64.getDecoder().decode(rootStr)
            val leaf = Base64.getDecoder().decode(leafStr)
            val valid = MerkleDistributor.verify(proof, root, leaf)
            promise.resolve(valid)
        } catch (e: Exception) {
            promise.reject("GAMING_ERROR", e.message, e)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun requireAdapter(): MwaWalletAdapter =
        adapter ?: error("ArtemisModule.initialize(...) must be called before MWA methods")

    private fun requireRpc(): JsonRpcClient = rpcClient
        ?: JsonRpcClient(RpcClientConfig(rpcUrl)).also {
            rpcClient = it
            rpcApi = RpcApi(it)
        }

    private fun requireRpcApi(): RpcApi = rpcApi
        ?: RpcApi(requireRpc()).also { rpcApi = it }

    private fun requireDas(): ArtemisDas = dasClient ?: run {
        val rpc = requireRpc()
        val das = dasUrl?.let { url ->
            CompositeDas(primary = HeliusDas(rpcUrl = url), fallback = RpcFallbackDas(rpc))
        } ?: RpcFallbackDas(rpc)
        dasClient = das
        das
    }

    private fun derivedWsFromRpc(url: String): String =
        url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")

    private fun digitalAssetToMap(a: com.selenus.artemis.cnft.das.DigitalAsset): WritableMap {
        val map = Arguments.createMap()
        map.putString("id", a.id)
        map.putString("name", a.name)
        map.putString("symbol", a.symbol)
        map.putString("uri", a.uri)
        map.putString("owner", a.owner)
        map.putInt("royaltyBasisPoints", a.royaltyBasisPoints)
        map.putBoolean("isCompressed", a.isCompressed)
        map.putBoolean("frozen", a.frozen)
        a.collectionAddress?.let { map.putString("collectionAddress", it) }
        map.putBoolean("collectionVerified", a.collectionVerified)
        return map
    }

    private fun encodeInstruction(ix: com.selenus.artemis.tx.Instruction): WritableMap {
        val map = Arguments.createMap()
        map.putString("programId", ix.programId.toBase58())
        val accounts = Arguments.createArray()
        ix.accounts.forEach { meta ->
            val accMap = Arguments.createMap()
            accMap.putString("pubkey", meta.pubkey.toBase58())
            accMap.putBoolean("isSigner", meta.isSigner)
            accMap.putBoolean("isWritable", meta.isWritable)
            accounts.pushMap(accMap)
        }
        map.putArray("accounts", accounts)
        map.putString("data", Base64.getEncoder().encodeToString(ix.data))
        return map
    }

    /**
     * Build the AuthorizationResult object the TS side consumes. Mirrors
     * upstream MWA 2.0 shape: authToken + accounts[] + walletUriBase +
     * walletIcon + capabilities (hoisted for convenience since the TS
     * wrapper caches it right after connect).
     */
    private suspend fun authorizationResultToMap(a: MwaWalletAdapter): WritableMap {
        val raw = a.lastAuthorization
        val caps = a.getMwaCapabilities()

        val map = Arguments.createMap()
        map.putString("authToken", raw?.authToken ?: "")
        map.putString("address", a.publicKey.toBase58())

        val accountsArr = Arguments.createArray()
        raw?.accounts?.forEach { accountsArr.pushMap(accountToMap(it)) }
        map.putArray("accounts", accountsArr)

        raw?.walletUriBase?.let { map.putString("walletUriBase", it) }
        raw?.walletIcon?.let { map.putString("walletIcon", it) }

        map.putMap("capabilities", capabilitiesToMap(caps))
        return map
    }

    private fun accountToMap(acct: MwaAccount): WritableMap {
        val map = Arguments.createMap()
        // `address` from upstream is base64-encoded public-key bytes; we
        // expose both the base64 (upstream-native) and base58 (Solana-
        // native) forms so JS callers can pick the shape they need.
        map.putString("address", acct.address)
        val pkBytes = Base64.getDecoder().decode(acct.address)
        map.putString("addressBase58", Pubkey(pkBytes).toBase58())
        acct.label?.let { map.putString("label", it) }
        acct.icon?.let { map.putString("icon", it) }
        acct.displayAddress?.let { map.putString("displayAddress", it) }
        acct.displayAddressFormat?.let { map.putString("displayAddressFormat", it) }
        acct.chains?.let { list ->
            val arr = Arguments.createArray()
            list.forEach { arr.pushString(it) }
            map.putArray("chains", arr)
        }
        acct.features?.let { list ->
            val arr = Arguments.createArray()
            list.forEach { arr.pushString(it) }
            map.putArray("features", arr)
        }
        return map
    }

    private fun signInResultToMap(siws: MwaSignInResult): WritableMap {
        val map = Arguments.createMap()
        map.putString("address", siws.address)
        map.putString("signedMessage", siws.signedMessage)
        map.putString("signature", siws.signature)
        siws.signatureType?.let { map.putString("signatureType", it) }
        return map
    }

    private fun capabilitiesToMap(c: MwaCapabilities): WritableMap {
        val map = Arguments.createMap()
        map.putInt("maxTransactionsPerRequest", c.maxTransactionsPerRequest ?: 0)
        map.putInt("maxMessagesPerRequest", c.maxMessagesPerRequest ?: 0)
        map.putBoolean("supportsSignAndSendTransactions", c.supportsSignAndSend())
        map.putBoolean("supportsCloneAuthorization", c.supportsCloneAuth())
        map.putBoolean("supportsSignTransactions", c.supportsSignTransactions())
        map.putBoolean("supportsSignIn", c.supportsSignIn())
        map.putBoolean("supportsLegacyTransactions", c.supportsLegacyTransactions())
        map.putBoolean("supportsVersionedTransactions", c.supportsVersionedTransactions())

        val features = Arguments.createArray()
        c.allFeatures().forEach { features.pushString(it) }
        map.putArray("features", features)

        val versions = Arguments.createArray()
        if (c.supportsLegacyTransactions()) versions.pushString("legacy")
        if (c.supportsVersionedTransactions()) versions.pushInt(0)
        map.putArray("supportedTransactionVersions", versions)
        return map
    }

    private fun sendResultToMap(
        result: com.selenus.artemis.wallet.SendTransactionResult,
        index: Int
    ): WritableMap {
        val map = Arguments.createMap()
        map.putInt("index", index)
        map.putString("signature", result.signature)
        map.putBoolean("confirmed", result.confirmed)
        result.slot?.let { map.putDouble("slot", it.toDouble()) }
        result.error?.let { map.putString("error", it) }
        result.signedRaw?.let {
            map.putString("signedRaw", Base64.getEncoder().encodeToString(it))
        }
        map.putBoolean("isSuccess", result.isSuccess)
        map.putBoolean("isFailure", result.isFailure)
        map.putBoolean("isSignedButNotBroadcast", result.isSignedButNotBroadcast)
        return map
    }

    private fun parseSendTransactionOptions(options: ReadableMap?): SendTransactionOptions {
        if (options == null) return SendTransactionOptions()
        fun has(key: String) = options.hasKey(key) && !options.isNull(key)
        return SendTransactionOptions(
            minContextSlot = if (has("minContextSlot")) options.getDouble("minContextSlot").toLong() else null,
            commitment = if (has("commitment")) parseCommitment(options.getString("commitment")!!) else Commitment.CONFIRMED,
            skipPreflight = has("skipPreflight") && options.getBoolean("skipPreflight"),
            maxRetries = if (has("maxRetries")) options.getInt("maxRetries") else null,
            preflightCommitment = if (has("preflightCommitment")) parseCommitment(options.getString("preflightCommitment")!!) else Commitment.PROCESSED,
            waitForConfirmation = !has("waitForConfirmation") || options.getBoolean("waitForConfirmation"),
            confirmationTimeout = if (has("confirmationTimeout")) options.getDouble("confirmationTimeout").toLong() else 60_000L,
            waitForCommitmentToSendNextTransaction = has("waitForCommitmentToSendNextTransaction") &&
                options.getBoolean("waitForCommitmentToSendNextTransaction")
        )
    }

    private fun parseCommitment(wire: String): Commitment = when (wire.lowercase()) {
        "processed" -> Commitment.PROCESSED
        "confirmed" -> Commitment.CONFIRMED
        "finalized" -> Commitment.FINALIZED
        else -> Commitment.CONFIRMED
    }

    private fun parseSignInPayload(payload: ReadableMap): MwaSignInPayload {
        fun stringOrNull(key: String): String? =
            if (payload.hasKey(key) && !payload.isNull(key)) payload.getString(key) else null

        val resources = if (payload.hasKey("resources") && !payload.isNull("resources")) {
            val list = payload.getArray("resources")!!
            (0 until list.size()).mapNotNull { list.getString(it) }
        } else null

        return MwaSignInPayload(
            domain = payload.getString("domain")
                ?: error("SignInPayload.domain is required"),
            uri = stringOrNull("uri"),
            statement = stringOrNull("statement"),
            resources = resources,
            version = stringOrNull("version"),
            chainId = stringOrNull("chainId"),
            nonce = stringOrNull("nonce"),
            issuedAt = stringOrNull("issuedAt"),
            expirationTime = stringOrNull("expirationTime"),
            notBefore = stringOrNull("notBefore"),
            requestId = stringOrNull("requestId")
        )
    }

    private fun launchSeedVaultIntent(promise: Promise, intentFactory: (SeedVaultManager) -> Intent?) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("ACTIVITY_ERROR", "Activity not available")
            return
        }
        if (seedVaultPromise != null) {
            promise.reject("CONCURRENT_ERROR", "Seed Vault action already in progress")
            return
        }
        seedVaultPromise = promise
        try {
            val intent = intentFactory(seedVaultManager)
                ?: error("Seed Vault intent builder returned null")
            activity.startActivityForResult(intent, seedVaultRequestCode)
        } catch (e: Exception) {
            seedVaultPromise = null
            promise.reject("LAUNCH_ERROR", e.message, e)
        }
    }

    /**
     * Purpose aliases accepted from JS. Upstream Seed Vault uses
     * integer constants (SeedVaultConstants.PURPOSE_*); we accept both
     * the integer string ("0", "1") and the human-readable alias
     * ("sign_solana_transaction", "sign_transaction") so the JS layer
     * can stay typo-friendly without losing precision.
     */
    private fun parseSeedVaultPurpose(value: String): Int {
        value.toIntOrNull()?.let { return it }
        return when (value.lowercase().replace(Regex("[^a-z0-9]"), "_")) {
            "sign_solana_transaction",
            "sign_transaction",
            "solana" ->
                com.selenus.artemis.seedvault.internal.SeedVaultConstants.PURPOSE_SIGN_SOLANA_TRANSACTION
            else -> error("Unknown Seed Vault purpose: $value")
        }
    }

    private fun readableArrayOfStrings(arr: ReadableArray): List<String> =
        (0 until arr.size()).mapNotNull { arr.getString(it) }

    private fun readableArrayOfBase64Bytes(arr: ReadableArray): List<ByteArray> =
        (0 until arr.size()).mapNotNull { i ->
            arr.getString(i)?.let { Base64.getDecoder().decode(it) }
        }

    private fun base64Array(bytesList: List<ByteArray>): WritableArray {
        val arr = Arguments.createArray()
        bytesList.forEach { arr.pushString(Base64.getEncoder().encodeToString(it)) }
        return arr
    }
}
