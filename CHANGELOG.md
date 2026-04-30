# Changelog

## 2.3.0 (2026-04-24)

Major React Native bridge rewrite, full upstream Mobile Wallet Adapter 2.0
parity, and a superset of the official Solana Mobile RN surface.

### Added

- **Android RN bridge full MWA surface.** Every MWA 2.0 verb exposed as a
  native method with structured `WritableMap` / `WritableArray` results.
  No JSON stringification across the bridge. Methods: `connect`,
  `connectWithFeatures`, `connectWithSignIn`, `reauthorize`, `deauthorize`,
  `getCapabilities`, `cloneAuthorization`, `signTransaction(s)`,
  `signAndSendTransaction(s)` with per-slot batch results,
  `signMessage(s)`, `signMessagesDetached`.
- **RN realtime.** `Realtime.onAccountChanged`, `Realtime.onSignatureConfirmed`,
  and `Realtime.onState` stream `ConnectionState` transitions with epoch
  counters, endpoint, attempt, and reason fields. Backed by
  `RealtimeEngine` with deterministic resubscribe and endpoint rotation.
- **RN RPC expansion.** `getAccountInfo`, `getMultipleAccounts`,
  `getTokenAccountsByOwner`, `simulateTransaction`, `sendRawTransaction`,
  `getSignatureStatuses`, `getSlot`, `getBlockHeight`,
  `getMinimumBalanceForRentExemption` wired to `RpcApi`.
- **RN DAS.** `dasAssetsByOwner`, `dasAsset`, `dasAssetsByCollection`.
  Helius primary with RPC fallback via `CompositeDas` + `RpcFallbackDas`
  when no DAS URL is configured.
- **RN compute budget.** `computeBudgetSetUnitLimit(units)` and
  `computeBudgetSetUnitPrice(microLamports)` return structured
  `InstructionShape` objects ready to fold into `@solana/web3.js`
  transactions.
- **RN PDA + ATA.** `findProgramAddress(seedsBase64, programId)` returns
  `{ address, bump }` through `Pda.findProgramAddress`;
  `getAssociatedTokenAddress(owner, mint, tokenProgram?)` matches the
  `@solana/spl-token` shape.
- **RN cross-platform crypto.** `base64ToBase58`, `base58ToBase64`,
  `isValidBase58`, `isValidSolanaPubkey`, `isValidSolanaSignature`,
  `base58EncodeCheck`, `base58DecodeCheck`, `sha256`,
  `cryptoGenerateKeypair`, `cryptoSign`, `cryptoVerify` on both Android
  and iOS. Previous revision had these on iOS only.
- **`transact(wallet, block)` helper** matching
  `@solana-mobile/mobile-wallet-adapter-protocol-mobile` shape. Opens a
  session, runs the block, and disconnects even if the block throws.
- **iOS Swift module rewrite** from broken static methods to proper RN
  instance methods with promise resolvers. `.m` bridge file realigned.
- **RN smoke harness** at `tests/RnSmokeApp.tsx` that drives every MWA
  verb against a live wallet with no mocks.

### Changed

- RN npm package `@selenus/artemis-solana-sdk` and its Android native
  dependencies now pin to the same version through a single
  `artemisVersion` Gradle ext property. No more drift between the npm
  version and the native Maven coordinates.
- Seed Vault auth tokens flow as `string` end to end across TS
  declarations, JS wrapper, Android bridge, and upstream contract.
- `signTransaction` on the bridge routes through
  `MwaWalletAdapter.signMessages` which maps to the wallet's
  `sign_transactions` RPC. Previous revision used `signArbitraryMessage`
  which produced detached signatures and broke deserialize() calls.
- Low-level `MobileWalletAdapterClient` bridge=null path now throws a
  typed `SessionNotReadyException` with actionable remediation text.
- Every compat `AuthorizationResult` and nested `AuthorizedAccount` /
  `SignInResult` type carries full structural equality and hashing
  covering every MWA 2.0 field. Legacy flat `publicKey` / `accountLabel`
  fields marked `@Deprecated`.
- `TransactionBatchResult` + `TransactionItemResult` with init-time
  invariants: `results.size == input.size`, `results[i].index == i`,
  `success XOR error`.
- Scenario ownership inverted. Keypair, transport, and client all flow
  through `SessionEngine` / `SecureTransport` / constructor injection.
  `MwaAssociationKeys` moved behind `SessionEngine` and unreachable from
  Scenario.

### Fixed

- RN package version (`2.3.0`) aligned with Android dependency version.
- Podspec renamed to `ArtemisSolanaSDK`, iOS marketed as utility layer.
- `ArtemisModule.kt` duplicated / malformed `SolanaPayUri.Request` block
  removed; file structure cleaned and sorted.
- Readiness state on non-Android platforms returns
  `WalletReadyState.Unsupported` instead of always-`Installed`.
- Every em-dash purged from repo source and docs; "v0-project",
  "LunaSDK", and stale versions scrubbed.

## Unreleased

Production hardening pass. Closes the remaining reliability gaps from the v68 stack: explicit websocket state machine, DAS failover, standalone ATA handling, and a single framework event surface across every subsystem.

### Added

#### `ConnectionState` and observable transport state (`artemis-ws`)

Five-state sealed class at [foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt](foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt): `Idle`, `Connecting`, `Connected`, `Reconnecting`, `Closed`. Every state carries a monotonic `epoch` so observers can tell a fresh connect from a reconnect that landed back on `Connected`.

`RealtimeEngine` now exposes `state: StateFlow<ConnectionState>` and emits transitions on `connect()`, `reconnect()`, `close()`, and from the underlying transport events.

```kotlin
artemis.realtime.state
    .onEach { state ->
        when (state) {
            is ConnectionState.Connected     -> hideOfflineBanner()
            is ConnectionState.Reconnecting  -> showBanner("reconnecting")
            is ConnectionState.Closed        -> showBanner("offline")
            else -> Unit
        }
    }
    .launchIn(scope)
```

#### `RpcFallbackDas` and `CompositeDas` (`artemis-cnft`)

DAS resilience layer for production apps that cannot tolerate Helius downtime.

`RpcFallbackDas` at [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/RpcFallbackDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/RpcFallbackDas.kt) synthesizes the same `DigitalAsset` view from vanilla RPC: `getTokenAccountsByOwner` filtered to NFT semantics (amount == 1, decimals == 0), Metaplex metadata PDA derivation, `getAccountInfo`, and Borsh-decoded metadata.

`CompositeDas` at [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt) routes every query to the primary first, falls back on failure, and applies a 30-second cooldown so a burst of calls does not pay the primary timeout repeatedly. Emits `ArtemisEvent.Das.ProviderFailover` for telemetry.

```kotlin
val das: ArtemisDas = CompositeDas(
    primary  = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$KEY"),
    fallback = RpcFallbackDas(rpc)
)
```

#### `AtaEnsurer` standalone utility (`artemis-cnft`)

Idempotent associated token account resolution and creation at [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt).

`resolve()` returns the destination ATA address plus a create instruction when the account does not exist on-chain. `resolveBatch()` does N targets in a single `getMultipleAccounts` call. Positive and negative caches keep a 10-second TTL so back-to-back transfers do not re-query. Token-2022 is supported via the `tokenProgram` parameter.

```kotlin
val ensurer = AtaEnsurer(rpc)
val resolution = ensurer.resolve(payer = wallet.publicKey, owner = recipient, mint = usdcMint)

val instructions = buildList {
    resolution.createIx?.let(::add)
    add(tokenTransfer(source, resolution.ata, amount))
}
```

`MarketplacePreflight.validateNftTransfer` now accepts an optional `recipient` parameter and returns `prependIxs` containing the ATA create instruction when needed.

#### `ArtemisEvent` framework event bus (`artemis-core`)

Single `Flow<ArtemisEvent>` for every subsystem at [foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt](foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt).

Sealed class hierarchy: `Wallet.Connected / Disconnected / SessionExpired / AccountChanged`, `Tx.Sent / Confirmed / Failed / Retrying`, `Realtime.StateChanged / AccountUpdated / SignatureObserved`, `Das.ProviderFailover`, plus `Custom` for app-defined events. Every event carries `timestamp` and a stable `Source` enum.

`ArtemisEventBus` exposes filtered streams: `wallet()`, `tx()`, `realtime()`, `das()`, `bySource(source)`. Drop-oldest semantics so the bus never backpressures emitters.

`WalletSessionManager`, `RealtimeEngine`, `TxEngine` (via `TxPipeline`), and `CompositeDas` are wired into the bus automatically. Apps observe one stream:

```kotlin
ArtemisEventBus.events
    .onEach { event ->
        when (event) {
            is ArtemisEvent.Wallet.Connected      -> analytics.track("wallet_connected", event.publicKey)
            is ArtemisEvent.Tx.Confirmed          -> refreshBalances()
            is ArtemisEvent.Realtime.StateChanged -> banner.update(event.stateName)
            is ArtemisEvent.Das.ProviderFailover  -> log.warn(event.reason)
            else -> Unit
        }
    }
    .launchIn(scope)
```

### Changed

- `WalletSessionManager` constructor now takes optional `publishToBus` and `walletName` parameters. Default is `publishToBus = true`. Existing callers compile unchanged.
- `RealtimeEngine` constructor now takes optional `publishToBus` parameter (default `true`). Existing callers compile unchanged.
- `MarketplacePreflight.validateNftTransfer` signature extended with optional `recipient`, `payer`, and `tokenProgram` parameters. The old single-argument call site continues to work.
- `MarketplacePreflight.PreflightResult` now includes `prependIxs: List<Instruction>` (default empty).
- `artemis-cnft` `commonMain` now depends on `:artemis-programs` and `:artemis-nft-compat` for ATA derivation and metadata parsing.

### Tests

- New `ConnectionStateTest` at [foundation/artemis-ws/src/jvmTest/kotlin/com/selenus/artemis/ws/ConnectionStateTest.kt](foundation/artemis-ws/src/jvmTest/kotlin/com/selenus/artemis/ws/ConnectionStateTest.kt) covers state transitions and epoch ordering.
- New `ArtemisEventBusTest` at [foundation/artemis-core/src/jvmTest/kotlin/com/selenus/artemis/core/ArtemisEventBusTest.kt](foundation/artemis-core/src/jvmTest/kotlin/com/selenus/artemis/core/ArtemisEventBusTest.kt) covers subsystem stream filtering and emission ordering.

### Verification

Affected modules compile clean and all jvmTest suites pass: `artemis-core`, `artemis-ws`, `artemis-vtx`, `artemis-wallet`, `artemis-cnft`. Downstream modules (`artemis-metaplex`, `artemis-jupiter`, `artemis-actions`, `artemis-anchor`, `artemis-solana-pay`, `artemis-token2022`, `artemis-candy-machine`, `artemis-mplcore`) are binary-compatible and compile unchanged.

---

## 2.2.0 - April 14, 2026

Adds real-time subscriptions, Digital Asset Standard (DAS) queries, and marketplace primitives directly to the core mobile stack.

### New: RealtimeEngine (artemis-ws)

High-level account and signature subscription manager built on `SolanaWsClient`. Delivers typed `AccountNotification` callbacks without raw WebSocket bookkeeping.

```kotlin
val realtime = RealtimeEngine(endpoints = listOf("wss://atlas-mainnet.helius-rpc.com/?api-key=KEY"))
realtime.connect()

val handle = realtime.subscribeAccount(pubkey) { info ->
    println("lamports: ${info.lamports}, slot: ${info.slot}")
}

realtime.subscribeSignature(signature) { confirmed ->
    println("confirmed: $confirmed")
}
```

- `subscribeAccount(pubkey, commitment, callback)` returns `SubscriptionHandle`
- `subscribeSignature(sig, commitment, callback)` auto-removes on first notification
- `subscribeProgram(programId, commitment)` delegates to raw `SolanaWsClient`
- `close()` shuts down the WebSocket and clears all callbacks

### New: ArtemisDas + HeliusDas (artemis-cnft)

Typed DAS (Digital Asset Standard) interface for NFT/cNFT asset queries.

```kotlin
val das = HeliusDas("https://mainnet.helius-rpc.com/?api-key=KEY")

val nfts: List<DigitalAsset> = das.assetsByOwner(walletPubkey)
val asset: DigitalAsset? = das.asset("GdR7assetId...")
val collection: List<DigitalAsset> = das.assetsByCollection("CollMint...")
```

`DigitalAsset` carries: `id`, `name`, `symbol`, `uri`, `owner`, `royaltyBasisPoints`, `isCompressed`, `frozen`, `collectionAddress`, `collectionVerified`.

### New: MarketplaceEngine (artemis-cnft)

High-level NFT transfer and protocol execution engine.

```kotlin
val marketplace = MarketplaceEngine(rpc, txEngine, das)

// Fetch assets via DAS
val assets = marketplace.getAssetsByOwner(walletPubkey)

// Transfer a compressed NFT via Bubblegum
val result = marketplace.transferCnft(
    wallet     = walletAdapter,
    dasClient  = dasClient,
    assetId    = "GdR7...",
    merkleTree = Pubkey.fromBase58("tree..."),
    newOwner   = recipientPubkey
)

// Execute arbitrary protocol instructions (Tensor, etc.)
val result = marketplace.executeInstructions(wallet, instructions)
```

### Updated: ArtemisMobile (artemis-wallet-mwa-android)

`ArtemisMobile.create()` now wires the full v68 stack in one call.

```kotlin
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.com"),
    iconPath     = "https://myapp.com/favicon.ico",
    identityName = "MyApp",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=KEY"
)

// rpc, wallet, txEngine, session, realtime, das, marketplace all ready
artemis.realtime.connect()
val nfts = artemis.das?.assetsByOwner(artemis.session.publicKey)
```

### Build changes

- `artemis-cnft`: added `:artemis-wallet` to `commonMain` dependencies
- `artemis-wallet-mwa-android`: added `:artemis-ws` and `:artemis-cnft` dependencies

---

## 2.1.1 - April 3, 2026

**Major Release**: Kotlin Multiplatform (KMP) conversion across Foundation, Mobile, and Ecosystem rings.

### Kotlin Multiplatform

- **Ring 1 Foundation**: All 9 modules converted to KMP (`kotlin("multiplatform")` with `jvm()` target):
  `artemis-core`, `artemis-rpc`, `artemis-ws`, `artemis-tx`, `artemis-vtx`, `artemis-programs`, `artemis-errors`, `artemis-logging`, `artemis-compute`
- **Ring 2 Mobile**: `artemis-wallet` converted to KMP; `artemis-wallet-mwa-android` and `artemis-seed-vault` remain Android-only as intended
- **Ring 3 Ecosystem**: All 12 protocol modules converted to KMP:
  `artemis-discriminators`, `artemis-nft-compat`, `artemis-token2022`, `artemis-mplcore`, `artemis-anchor`, `artemis-solana-pay`, `artemis-candy-machine`, `artemis-candy-machine-presets`, `artemis-cnft`, `artemis-jupiter`, `artemis-actions`, `artemis-metaplex`

### Breaking Changes

- `JupiterClient` amount methods now use `Double` instead of `java.math.BigDecimal`
- `JupiterClient` / `ActionsClient` constructors accept `HttpApiClient` instead of `OkHttpClient`
- `SolanaPayUri.Request.amount` is now `String?` instead of `BigDecimal?`
- `BorshReader` (metaplex) constructor takes `ByteArray` instead of `ByteBuffer`

### Platform Abstraction

- `expect/actual` seams for SHA-256, Base64, Ed25519, secure random, system time
- `HttpApiClient` interface for HTTP transport (OkHttp actual on JVM)
- `HttpTransport` interface for RPC transport
- Zero `java.*` imports in any `commonMain` source set
- JVM toolchain set to 17 for all KMP and JVM modules

### Architecture

- Ring dependency enforcement via `./gradlew checkDependencyRings`
- Six-ring model: Foundation, Mobile, Ecosystem, Advanced, Compat, Interop
- Strict downward-only dependencies enforced at build time

## 1.6.0 - January 24, 2026

**Major Release**: Five new developer experience modules.

### New Modules

#### Transaction Intent Protocol (`artemis-intent`)
**Human-readable transaction decoding**

Decode raw Solana transactions into human-readable intent summaries with risk analysis:

```kotlin
import com.selenus.artemis.intent.TransactionIntentDecoder
import com.selenus.artemis.intent.TransactionIntent

val decoder = TransactionIntentDecoder()
val analysis = decoder.analyze(transaction)

// Get human-readable summary
println(analysis.summary)  // "Transfer 5 SOL to Abc123..., then swap 10 USDC"

// Check risk level
if (analysis.riskLevel == RiskLevel.HIGH) {
    showWarning("This transaction requires careful review")
}

// Get individual intents
analysis.intents.forEach { intent ->
    println("${intent.action}: ${intent.humanReadable}")
    println("Risk: ${intent.riskLevel}, Confidence: ${intent.confidence}")
}
```

**Supported Programs**:
- System Program (transfers, account creation)
- SPL Token & Token-2022 (transfers, approvals, minting)
- Associated Token Program (ATA creation)
- Compute Budget (priority fees, unit limits)
- Memo Program
- Stake Program (delegation, withdrawal)

#### Real-Time Portfolio Sync (`artemis-portfolio`)
**Live WebSocket portfolio tracking**

Reactive portfolio tracking with debounced updates and price feeds:

```kotlin
import com.selenus.artemis.portfolio.PortfolioTracker
import com.selenus.artemis.portfolio.PortfolioConfig

val tracker = PortfolioTracker(rpcApi, webSocketClient)

// Start tracking
tracker.trackWallet(myWallet)

// Reactive state updates
tracker.state.collect { portfolio ->
    println("Total: ${portfolio.totalValueUsd} USD")
    portfolio.assets.forEach { asset ->
        println("${asset.symbol}: ${asset.amount} (${asset.valueUsd} USD)")
    }
}

// Or collect events for UI updates
tracker.events.collect { event ->
    when (event) {
        is PortfolioEvent.AssetUpdated -> updateAssetCard(event.asset)
        is PortfolioEvent.PriceChanged -> animatePriceChange(event)
        is PortfolioEvent.TransactionDetected -> showNotification(event.signature)
    }
}
```

#### Offline Transaction Queue (`artemis-offline`)
**Durable offline transaction support**

Queue transactions while offline with automatic retry and durable nonces:

```kotlin
import com.selenus.artemis.offline.OfflineTransactionQueue
import com.selenus.artemis.offline.QueueConfig

val queue = OfflineTransactionQueue(
    submitter = DefaultTransactionSubmitter(rpcApi),
    storage = InMemoryQueueStorage(), // Or implement persistent storage
    config = QueueConfig(
        maxRetries = 5,
        retryDelayMs = 1000,
        useDurableNonce = true  // Transactions survive blockhash expiry
    )
)

// Queue while offline
val id = queue.enqueue(transaction, priority = Priority.HIGH)

// Later, when online
queue.processQueue()

// Track status
queue.events.collect { event ->
    when (event) {
        is QueueEvent.TransactionSubmitted -> println("Sent: ${event.signature}")
        is QueueEvent.TransactionConfirmed -> println("Confirmed: ${event.signature}")
        is QueueEvent.TransactionFailed -> handleFailure(event.error)
    }
}
```

#### Predictive Transaction Scheduler (`artemis-scheduler`)
**Network-aware transaction scheduling**

Predict optimal transaction submission times based on network conditions:

```kotlin
import com.selenus.artemis.scheduler.TransactionScheduler
import com.selenus.artemis.scheduler.SchedulerConfig

val scheduler = TransactionScheduler(rpcApi, SchedulerConfig())

// Get current network state
val networkState = scheduler.getNetworkState()
println("TPS: ${networkState.tps}, Congestion: ${networkState.congestionLevel}")

// Get scheduling recommendation
val recommendation = scheduler.recommend()
when (recommendation.action) {
    ScheduleAction.SUBMIT_NOW -> submitTransaction()
    ScheduleAction.WAIT -> {
        println("Wait ${recommendation.delayMs}ms for better conditions")
        println("Reason: ${recommendation.reason}")
    }
    ScheduleAction.INCREASE_PRIORITY -> {
        transaction.addPriorityFee(recommendation.suggestedPriorityFee)
        submitTransaction()
    }
}

// Monitor network conditions
scheduler.networkState.collect { state ->
    updateNetworkIndicator(state.congestionLevel)
}
```

#### Intelligent Batching Engine (`artemis-batch`)
**Automatic transaction batching**

Automatically combine multiple operations into optimized transactions:

```kotlin
import com.selenus.artemis.batch.TransactionBatchEngine
import com.selenus.artemis.batch.BatchOperation
import com.selenus.artemis.batch.BatchStrategy

val engine = TransactionBatchEngine(BatchConfig())

// Define operations
val operations = listOf(
    BatchOperation.Transfer(from, to1, 1_000_000),
    BatchOperation.Transfer(from, to2, 2_000_000),
    BatchOperation.TokenTransfer(tokenMint, from, to3, 100),
    BatchOperation.CreateAta(owner, tokenMint),
    BatchOperation.Memo("Batch payment")
)

// Plan the batch
val plan = engine.plan(operations, BatchStrategy.MINIMIZE_TRANSACTIONS)

println("${plan.operations.size} ops → ${plan.batches.size} transactions")
println("Estimated savings: ${plan.estimatedSavingsLamports} lamports")

// Build transactions
val transactions = engine.buildTransactions(plan, feePayer, recentBlockhash)

// Execute with progress tracking
engine.events.collect { event ->
    when (event) {
        is BatchEvent.BatchStarted -> showProgress(event.batchIndex, plan.batches.size)
        is BatchEvent.BatchCompleted -> updateProgress(event.result)
        is BatchEvent.AllCompleted -> showSuccess()
    }
}
engine.execute(plan) { tx, index -> submitTransaction(tx) }
```

### New Modules

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `artemis-intent` | Transaction Intent Protocol | core, tx, programs |
| `artemis-portfolio` | Real-Time Portfolio Sync | core, rpc, ws |
| `artemis-offline` | Offline Transaction Queue | core, tx, rpc |
| `artemis-scheduler` | Predictive Transaction Scheduler | core, rpc |
| `artemis-batch` | Intelligent Batching Engine | core, tx, rpc, programs, compute |

### Why these modules matter

These modules address common developer pain points:

1. **Transaction Intent Protocol** - Users can finally understand what they're signing
2. **Real-Time Portfolio Sync** - No more manual polling, reactive UI updates
3. **Offline Transaction Queue** - Mobile apps work in subways, planes, dead zones
4. **Predictive Scheduler** - Lower costs, higher success rates during congestion
5. **Intelligent Batching** - Fewer transactions, lower fees, better UX

### Correct Import Paths

```kotlin
// Transaction Intent Protocol
import com.selenus.artemis.intent.TransactionIntentDecoder
import com.selenus.artemis.intent.TransactionIntent
import com.selenus.artemis.intent.RiskLevel

// Real-Time Portfolio
import com.selenus.artemis.portfolio.PortfolioTracker
import com.selenus.artemis.portfolio.Asset
import com.selenus.artemis.portfolio.PortfolioEvent

// Offline Queue
import com.selenus.artemis.offline.OfflineTransactionQueue
import com.selenus.artemis.offline.QueuedTransaction
import com.selenus.artemis.offline.TransactionStatus

// Predictive Scheduler
import com.selenus.artemis.scheduler.TransactionScheduler
import com.selenus.artemis.scheduler.NetworkState
import com.selenus.artemis.scheduler.ScheduleAction

// Intelligent Batching
import com.selenus.artemis.batch.TransactionBatchEngine
import com.selenus.artemis.batch.BatchOperation
import com.selenus.artemis.batch.BatchPlan
```

---

## 1.5.1 - January 24, 2026

**Patch Release**: API Fixes & Documentation Improvements

This patch addresses API issues reported by integrators and improves documentation accuracy.

### Fixes

#### Token-2022 Module (`artemis-token2022`)
- **Added** `Token2022Tlv` in correct package `com.selenus.artemis.token2022`
  - TLV decoding utilities for all 19 Token-2022 extension types
  - `decode()`, `findByType()`, `hasExtension()`, `getTypeName()` methods

#### Compute Budget Module (`artemis-compute`)
- **Added** `ComputeBudgetPresets` to `artemis-compute` module (was only in `artemis-gaming`)
  - Tier enum: `STANDARD`, `ENHANCED`, `PRIORITY`, `URGENT`, `MAXIMUM`
  - `preset(tier)`, `setComputeUnitLimit(units)`, `setComputeUnitPrice(microLamports)`
  - `custom(units, microLamports)` for advanced use cases

#### DePIN Module (`artemis-depin`)
- **Added** `DeviceAttestation` for device attestation proofs
  - `createAttestation()`, `createChallenge()`, `respondToChallenge()`, `verifyAttestation()`
  - `AttestationProof` and `AttestationChallenge` data classes
- **Clarified** `LocationProof` is a data class inside `DeviceIdentity.kt`

#### RPC Module (`artemis-rpc`)
- **Added** `AccountInfo` typed wrapper with `.owner`, `.lamports`, `.data` properties
- **Added** `TokenAccountInfo` for parsed SPL token accounts
- **Added** `MintInfo` for parsed mint accounts
- **Added** `getAccountInfoParsed()`, `getTokenAccountInfoParsed()`, `getMintInfoParsed()` methods to `RpcApi`

### Documentation

- **Updated** README.md with a full API reference section
  - Correct import paths for all modules
  - Code samples for Token-2022, Compute, Gaming, Privacy, DePIN
- **Updated** Migration Guide with accurate package names
- **Added** Troubleshooting section for common import issues
- **Clarified** class naming: `VerifiableRandomness` (not `VrfUtils`)

### Correct Import Paths (Quick Reference)

```kotlin
// Core
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Keypair

// RPC with typed responses
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.AccountInfo

// Token-2022
import com.selenus.artemis.token2022.Token2022Tlv

// Compute
import com.selenus.artemis.compute.ComputeBudgetPresets

// Gaming (note: VerifiableRandomness, not VrfUtils)
import com.selenus.artemis.gaming.VerifiableRandomness
import com.selenus.artemis.gaming.PriorityFeeOracle
import com.selenus.artemis.gaming.RewardDistribution

// Privacy
import com.selenus.artemis.privacy.ConfidentialTransfer

// DePIN
import com.selenus.artemis.depin.DeviceAttestation
import com.selenus.artemis.depin.DeviceIdentity  // Contains LocationProof
```

---

## 1.5.0 - January 24, 2026

 **Major Release**: Privacy and gaming modules.

New privacy and gaming features following Solana Foundation standards and modern Android architecture patterns.

### New Features

#### Privacy Module (`artemis-privacy`)
Cryptographically-sound privacy features for confidential transactions and anonymous operations:

- **Confidential Transfers** (`ConfidentialTransfer.kt`)
  - Pedersen commitments for hidden transaction amounts
  - Range proofs to prevent overflow attacks
  - AES-256-GCM encrypted amounts with SecureCrypto
  - Auditor key support for regulatory compliance
  - Mobile-optimized cryptographic operations

- **Ring Signatures** (`RingSignature.kt`)
  - SAG (Spontaneous Anonymous Group) signatures
  - Key images for double-spend prevention
  - Linkable signatures for vote tracking
  - Anonymous DAO voting, whistleblower protection
  - Up to 128 members per ring

- **Mixing Pools** (`MixingPool.kt`)
  - CoinJoin-style transaction mixing
  - Commit-reveal scheme for output privacy
  - Shuffled outputs to break transaction graph
  - Configurable pool sizes (3-10 participants)

#### Gaming Module Enhancements (`artemis-gaming`)
Production-ready gaming utilities with cryptographic guarantees:

- **Verifiable Randomness** (`VerifiableRandomness.kt`)
  - VRF (Verifiable Random Function) with proofs
  - Commit-reveal scheme for player-contributed entropy
  - Multi-party random beacon support
  - Provably fair for card games, loot boxes, matchmaking
  - Mobile-optimized (no heavy curve operations)

- **Game State Proofs** (`GameStateProofs.kt`)
  - Merkle-ized game state for efficient verification
  - State transition proofs (valid move verification)
  - Optimistic state channels with fraud proofs
  - Checkpoint system for state recovery
  - Off-chain gaming with on-chain dispute resolution

- **Reward Distribution** (`RewardDistribution.kt`)
  - 4 payout strategies:
    - Winner Takes All
    - Linear Decay
    - Exponential Payout (configurable decay)
    - Poker-style (50%/30%/20% splits)
  - Merkle claim trees for gas-efficient payouts
  - Streamed rewards support
  - Battle royale, tournaments, esports ready

#### Metaplex Module Enhancements (`artemis-metaplex`)

- **Advanced NFT Operations** (`AdvancedNftOperations.kt`)
  - Batch minting (up to 4 NFTs per transaction)
  - Dynamic metadata with state hashing
  - Collection management with verification
  - Royalty enforcement helpers
  - Off-chain metadata caching
  - Mobile-first design patterns

#### Token-2022 Module Enhancements (`artemis-token2022`)

- **Advanced Token-2022 Extensions** (`AdvancedToken2022Extensions.kt`)
  - Complete extension support:
    - Interest-Bearing Tokens (automatic yield calculation)
    - Non-Transferable/Soulbound Tokens
    - Permanent Delegate
    - Transfer Hooks (custom logic)
    - Metadata Pointer (on-chain metadata)
    - Confidential Transfers (private amounts)
    - CPI Guard
    - Default Account State
  - `prepareMintWithExtensions()` for multi-extension mints
  - Mobile-optimized serialization

#### Wallet Module Enhancements (`artemis-wallet`)

- **SendTransactionOptions API**
  - Full commitment level control (processed/confirmed/finalized)
  - Preflight configuration (skip/enable, commitment)
  - Max retries configuration
  - Batch transaction support with ordered execution
  - `waitForCommitmentToSendNextTransaction` for dependent transactions
  - Priority fee strategy integration
  - Mobile-optimized defaults

- **MWA Adapter Updates** (`artemis-wallet-mwa-android`)
  - Implements new `WalletAdapterSignAndSend` interface
  - `signAndSendTransaction()` with `SendTransactionOptions`
  - `signAndSendTransactions()` with `BatchSendResult`
  - Full Solana Mobile Wallet Adapter 2.0 parity

### 🔄 Changed

- **Transaction Infrastructure**: Enhanced with `SendTransactionOptions` across all wallet adapters
- **Mobile Wallet Adapter**: Updated to support structured transaction results with commitment tracking
- **API Consistency**: All wallet methods now follow coroutine-first patterns with Flow

### Architecture

- **Solana Foundation Standards**: cryptographic implementations verified against official specifications
- **Modern Android architecture**: Kotlin Coroutines, Flow, StateFlow
- **Solana Mobile compatibility**: source-compatible client-library migration path for Solana Mobile apps
- **BIP-39 / SLIP-0010 verified**: cryptographic primitives match `bitcoin/bips` and `satoshilabs/slips`
- **Zero new dependencies**: built on existing BouncyCastle and the in-repo SecureCrypto module
- **Mobile-first**: tuned for Android performance and battery use

### Verification

- **Full project build**: 297 Gradle tasks pass on a clean checkout
- **Zero compilation errors** across 28+ modules at the time of release
- **Cryptographic verification**:
  - BIP-39: PBKDF2-HMAC-SHA512 with 2048 iterations
  - SLIP-0010: Ed25519 derivation with the `ed25519 seed` constant
  - All implementations match official specifications

### Release docs

- Main README updated with feature descriptions
- Module-specific documentation added for new features
- Devnet integration test sample added
- Migration guides updated for Solana Mobile SDK users

### Targeted use cases

- Mobile games (VRF, state proofs, reward distribution)
- Privacy-focused dApps (confidential transfers, ring signatures)
- NFT marketplaces (batch minting, dynamic metadata)
- DeFi protocols (Token-2022 extensions, interest-bearing tokens)

---

## 1.4.0

**Breaking Change**: Renamed `artemis-runtime` to `artemis-core` to match Solana Mobile SDK naming convention.

### Changed

- **Module Rename**: `artemis-runtime` → `artemis-core`
  - Matches Solana Mobile's `web3-core` naming convention for source compatibility
  - Also absorbed former `artemis-core` (multiplatform) utilities
  - `artemis-core` is now the single foundational module containing:
    - Primitives: Pubkey, Keypair, Base58, Pda, Crypto, Signer
    - Utilities: BatchProcessor, Flows, StateManagement, TransactionPipeline

### Migration

Replace `artemis-runtime` dependency with `artemis-core`:
```kotlin
// Before
implementation("xyz.selenus:artemis-runtime:1.3.x")

// After  
implementation("xyz.selenus:artemis-core:1.4.0")
```

Import paths remain the same (`com.selenus.artemis.core.*`).

### Removed

- `artemis-runtime` module (renamed to `artemis-core`)
- `artemis-core-jvm` multiplatform artifact (no longer needed)

## 1.3.1

Patch release fixing Maven Central publishing for multiplatform modules.

### Fixed

- Added Dokka javadoc generation for `artemis-core-jvm` multiplatform artifact
- Fixed Maven Central validation requirement for javadoc JAR in JVM publications

## 1.3.0

Major release. Unit tests across all modules plus documentation updates.

### Added

- **Test Coverage**: Added unit tests for all 26+ modules
  - artemis-candy-machine, artemis-candy-machine-presets
  - artemis-cnft, artemis-compute, artemis-core
  - artemis-depin, artemis-discriminators, artemis-errors
  - artemis-gaming, artemis-logging, artemis-metaplex
  - artemis-mplcore, artemis-nft-compat, artemis-presets
  - artemis-preview, artemis-privacy, artemis-programs
  - artemis-replay, artemis-rpc, artemis-runtime
  - artemis-seed-vault, artemis-solana-pay, artemis-token2022
  - artemis-tx, artemis-tx-presets, artemis-vtx
  - artemis-wallet, artemis-ws

### Fixed

- Fixed invalid Candy Guard program ID in `CandyMachineIds.kt`
- Fixed PDA seed length validation in MPL Core tests

### Documentation

- Updated README with complete module listing (26+ modules)
- Reorganized module documentation by category (Core, Transactions, NFT, Mobile, Gaming)
- Updated all version references to 1.3.0

## 1.2.0

Initial Maven Central release with full SDK feature set.

## 1.0.8

Patch release focusing on code hygiene and legacy cleanup.

### Changes
- **Refactor**: Defined Artemis-native `SeedVaultConstants` with full documentation.
- **Cleanup**: Removed legacy `com.solana` packages and duplicate `SignInWithSolana` logic from MWA module.
- **Verification**: Validated MWA and Seed Vault implementations are self-contained.

## 65

Hardening + Metaplex parity pass.

### Changes

- Added `@ExperimentalArtemisApi` annotation for clarifying stability boundaries.
- Added `:artemis-metaplex` one-stop `Metaplex` facade that composes supported Metaplex program workflows:
  - Token Metadata reads via `:artemis-nft-compat`
  - Candy Machine v3 mint presets via `:artemis-candy-machine-presets`
  - Bubblegum/cNFT flows via `:artemis-cnft`
- Added `docs/metaplex-parity.md` checklist.

- Added Metaplex-style NFT query helpers to `:artemis-nft-compat` (indexer-free):
  - `NftClient.findByMint`
  - `NftClient.findAllByMintList`
  - `NftClient.findAllByOwner` (heuristic: token accounts amount == 1)
  - `NftClient.findAllByCreator` (memcmp filter on metadata creator slots; may be heavy)
  - `NftClient.findAllByCandyMachineV2` (best-effort creator-slot filter)

These mirror the common Metaplex Android SDK “find” methods but keep Artemis indexer-free.

### Non-goals

- Auction House / Gumdrop are not included in default surface area. They can land as optional modules if/when needed.

## 64: Solana Mobile Compile-Proof Sample + CI Fix

### Changes

1. **CI now provisions Android SDK**
   - Adds `android-actions/setup-android@v3`
   - Installs `platforms;android-35` and `build-tools;35.0.0`

2. **Optional Android sample app** (`:samples:solana-mobile-compose-mint-app`)
   - Demonstrates:
     - `MwaWalletAdapter.connect()`
     - `CandyMachineMintPresets.mintNewWithSeed(...)`
   - Excluded from the default build unless enabled with:
     `-PenableAndroidSamples=true`

3. **CONTRIBUTING.md**
   - Defines build and review commands

### Tenets audit

- No new required dependencies in core modules
- Sample is optional and does not pollute Artemis core
- No TODOs or placeholders added

## 63

This release hardens Artemis for Solana Kit / Solana Mobile review by making builds reproducible from a fresh checkout.

### Added

- Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) so contributors and CI can build without a local Gradle install.
- GitHub Actions CI workflow that runs `./gradlew build` on push and pull requests.
- `scripts/verify.sh` for local verification.

### Notes

- The wrapper is intentionally lightweight and reads `gradle/wrapper/gradle-wrapper.properties` for the Gradle distribution URL.

## 62

Hardening pass for Solana Kit / Solana Mobile review readiness.

### Changes

- Docs: replaced stale package map with a module-accurate `docs/packages.md`.
- Docs: added `docs/public-api.md` to name the intended stable entrypoints.
- Docs: added `docs/why-artemis-vs-kotlin-sdks.md` for reviewer context.
- Samples: added `samples/solana-mobile-compose-mint` walkthrough for MWA + Candy Machine mint preset.

### No breaking changes

This release is documentation, clarity, and integration hardening. No runtime behavior changes.

## 60

### Added

- `artemis-candy-machine-presets`: optional one-call Candy Machine mint preset for mobile.
  - Uses v58 planner + safe builder to construct `mint_v2` deterministically.
  - Uses v59 transaction composer preset for optional ATA creation, priority fees, and resend/confirm.

### Docs

- Added: `docs/candy-machine-mint-presets.md`

## 59

Transaction composer presets.

### Added

- **artemis-tx-presets**: optional composer presets for mobile and games
  - create missing ATAs (only when needed)
  - priority fees and compute limits injected via `SendPipeline`
  - resend and confirm loop for flaky mobile networks

### Docs

- `docs/tx-composer-presets.md`

## 56 highlights

Metaplex parity upgrades (optional module)

pNFT:
- Token record PDA + tolerant token record parser
- NftClient helper to fetch token records for UX (locked state, delegates, rule sets)

Collections:
- Collection authority record PDA + parser + fetch helper
- Token Metadata instruction builders:
  - approveCollectionAuthority
  - revokeCollectionAuthority
  - setAndVerifyCollection
  - unverifyCollection
  - verifySizedCollectionItem

Metadata parser now also surfaces collectionDetails (sized collections).

## 55 highlights

Metaplex-compatible capabilities (optional module)

- Token Metadata PDAs (metadata, master edition, edition marker, token record, collection authority record)
- Metadata parser expanded (collection + uses)
- MasterEditionV2 parser
- Token Metadata instruction builders:
  - createMetadataAccountV3
  - createMasterEditionV3
  - updateMetadataAccountV2
  - signMetadata
  - verifyCollection
- RPC-only wallet NFT listing helper:
  - listWalletNfts(owner) derives metadata PDAs and uses getMultipleAccounts for batch fetch

## 54 highlights

1) Token program helpers
- SPL Token builders (transfer, mintTo, burn, closeAccount, transferChecked, syncNative, initializeMint2)
- Token-2022 builders (initializeMint2, transferChecked, mintToChecked, closeAccount)
- Associated Token Account create instruction builder (supports Token and Token-2022)

2) Optional Metaplex-compatible NFT module
- PDA derivations for metadata and master edition
- Metadata fetch + minimal Borsh parse for name/symbol/uri/sellerFee/creators

3) Solana Mobile migration doc
- Adopt Artemis native MWA client layer in 15 minutes

## 53 highlights

- Batch-aware MWA routing based on get_capabilities limits
- Per-batch retry helper for wallet operations
- Docs for batching behavior

## 52 highlights

- MWA feature detection via get_capabilities
- Automatic fallback routing between sign-and-send and sign-only
- Adapter helper `signThenSendViaRpc` that routes between sign-and-send and sign-only automatically

## 50 highlights

### Wallet and mobile app wiring

- Android MWA adapter adds optional sign-and-send fast path when wallet supports it
- DataStoreAuthTokenStore for authToken persistence
- Android sample includes a minimal Send SOL + Memo flow using Artemis transaction builder

Arcana and Arcane remain optional and do not affect core SDK usage.

## 49 highlights

- Android MWA wallet adapter module (reference implementation)
- Local signer wallet adapter for tests and server-side
- Wallet integration docs for Android and generic usage

## 48 highlights

Core SDK improvements focused on being a general-purpose Solana Kotlin SDK.

### RPC reliability and ergonomics

- JsonRpcClient now supports retry and backoff via RpcClientConfig
- RpcFacade groups RPC into smaller surfaces
- new docs: rpc-reliability.md and rpc-facade.md

### Game presets expansion

Arcane rollup presets now include CASINO and LOTTERY styles.
Arcane remains optional and separate from the core Solana replacement SDK.

## 47 highlights

- RPC "long tail" completion helpers and typed filter builders
- Confirmation utilities for mobile reliability
- Token and stake query helpers

### What shipped

- RpcFilters: memcmp and dataSize builders for getProgramAccounts
- RpcApi.confirmTransaction: signature status polling
- RpcApi.sendAndConfirmRawTransaction: one-call send + confirm
- additional wrappers for token, stake, fee, and slot/commitment methods
- callRaw remains available for any RPC not explicitly wrapped yet

This release reduces the need for `callRaw` in common apps while keeping full coverage.

## 46 highlights

- Stable error taxonomy for mobile UX (artemis-errors)
- RpcApi expanded toward full Solana JSON-RPC coverage
- RpcApi.callRaw added for any remaining methods not wrapped yet
- SendPipeline convenience overload uses default blockhash error detection

## 45 highlights

SolanaKT parity sweep for RPC.

### What shipped

- artemis-rpc rebuilt for correctness and maintainability
- expanded RPC method coverage used by mobile apps
- Ktor parity via HttpTransport remains supported
- compatibility map: docs/compat/solanakt.md
- request fixture unit tests for critical methods

### Next

- expand RPC methods used by token/NFT stacks (more filters, parsed variants)
- add mobile-friendly error taxonomy across RPC and wallet

## 44 highlights

- artemis-logging: optional SLF4J logging facade (reflection bridge) with stdout fallback
- artemis-rpc: pluggable HttpTransport for Ktor parity without extra dependencies
- audit checklist added (docs/audit.md)

This release improves mobile and app-stack compatibility while keeping Artemis modular.

## 43 highlights

Core SDK upgrade: Wallet capability layer and unified send pipeline facade.

### What shipped

- artemis-wallet:
  - WalletAdapter contract
  - WalletCapabilities + caching
  - request hints (sign, re-sign, fee payer swap)
  - SendPipeline facade with compute tuning and re-sign handling

### Why it matters

Android wallets differ in:
- signing formats
- batch support
- re-sign behavior
- fee payer swap and partial signing

This layer normalizes the differences so apps can drop in with fewer custom branches.

### Example

```kotlin
val result = SendPipeline.send(
  config = SendPipeline.Config(desiredPriority0to100 = 60),
  adapter = walletAdapter,
  getLatestBlockhash = { rpc.getLatestBlockhash() },
  compileLegacyMessage = { bh, computeIxs ->
    txCompiler.compileLegacy(blockhash = bh, computeIxs = computeIxs)
  },
  sendSigned = { signed -> rpc.sendRawTransaction(signed) },
  isBlockhashFailure = { err -> err.message?.contains("blockhash", ignoreCase = true) == true }
)
```

## 42 highlights

Core SDK upgrade: Compute budget helpers.

### What shipped

- artemis-compute: ComputeBudgetProgram builders and ComputeBudgetBuilder
- integration with TxBudgetAdvisor advice (v41)

### ComputeBudgetBuilder

Use game friendly presets or your own priority:

```kotlin
val instructions = ComputeBudgetBuilder()
  .forGameAction(ComputeBudgetBuilder.GamePriority.PLAYER_ACTION)
  .withLegacySizeBytes(advice.legacySizeBytes)
  .withV0SizeBytes(advice.v0SizeBytes)
  .buildInstructions()
```

Or build from the TxBudgetAdvisor output:

```kotlin
val instructions = ComputeBudgetBuilder()
  .fromAdvice(advice)
  .buildInstructions()
```

This reduces failed sends on mobile by keeping compute and fees consistent.

## 41 highlights

Core SDK upgrades focused on mobile reliability:

- wallet re-sign primitives (refresh + re-sign loop interfaces)
- transaction budget advisor (size and compute heuristics for v0 + ALTs)
- package map updated so modules stay easy to remember

### Wallet resilience

WalletResilience provides:
- WalletSigner interface
- SignerCapabilities
- withReSign() helper for blockhash refresh flows

This reduces "send failed" incidents on mobile when users background apps or approve slowly.

### TxBudgetAdvisor

TxBudgetAdvisor.advise() returns:
- legacy and v0 message size
- shouldPreferV0 decision
- recommended compute unit limit and price

Games can tune priority from 0..100 depending on user action value.

## 40 highlights

- zone rate policy helper for cost predictability
- package map to keep modules easy to remember

## 39 highlights

- websocket microblock stream can request delta-first payloads

MicroblockStreamClient.connect(, preferDelta = true, )

When preferDelta is true and the server has deltaB64:
- framesCjsonB64 is nulled
- deltaB64 is included

## 38 highlights

- preferDelta support for cheaper microblock fetches
- tuning client helper for Arcane Cloud recommendations
- retention guidance for studios that want low infra costs

### preferDelta

ArcaneMicroblockPager.fetch(, preferDelta = true)

This requests:
- preferDelta=true

Arcane Cloud will return:
- framesCjsonB64 = null
- deltaB64 filled when available

Decode:
- DeltaCodec.decodeDeltaB64Url(deltaB64)

### Retention

Arcane Cloud can delete old raw frames after microblocks exist.
Studios can keep a safety window for debugging and audits.

### Tuning

TuningClient.recommendations() returns JSON that includes:
- recommended ticks
- suggested retention window

## 37 highlights

- delta microblock decode support (gzip)
- genre preset helpers for cadence
- docs for cost and tuning

### Delta

Arcane Cloud may return deltaB64 on microblocks.

Decode:
- DeltaCodec.decodeDeltaB64Url(deltaB64)

This reduces egress and keeps Arcane Cloud costs comparable to other engines.

### Presets

GenrePresets provides recommended defaults for:
- shooters
- runners
- platformers
- RPG and MMORPG

## 36 highlights

### Arcane Cloud

- live microblock websocket stream pushes new microblocks as they are flushed
- microblock cadence is configurable for cost control

Environment:
- ARCANE_MICROBLOCK_RT_TICKS (default 30)
- ARCANE_MICROBLOCK_DUR_TICKS (default 60)

### Artemis SDK

- SessionMigration helper for rollover + gateway affinity
- Microblock stream client continues to work unchanged
- ReadModels expands with token, NFT, and program read model interfaces

This keeps the SDK neutral:
- raw RPC implementations work
- Luna (Helius) implementations work
- Arcane Cloud paid bundles work

## 35 highlights

- rollup microblock paging client
- rollup microblock stream client (websocket)
- memo helper for commit anchoring
- core read model interfaces (provider-agnostic)

### Rollup stream

MicroblockStreamClient connects to:
- /v1/rollup/microblocks/stream

It is newline-delimited json.

### Anchoring

RollupMemo.memosForCommit(commit) returns memo strings.
