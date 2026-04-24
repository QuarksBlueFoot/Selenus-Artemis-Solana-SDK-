# Mobile app guide

End-to-end walkthrough for building an Android Solana app on Artemis 2.3.0. The guide assumes you already know Kotlin, Coroutines, and Compose. Every code sample maps to a real API in the repo with a file path you can click into.

For the architectural ring map, see [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md). For migration notes from the official Solana Mobile SDK, see [REPLACE_SOLANA_MOBILE_STACK.md](REPLACE_SOLANA_MOBILE_STACK.md).

## 1. Add dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.3.0")
    implementation("xyz.selenus:artemis-rpc:2.3.0")
    implementation("xyz.selenus:artemis-ws:2.3.0")
    implementation("xyz.selenus:artemis-tx:2.3.0")
    implementation("xyz.selenus:artemis-vtx:2.3.0")
    implementation("xyz.selenus:artemis-programs:2.3.0")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.3.0")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.0")
    implementation("xyz.selenus:artemis-seed-vault:2.3.0") // Saga only

    // NFT, DAS, marketplace
    implementation("xyz.selenus:artemis-cnft:2.3.0")

    // Optional
    implementation("xyz.selenus:artemis-jupiter:2.3.0")
    implementation("xyz.selenus:artemis-actions:2.3.0")
    implementation("xyz.selenus:artemis-anchor:2.3.0")
    implementation("xyz.selenus:artemis-metaplex:2.3.0")
}
```

The current published version is `2.3.0`. The `version` field in [../gradle.properties](../gradle.properties) is the source of truth.

## 2. Initialize the stack with one call

`ArtemisMobile.create()` builds the entire mobile stack: RPC client, MWA wallet adapter, transaction engine, session manager, websocket realtime, optional DAS, and marketplace. Source at [../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt).

```kotlin
class MyActivity : ComponentActivity() {

    private lateinit var artemis: ArtemisMobile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        artemis = ArtemisMobile.create(
            activity     = this,
            identityUri  = Uri.parse("https://myapp.example.com"),
            iconPath     = "https://myapp.example.com/favicon.ico", // absolute HTTPS
            identityName = "My App",
            rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=$KEY",
            wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=$KEY",
            dasUrl       = "https://mainnet.helius-rpc.com/?api-key=$KEY"
        )

        setContent { MyApp(artemis) }
    }
}
```

The `dasUrl` parameter is optional. Pass `null` and `artemis.das` will be `null`. The marketplace and DAS query helpers degrade gracefully when DAS is unavailable.

The constructor accepts a `chain` parameter that defaults to `"solana:mainnet"`. Pass `"solana:devnet"` for devnet builds.

## 3. Connect a wallet

`artemis.wallet` is the raw `MwaWalletAdapter`. `artemis.sessionManager` wraps it with lazy connect, auth token caching, and lifecycle callbacks. Source: [../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt](../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt).

```kotlin
// React to lifecycle events
artemis.sessionManager.onDisconnect { showConnectButton() }
artemis.sessionManager.onAccountChanged { newKey -> refreshUi(newKey) }
artemis.sessionManager.onSessionExpired { Log.i("Wallet", "session expired, reconnecting next call") }

// Trigger a connect (or use withWallet { } to connect lazily on first use)
viewModelScope.launch {
    val session = artemis.sessionManager.get()
    val pubkey  = session.publicKey
}
```

The `withWallet { }` helper is the cleanest pattern for one-shot operations. It connects on first call, retries once on session expiration, and propagates the result.

```kotlin
val signature = artemis.sessionManager.withWallet { session ->
    session.sendSol(recipient, 1_000_000_000L)
}
```

## 4. Send a transaction

`WalletSession` exposes `send(ix)`, `sendBatch(ixs)`, `sendSol`, and `sendToken`. Each routes through the `TxEngine` pipeline so blockhash, simulation, signing, sending, and confirmation are handled automatically. Source: [../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSession.kt](../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSession.kt).

```kotlin
// SOL transfer
val result = artemis.session.sendSol(recipient, 1_000_000_000L)

// Single instruction
val ix     = SystemProgram.transfer(artemis.session.publicKey, recipient, 500_000_000L)
val result = artemis.session.send(ix)

// Multiple instructions in one transaction
val result = artemis.session.sendBatch(listOf(ix1, ix2, ix3))
```

For lower-level control, drop down to the engine:

```kotlin
val engine = artemis.txEngine
val result = engine.execute(
    instructions = listOf(transferIx),
    feePayer     = artemis.session.publicKey,
    externalSign = { unsignedTx -> artemis.wallet.signMessage(unsignedTx, SignTxRequest("signTransaction")) },
    config       = TxConfig(retries = 3, simulate = true, computeUnitPrice = 1000L)
)
```

The pipeline emits `ArtemisEvent.Tx.Sent / Confirmed / Failed / Retrying` on the framework event bus on the way through. Source: [../foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt](../foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt).

## 5. Realtime account subscriptions

`artemis.realtime` is a `RealtimeEngine` instance pre-wired to your `wsUrl`. Subscriptions survive reconnects and the engine rotates through endpoint failures. Source: [../foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/RealtimeEngine.kt](../foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/RealtimeEngine.kt).

```kotlin
artemis.realtime.connect()

val handle = artemis.realtime.subscribeAccount(
    pubkey     = artemis.session.publicKey.toBase58(),
    commitment = "confirmed"
) { info ->
    Log.d("Realtime", "lamports=${info.lamports} slot=${info.slot}")
}

// Confirmation watcher (auto-removes after first notification)
artemis.realtime.subscribeSignature(txSignature) { confirmed ->
    Log.d("Realtime", "confirmed=$confirmed")
}

// Unsubscribe
handle.close()
```

Bind the typed transport state to a Compose banner:

```kotlin
@Composable
fun ConnectionBanner(realtime: RealtimeEngine) {
    val state by realtime.state.collectAsState()

    when (val s = state) {
        is ConnectionState.Connected     -> Spacer(Modifier.height(0.dp))
        is ConnectionState.Connecting    -> Banner("connecting")
        is ConnectionState.Reconnecting  -> Banner("reconnecting (attempt ${s.attempt})")
        is ConnectionState.Closed        -> Banner("offline")
        is ConnectionState.Idle          -> Spacer(Modifier.height(0.dp))
    }
}
```

`ConnectionState` is at [../foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt](../foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt).

## 6. Framework event bus

Every subsystem (wallet, tx, realtime, DAS) publishes through one `Flow<ArtemisEvent>`. Bind it once at the app level instead of wiring per-module listeners. Source: [../foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt](../foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt).

```kotlin
class AppEventCollector(private val scope: CoroutineScope) {

    fun start() {
        ArtemisEventBus.events
            .onEach { event ->
                when (event) {
                    is ArtemisEvent.Wallet.Connected      -> analytics.track("wallet_connected", event.publicKey)
                    is ArtemisEvent.Wallet.Disconnected   -> analytics.track("wallet_disconnected")
                    is ArtemisEvent.Tx.Confirmed          -> snackbar("transaction confirmed: ${event.signature}")
                    is ArtemisEvent.Tx.Failed             -> snackbar("transaction failed: ${event.message}")
                    is ArtemisEvent.Realtime.StateChanged -> banner.update(event.stateName)
                    is ArtemisEvent.Das.ProviderFailover  -> Log.w("DAS", "fallback active: ${event.reason}")
                    else -> Unit
                }
            }
            .launchIn(scope)
    }
}
```

For one slice at a time use the typed accessors:

```kotlin
ArtemisEventBus.tx().onEach { ... }.launchIn(scope)
ArtemisEventBus.wallet().onEach { ... }.launchIn(scope)
ArtemisEventBus.realtime().onEach { ... }.launchIn(scope)
```

## 7. NFT queries via DAS

`artemis.das` is non-null when you passed `dasUrl` to `ArtemisMobile.create()`. The default uses `HeliusDas` against the Helius DAS API. Source: [../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/HeliusDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/HeliusDas.kt).

```kotlin
val nfts: List<DigitalAsset> = artemis.das?.assetsByOwner(walletPubkey) ?: emptyList()
val asset: DigitalAsset?     = artemis.das?.asset("GdR7assetId...")
val collection               = artemis.das?.assetsByCollection("CollMint...")
```

For production resilience wrap the primary DAS in `CompositeDas`, which falls back to `RpcFallbackDas` on Helius failure and applies a 30 second cooldown so a burst of calls does not pay the timeout repeatedly. Source: [../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt).

```kotlin
val das: ArtemisDas = CompositeDas(
    primary  = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$KEY"),
    fallback = RpcFallbackDas(artemis.rpc)
)
```

## 8. Compressed NFT transfer

```kotlin
val result = artemis.marketplace.transferCnft(
    wallet     = artemis.wallet,
    dasClient  = myDasClient,
    assetId    = "GdR7assetId...",
    merkleTree = Pubkey.fromBase58("tree..."),
    newOwner   = recipientPubkey
)
println("signature=${result.signature} confirmed=${result.confirmed}")
```

Preflight runs by default and validates ownership, frozen state, DAS record, and (for standard SPL NFTs) destination ATA presence. The validation result includes a `prependIxs: List<Instruction>` field with a create-ATA instruction when the destination ATA does not exist. Source: [../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplacePreflight.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplacePreflight.kt).

## 9. Token transfers with ATA handling

```kotlin
val ensurer    = AtaEnsurer(artemis.rpc)
val resolution = ensurer.resolve(
    payer = artemis.session.publicKey,
    owner = recipient,
    mint  = usdcMint
)

val instructions = buildList {
    resolution.createIx?.let(::add)
    add(tokenTransfer(sourceAta, resolution.ata, amount))
}

artemis.session.sendBatch(instructions)
```

Batched variant for airdrops uses one `getMultipleAccounts` call for N destinations and caches results for 10 seconds. Source: [../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt).

## 10. Manifest, deep links, and Sign-In With Solana

### Manifest entries for MWA

The MWA spec requires your app to declare its identity URI and icon. Make sure your `AndroidManifest.xml` has internet permission and that your identity URI is reachable over HTTPS.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Solana Pay deep link handler:

```xml
<activity android:name=".PaymentActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="solana" />
    </intent-filter>
</activity>
```

### Sign-In With Solana

The Sign-In With Solana flow is implemented in the MWA protocol layer at [../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/MwaSignIn.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/MwaSignIn.kt).

## 11. Lifecycle and cleanup

`ArtemisMobile` does not own an Activity-scoped lifecycle. Wire cleanup at the appropriate scope:

```kotlin
class MainViewModel(private val artemis: ArtemisMobile) : ViewModel() {

    init {
        artemis.realtime.connect()
    }

    override fun onCleared() {
        super.onCleared()
        artemis.realtime.close()
    }
}
```

Foreground-only realtime is a reasonable default. Mobile devices throttle background websocket traffic aggressively, so reconnecting on resume is usually preferred over keeping the socket open across activity death.

## 12. Testing

The integration test module at [../testing/artemis-devnet-tests/](../testing/artemis-devnet-tests/) is a working reference for setting up a session against devnet. The script at [../run-devnet-tests.sh](../run-devnet-tests.sh) provisions a keypair if you do not have one and runs the JUnit suite.

For unit tests, prefer the in-repo `WalletSession.local(keypair, txEngine)` constructor so you can drive the pipeline without a real wallet adapter:

```kotlin
val keypair = Keypair.random()
val session = WalletSession.local(keypair, txEngine = TxEngine(rpc))
val result  = session.sendSol(recipient, 1_000_000L)
```

## License

Apache License 2.0. See [../LICENSE](../LICENSE).
