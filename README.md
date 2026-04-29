# Artemis Solana SDK

this is the kotlin solana sdk you actually want.

one Kotlin Multiplatform dependency. JSON-RPC, WebSocket subs with reconnect and replay, legacy + v0 transactions, MWA 2.0, Seed Vault, Token-2022, compressed NFTs with DAS failover, Solana Pay, Jupiter, Anchor, Actions. plus a real reliability layer wrapped around all of it.

[![Maven Central](https://img.shields.io/maven-central/v/xyz.selenus/artemis-core?style=flat-square)](https://central.sonatype.com/search?q=xyz.selenus)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)

## Why Artemis

the kotlin/android solana story is fragmented. you ship `mobile-wallet-adapter-clientlib-ktx` for MWA, `seedvault-wallet-sdk` for custody on Saga and Seeker, `solana-kmp` or Sol4k for RPC and transactions, Metaplex KMM for NFT metadata, and you still hand-roll a websocket layer, a retry pipeline, and a DAS client.

five dependencies. five opinions about coroutines. five version matrices. zero shared reliability story.

Artemis is one dependency graph, one coroutine-first surface, one `ArtemisMobile.create()` call for the full stack. every module pulls weight. `artemis-rpc` ships 92 typed JSON-RPC methods with a batch DSL and a real circuit breaker. `artemis-ws` runs a real websocket with deterministic resubscribe on reconnect. `artemis-vtx` drives a simulate + retry + priority-fee transaction pipeline. `artemis-wallet-mwa-android` speaks MWA 2.0 end to end with P-256 association, AES-128-GCM session crypto, HKDF-SHA256 key derivation, and Sign-In With Solana.

if you can't rewrite call sites, the drop-in `interop/artemis-*-compat` shims let you swap Maven coordinates and keep your imports. one dep, not five.

## What you get

* JSON-RPC client. 92 typed Solana methods, batch DSL, blockhash cache, endpoint pool, three-state circuit breaker (`Closed`/`Open`/`HalfOpen`) at [foundation/artemis-rpc/src/jvmMain/kotlin/com/selenus/artemis/rpc/CircuitBreaker.kt](foundation/artemis-rpc/src/jvmMain/kotlin/com/selenus/artemis/rpc/CircuitBreaker.kt). method surface at [foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcApi.kt](foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcApi.kt).
* WebSocket realtime. auto-reconnect, deterministic resubscribe, heartbeat, polling fallback, typed `ConnectionState` StateFlow at [foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt](foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt).
* Transaction engine. blockhash management, simulation, retry pipeline, durable nonce, priority fees, address lookup tables, v0 transactions. one entry point at [foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt](foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt).
* Wallet abstraction. local keypair, raw signer, and adapter signing all collapse into one `WalletSession`. lifecycle handled by `WalletSessionManager` at [mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt](mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt).
* Mobile Wallet Adapter 2.0 for Android. protocol implementation at [mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/](mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/) (P-256, AES-128-GCM, HKDF-SHA256, Base64Url, MWA RPC, MWA WebSocket server, Sign-In With Solana).
* Saga Seed Vault for hardware-backed key custody at [mobile/artemis-seed-vault/](mobile/artemis-seed-vault/).
* DAS query layer. Helius primary, RPC fallback, composite router that does failover with cooldown. files: [HeliusDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/HeliusDas.kt), [RpcFallbackDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/RpcFallbackDas.kt), [CompositeDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt).
* Compressed NFT (Bubblegum) transfers, marketplace preflight, standalone ATA ensurer at [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/).
* Framework event bus. wallet, transaction, realtime, and DAS lifecycle all flow through one `Flow<ArtemisEvent>`. impl at [foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt](foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt).
* Token-2022, Metaplex Token Metadata, MPL Core, Candy Machine v3, Solana Pay, Anchor IDL, Jupiter, and Solana Actions integrations under [ecosystem/](ecosystem/).
* `ArtemisMobile.create()` wires every layer above into one object. drops your app from "import five SDKs" to a single call. source at [ArtemisMobile.kt](mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt).

## Install

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Foundation (KMP)
    implementation("xyz.selenus:artemis-core:2.3.0")
    implementation("xyz.selenus:artemis-rpc:2.3.0")
    implementation("xyz.selenus:artemis-ws:2.3.0")
    implementation("xyz.selenus:artemis-tx:2.3.0")
    implementation("xyz.selenus:artemis-vtx:2.3.0")
    implementation("xyz.selenus:artemis-programs:2.3.0")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.3.0")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.0")
    implementation("xyz.selenus:artemis-seed-vault:2.3.0")

    // NFT, DAS, marketplace
    implementation("xyz.selenus:artemis-cnft:2.3.0")

    // Optional ecosystem modules
    implementation("xyz.selenus:artemis-token2022:2.3.0")
    implementation("xyz.selenus:artemis-jupiter:2.3.0")
}
```

current published version is `2.3.0`. the `version` field in [gradle.properties](gradle.properties) is the source of truth.

## Quick start

### One call, full stack

`ArtemisMobile.create()` builds RPC + MWA wallet + TxEngine + session manager + realtime + DAS + marketplace from a single Activity. that's it.

```kotlin
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico", // absolute HTTPS
    identityName = "My App",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

// Every wallet op goes through the session manager. It connects lazily,
// caches the auth token, retries on session expiration, and fires lifecycle events.
val sig = artemis.sessionManager.withWallet { session ->
    session.sendSol(recipient, 1_000_000_000L)
}
```

constructor lives at [ArtemisMobile.kt:85](mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt#L85).

### Realtime account and signature subscriptions

```kotlin
artemis.realtime.connect()

val handle = artemis.realtime.subscribeAccount(
    pubkey = artemis.session.publicKey.toBase58(),
    commitment = "confirmed"
) { info ->
    println("lamports: ${info.lamports}, slot: ${info.slot}")
}

artemis.realtime.subscribeSignature(txSignature) { confirmed ->
    println("confirmed: $confirmed")
}

handle.close()
```

watch the transport directly:

```kotlin
artemis.realtime.state
    .onEach { state ->
        when (state) {
            is ConnectionState.Connected     -> hideOfflineBanner()
            is ConnectionState.Reconnecting  -> showBanner("reconnecting...")
            is ConnectionState.Closed        -> showBanner("offline")
            else -> Unit
        }
    }
    .launchIn(scope)
```

`ConnectionState` is at [ConnectionState.kt](foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt). every transition carries a monotonic `epoch`, so a fresh connect and a reconnect that lands back on `Connected` are not the same event. yes we built that because we got tired of pretending RPC failures don't happen.

### Framework event bus

every subsystem publishes through one `Flow<ArtemisEvent>`. no per-module listener wiring.

```kotlin
ArtemisEventBus.events
    .onEach { event ->
        when (event) {
            is ArtemisEvent.Wallet.Connected      -> analytics.track("wallet_connected", event.publicKey)
            is ArtemisEvent.Tx.Confirmed          -> refreshBalances()
            is ArtemisEvent.Realtime.StateChanged -> banner.update(event.stateName)
            is ArtemisEvent.Das.ProviderFailover  -> log.warn("DAS failover: ${event.reason}")
            else -> Unit
        }
    }
    .launchIn(scope)
```

want one slice:

```kotlin
ArtemisEventBus.wallet().onEach { ... }.launchIn(scope)
ArtemisEventBus.tx().onEach { ... }.launchIn(scope)
ArtemisEventBus.realtime().onEach { ... }.launchIn(scope)
```

bus is wired into `WalletSessionManager`, `RealtimeEngine`, `TxEngine`, and `CompositeDas` automatically. source at [ArtemisEvent.kt](foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt).

### NFT queries with automatic fallback

```kotlin
val das: ArtemisDas = CompositeDas(
    primary  = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$KEY"),
    fallback = RpcFallbackDas(rpc)
)

val nfts: List<DigitalAsset> = das.assetsByOwner(walletPubkey)
val asset: DigitalAsset?     = das.asset("GdR7...")
val collection               = das.assetsByCollection("CollMint...")
```

when Helius times out, gets rate-limited, or errors, `CompositeDas` re-issues against `RpcFallbackDas`. fallback synthesizes the same `DigitalAsset` view from `getTokenAccountsByOwner` plus the Metaplex metadata PDA. after a failure the primary stays cooled off for 30 seconds, so a burst of calls does not pay the timeout repeatedly. source at [CompositeDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt).

### ATA ensurer for token transfers

```kotlin
val ensurer = AtaEnsurer(rpc)

val resolution = ensurer.resolve(
    payer = wallet.publicKey,
    owner = recipient,
    mint  = usdcMint
)

val instructions = buildList {
    resolution.createIx?.let(::add)         // only added when the ATA does not exist
    add(tokenTransfer(source, resolution.ata, amount))
}
```

batched variant for airdrops uses a single `getMultipleAccounts` call for N destinations. cache invalidates on a 10 second TTL. source at [AtaEnsurer.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt).

### Compressed NFT transfer

```kotlin
val result = artemis.marketplace.transferCnft(
    wallet     = artemis.wallet,
    dasClient  = myDasClient,
    assetId    = "GdR7...",
    merkleTree = Pubkey.fromBase58("tree..."),
    newOwner   = recipientPubkey
)
```

preflight runs by default. validates ownership, frozen state, and (for standard NFTs) destination ATA presence. source at [MarketplaceEngine.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplaceEngine.kt) and [MarketplacePreflight.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplacePreflight.kt).

### Plain transactions

```kotlin
val ix = SystemProgram.transfer(
    from     = wallet.publicKey,
    to       = recipient,
    lamports = 500_000_000L
)

val engine = TxEngine(rpc)
val result = engine.execute(
    instructions = listOf(ix),
    signer       = keypair,
    config       = TxConfig(retries = 3, simulate = true, computeUnitPrice = 1000L)
)
```

pipeline runs `prepare → simulate → sign → send → confirm`, refreshes the blockhash on retry, and emits `ArtemisEvent.Tx.Sent / Confirmed / Failed / Retrying` to the event bus on the way through. source at [TxEngine.kt](foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt).

## Module rings

strict downward-only deps. Foundation never depends on anything above it. Mobile depends only on Foundation. Ecosystem depends on Foundation. Advanced never leaks into the core path.

```text
Ring 1  Foundation   core, rpc, ws, tx, vtx, programs, errors, logging, compute       [KMP]
Ring 2  Mobile       wallet [KMP], wallet-mwa-android [Android], wallet-mwa-walletlib-android,
                     seed-vault [Android]
Ring 3  Ecosystem    token2022, metaplex, mplcore, cnft, candy-machine,
                     solana-pay, anchor, jupiter, actions                              [KMP]
Ring 4  Advanced     privacy, streaming, simulation, batch, scheduler, offline,
                     portfolio, replay, gaming, depin, nlp, intent, universal, preview
Ring 5  Presets      discriminators, nft-compat, tx-presets,
                     candy-machine-presets, presets
Ring 6  Interop      mwa-compat, mwa-clientlib-compat, mwa-walletlib-compat,
                     mwa-common-compat, seedvault-compat, sol4k-compat,
                     solana-kmp-compat, metaplex-android-compat,
                     rpc-core-compat, web3-solana-compat
```

full ring map and dep rules are in [docs/ARCHITECTURE_OVERVIEW.md](docs/ARCHITECTURE_OVERVIEW.md) and [docs/DEPENDENCY_RULES.md](docs/DEPENDENCY_RULES.md).

### Foundation

| Module | What it does |
|--------|--------------|
| `artemis-core` | Pubkey, Keypair, Base58, PDA derivation, Ed25519, SHA-256, Base64. `EventBus`, `ArtemisEvent`, `StateManager`. |
| `artemis-rpc` | 92 Solana JSON-RPC methods, typed result wrappers, batch DSL via `BatchRequestBuilder`, `BlockhashCache`, endpoint pool, three-state `CircuitBreaker`. |
| `artemis-ws` | `SolanaWsClient` (auto-reconnect, deterministic resubscribe, heartbeat, polling fallback), `RealtimeEngine` (typed account/signature/program subscriptions), `ConnectionState` StateFlow. |
| `artemis-tx` | Legacy transaction construction, serialization, signing, durable nonce. |
| `artemis-vtx` | v0 versioned transactions, address lookup tables, `TxEngine` pipeline, retry policy, dynamic priority fee, ALT planner. |
| `artemis-programs` | System (12 instruction builders), Token (10 builders), Associated Token, Compute Budget, Stake (5 builders). |
| `artemis-errors` | Structured Solana error types, on-chain error decoding. |
| `artemis-logging` | Lightweight structured logging. |
| `artemis-compute` | Compute unit estimation, priority fee helpers. |

### Mobile

| Module | What it does |
|--------|--------------|
| `artemis-wallet` | `WalletAdapter` interface, `WalletSession` (Local / Adapter / Raw signing), `WalletSessionManager` with lazy connect, retry, and lifecycle callbacks. |
| `artemis-wallet-mwa-android` | Mobile Wallet Adapter 2.0 **client**. P-256 association, AES-128-GCM session cipher, HKDF-SHA256 key derivation, MWA RPC, websocket transport, Sign-In With Solana. `ArtemisMobile.create()` for one-call setup. |
| `artemis-wallet-mwa-walletlib-android` | Mobile Wallet Adapter 2.0 **wallet-side** runtime (the inverse direction). Drives the wallet's `Scenario`, dispatches inbound JSON-RPC, gates reauthorize by chain, awaits wallet-driven `DeauthorizedEvent.complete()`, enforces sign-messages address-set check. For wallet authors building MWA support; dApps use `artemis-wallet-mwa-android`. |
| `artemis-seed-vault` | Saga Seed Vault integration for hardware-backed key custody. |

### Ecosystem

| Module | What it does |
|--------|--------------|
| `artemis-token2022` | Token-2022 mint and account extensions: transfer fees, interest bearing, non-transferable, permanent delegate, default account state, transfer hook, metadata pointer, group/member pointers, confidential transfers, CPI guard, immutable owner, mint close authority. |
| `artemis-metaplex` | Token Metadata: metadata account, master edition, collection. |
| `artemis-mplcore` | MPL Core (Asset) program. |
| `artemis-cnft` | Bubblegum (compressed NFTs) instructions and transfers, `ArtemisDas` interface, `HeliusDas`, `RpcFallbackDas`, `CompositeDas`, `MarketplaceEngine`, `MarketplacePreflight`, `AtaEnsurer`. |
| `artemis-candy-machine` | Candy Machine v3 mintV2 builder, guard accounts planner, manifest parser. |
| `artemis-solana-pay` | Solana Pay URL parsing, `SolanaPayManager`. |
| `artemis-anchor` | Anchor IDL parsing, Borsh serializer, type-safe `AnchorProgram` client. |
| `artemis-jupiter` | `JupiterClient` quote / swap, route building. |
| `artemis-actions` | Solana Actions and Blinks fetch / execute. |

### Advanced (status varies)

these ship in the repo but maturity varies. `gaming`, `intent`, `privacy`, `portfolio`, and `offline` have substantial implementations. the rest define interfaces and primitive helpers, intended as opt-in starting points, not drop-in production layers. none of these are required for the core mobile path.

| Module | Status | Source |
|--------|--------|--------|
| `artemis-privacy` | Working primitives | StealthAddress, EncryptedMemo, ConfidentialTransfer, LightProtocolClient, RingSignature, ShamirSecretSharing |
| `artemis-portfolio` | Working primitives | Portfolio fetcher, snapshot, balance providers |
| `artemis-offline` | Working primitives | Offline queue, persistent store, retry |
| `artemis-gaming` | Working primitives | Session keys, VRF wrappers, state proof helpers (18 files) |
| `artemis-intent` | Working primitives | Per-program intent decoders (11 files) |
| `artemis-streaming` | Interface + reference impl | `ZeroCopyAccountStream` exposes a `WebSocketClient` interface that the caller wires to `artemis-ws` |
| `artemis-simulation` | Interface + reference impl | `PredictiveSimulator` expects a caller-provided `RpcAdapter` |
| `artemis-batch` | Helpers | `TransactionBatchEngine` |
| `artemis-scheduler` | Helpers | `NetworkState`, `TransactionScheduler` |
| `artemis-replay` | Helpers | Transaction replay primitives |
| `artemis-depin` | Helpers | Device attestation primitives |
| `artemis-nlp` | Helpers | Token / instruction entity resolver |
| `artemis-universal` | Helpers | IDL-less program discovery |
| `artemis-preview` | Helpers | Tx preview rendering |

if a module says "Helpers", it exists for app teams that want a starting point and intend to extend it. Foundation, Mobile, and Ecosystem are the production surface.

### Interop (drop-in shims)

source-compatible shims that publish the upstream package + class FQNs. swap your Maven coordinates and existing import lines keep compiling. each module pins the upstream version it targets (see `extra["upstream.version"]` in the module's `build.gradle.kts`). CI's `verifyApiSnapshots` task fails the build when the public surface drifts from the committed snapshot.

| Module | Replaces (artifact) | Upstream pin |
|--------|---------------------|--------------|
| `artemis-mwa-compat` | `com.solana.mobilewalletadapter:clientlib-ktx` | 1.4.3 |
| `artemis-mwa-clientlib-compat` | `com.solana.mobilewalletadapter:clientlib` | 1.4.3 |
| `artemis-mwa-walletlib-compat` | `com.solana.mobilewalletadapter:walletlib` | 1.4.3 |
| `artemis-mwa-common-compat` | `com.solana.mobilewalletadapter:common` | 1.4.3 |
| `artemis-seedvault-compat` | `com.solanamobile:seedvault-wallet-sdk` | 0.4.0 |
| `artemis-sol4k-compat` | `org.sol4k:sol4k` | 0.7.0 |
| `artemis-solana-kmp-compat` | `foundation.metaplex:solana-kmp` | main@2024-06-05 (upstream dormant) |
| `artemis-metaplex-android-compat` | `com.metaplex.lib:lib` | main@2024-04-06 (upstream dormant) |
| `artemis-rpc-core-compat` | `com.solana:rpc-core` | main@2026-01-09 |
| `artemis-web3-solana-compat` | `com.solana:web3-solana` (Funkatronics) | main@2025-08 |

## What this replaces

two adoption modes. pick one up front.

### Artemis-native ready

the native surface (`ArtemisMobile.create`, `WalletSession`, `TxEngine`, `RpcApi`, `RealtimeEngine`, `ArtemisDas`, `MarketplaceEngine`) is ready to use today. every capability marked **Verified** in [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md) is backed by a test that fails if the feature regresses.

### SMS-drop-in ready

keep your existing imports. swap the Maven coordinates to the Artemis compat artifacts under `interop/artemis-*-compat`. this track is **Verified** for the MWA client (ktx + non-ktx), MWA walletlib drop-in (chain-gated reauthorize, wallet-driven deauthorize, sign-messages address check, typed exceptions), Seed Vault static surface, sol4k 0.7.0 surface (incl. Token-2022 instructions and the upstream `RpcException` data-class shape), and typed result classes. the rest is **Partial** or **In Progress**. matrix in [PARITY_MATRIX.md](docs/PARITY_MATRIX.md) is the source of truth per capability.

Replaces:

* `org.sol4k:sol4k` (0.7.0): JVM Solana primitives, Connection, transactions, instruction builders incl. Token-2022 → `artemis-sol4k-compat`
* `foundation.metaplex:solana-kmp` (dormant 2024-06): RPC, public keys, transactions, ReadApi → `artemis-solana-kmp-compat`
* `com.solana.mobilewalletadapter:clientlib-ktx` (1.4.3): MWA 2.0 dApp surface → `artemis-mwa-compat`
* `com.solana.mobilewalletadapter:clientlib` (1.4.3): low-level MWA client → `artemis-mwa-clientlib-compat`
* `com.solana.mobilewalletadapter:walletlib` (1.4.3): wallet-side MWA scenario, authorization, protocol → `artemis-mwa-walletlib-compat`
* `com.solana.mobilewalletadapter:common` (1.4.3): protocol contract, SIWS, NotifyOnComplete → `artemis-mwa-common-compat`
* `com.solanamobile:seedvault-wallet-sdk` (0.4.0): Seed Vault static surface → `artemis-seedvault-compat`
* `com.metaplex.lib:lib` (Metaplex Android, dormant 2024-04): partial parity for the most-used 60% (NFT read, DAS, identity drivers; auctions and full mutation surface still pending) → `artemis-metaplex-android-compat`
* `com.solana:rpc-core` (2026-01-09): JSON-RPC envelope types, SolanaRpcClient → `artemis-rpc-core-compat`
* `com.solana:web3-solana` (Funkatronics): SolanaPublicKey, Transaction, Builder, instruction primitives → `artemis-web3-solana-compat`

each compat module pins the exact upstream version it targets in its `build.gradle.kts` (`extra["upstream.version"]`). CI's `verifyApiSnapshots` job runs `./gradlew dumpApi` and fails when the public surface drifts. intentional changes update both the snapshot under `interop/<module>/api/<module>.api` and the upstream pin together.

status vocabulary (`Verified` / `In Progress` / `Partial` / `Experimental` / `Planned`) is defined at the top of [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md). migration walkthrough with both tracks: [docs/REPLACE_SOLANA_MOBILE_STACK.md](docs/REPLACE_SOLANA_MOBILE_STACK.md).

## Reliability features

these are why this SDK is meant for production mobile work, not just a wrapper around RPC. yeah we built a circuit breaker because we got tired of pretending RPC failures don't happen.

| Feature | Where | What it does |
|---------|-------|--------------|
| Blockhash cache | [BlockhashCache.kt](foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/BlockhashCache.kt) | TTL-based blockhash cache, refresh on retry, expiration check before use |
| Retry pipeline | [RetryPipeline.kt](foundation/artemis-tx/src/commonMain/kotlin/com/selenus/artemis/tx/RetryPipeline.kt) | Exponential backoff with jitter, retry on RPC failure, retry on blockhash expired, classified errors |
| WS reconnect + replay | [SolanaWsClient.kt](foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/SolanaWsClient.kt), [RealtimeEngine.kt](foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/RealtimeEngine.kt) | Auto-reconnect, deterministic resubscribe order, heartbeat, polling fallback, endpoint rotation |
| Connection state machine | [ConnectionState.kt](foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt) | Typed `Idle / Connecting / Connected / Reconnecting / Closed` with monotonic epoch counter, surfaced as `StateFlow` |
| Wallet session lifecycle | [WalletSessionManager.kt](mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt) | Lazy connect, auth token caching, `withWallet { }` retry on session expiration, `onDisconnect / onAccountChanged / onSessionExpired` callbacks |
| DAS failover | [CompositeDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt) | Primary + fallback DAS routing with cooldown memo so callers do not pay primary timeouts in a burst |
| Marketplace preflight | [MarketplacePreflight.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplacePreflight.kt) | Validates ownership, frozen state, DAS record, destination ATA, and returns prepend instructions for missing ATAs |
| ATA ensurer | [AtaEnsurer.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt) | Single + batched ATA resolution with positive / negative cache and Token-2022 support |
| Framework event bus | [ArtemisEvent.kt](foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt) | Unified `ArtemisEvent` stream from wallet, tx, realtime, and DAS |
| Endpoint pool + circuit breaker | [RpcEndpointPool.kt](foundation/artemis-rpc/src/jvmMain/kotlin/com/selenus/artemis/rpc/RpcEndpointPool.kt), [CircuitBreaker.kt](foundation/artemis-rpc/src/jvmMain/kotlin/com/selenus/artemis/rpc/CircuitBreaker.kt) | RPC endpoint rotation with health tracking; standalone three-state `CircuitBreaker` (`Closed`/`Open`/`HalfOpen`) wrappable around any call |

## Build and test

```bash
./gradlew build
./gradlew test
```

run a single module:

```bash
./gradlew :artemis-vtx:jvmTest
./gradlew :artemis-ws:jvmTest
./gradlew :artemis-cnft:jvmTest
```

Android library modules need the Android SDK on the build machine (set `ANDROID_HOME` or `sdk.dir` in `local.properties`). pure JVM modules build without it.

the optional Android sample is excluded from the default build:

```bash
./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
```

devnet test runner is at [run-devnet-tests.sh](run-devnet-tests.sh). it exercises the JVM test path against a real devnet keypair via the integration test module at [testing/artemis-devnet-tests/](testing/artemis-devnet-tests/).

## Documentation

| Document | Covers |
|----------|--------|
| [docs/ARCHITECTURE_OVERVIEW.md](docs/ARCHITECTURE_OVERVIEW.md) | Ring structure, dependency rules, module hierarchy |
| [docs/MODULE_MAP.md](docs/MODULE_MAP.md) | Every module with purpose and adoption context |
| [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md) | Side-by-side feature comparison vs solana-kmp, sol4k, Solana Mobile SDK, Metaplex KMM |
| [docs/REPLACE_SOLANA_MOBILE_STACK.md](docs/REPLACE_SOLANA_MOBILE_STACK.md) | Step-by-step migration from the official Solana Mobile Stack |
| [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md) | Release-label checklist and the CI gates that back each claim |
| [docs/adr/0001-engine-architecture.md](docs/adr/0001-engine-architecture.md) | Engine / Provider / Adapter / Compat layering rules |
| [docs/MOBILE_APP_GUIDE.md](docs/MOBILE_APP_GUIDE.md) | Android mobile app integration walkthrough |
| [docs/DEPENDENCY_RULES.md](docs/DEPENDENCY_RULES.md) | What can depend on what, and why |
| [docs/ADOPTION_BUNDLES.md](docs/ADOPTION_BUNDLES.md) | Recommended dependency sets per use case |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [CHANGELOG.md](CHANGELOG.md) | Release history |

## License

Apache License 2.0. see [LICENSE](LICENSE).

Maintained by [Bluefoot Labs](https://bluefootlabs.com).
