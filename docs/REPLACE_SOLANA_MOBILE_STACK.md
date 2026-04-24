# Replacing the Solana Mobile Stack with Artemis

Step-by-step migration from the standard Solana Mobile Kotlin dependencies to Artemis equivalents. For inline API mappings see [migration-solana-mobile.md](migration-solana-mobile.md).

## Two migration tracks, pick one

There are two honest ways to adopt Artemis. Pick one up front.

### Artemis-native migration (recommended today)

- Adopt `ArtemisMobile.create(...)`, `WalletSession`, `WalletSessionManager`, `TxEngine`, and `RpcApi` directly.
- Change the call sites in your app to the Artemis shapes below.
- Every feature marked `Verified` in [PARITY_MATRIX.md](PARITY_MATRIX.md) under the Wallet/Mobile, RPC, and WebSocket sections is safe to depend on.
- Best for: new apps, or existing apps that are willing to update call sites once.

### SMS-drop-in migration

- Keep your existing `com.solanamobile.*` / `org.sol4k.*` / `com.solana.*` imports. Swap the Maven coordinates to the Artemis compat artifacts under `interop/artemis-*-compat`.
- Artemis matches the upstream public API so the surface-level imports compile unchanged. Behaviour is backed by Artemis engines under the hood.
- Today this track is `Verified` for the MWA client (ktx + non-ktx), the Seed Vault static surface, and the compat typed results. Other compat modules are `Partial`. See PARITY_MATRIX.md.
- Best for: large apps that do NOT want to update call sites, or apps that depend on a specific upstream class shape that Artemis may not expose as a native type.

The rest of this document shows both forms where they differ.

## Dependency mapping

| Old dependency | Artemis replacement | Notes |
| --- | --- | --- |
| `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` | `xyz.selenus:artemis-wallet-mwa-android` | MWA 2.0 client with persistent session management |
| `com.solanamobile:seedvault-wallet-sdk` | `xyz.selenus:artemis-seed-vault` | Seed Vault integration for Saga |
| `com.solanamobile:rpc-core` (or solana-kmp for RPC) | `xyz.selenus:artemis-rpc` | JSON-RPC with endpoint pool, batch DSL, blockhash cache |
| solana-kmp `PublicKey`, `Transaction` | `xyz.selenus:artemis-core`, `xyz.selenus:artemis-tx` | Core types and legacy transaction model |
| solana-kmp `VersionedTransaction` | `xyz.selenus:artemis-vtx` | v0 versioned transactions with address lookup tables |
| sol4k primitives | `xyz.selenus:artemis-core` | Pubkey, Keypair, Base58, Ed25519, PDA |
| No equivalent | `xyz.selenus:artemis-cnft` | `ArtemisDas`, `HeliusDas`, `RpcFallbackDas`, `CompositeDas`, `MarketplaceEngine`, `AtaEnsurer` |
| No equivalent | `xyz.selenus:artemis-ws` | `RealtimeEngine`, typed `ConnectionState` StateFlow |

The current published version is `2.3.0`. The version field is the source of truth in [../gradle.properties](../gradle.properties).

## Gradle migration

### Before

```kotlin
dependencies {
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")
    implementation("com.solanamobile:seedvault-wallet-sdk:0.2.6")
    implementation("com.metaplex:solana:0.3.0") // solana-kmp
    // or
    implementation("org.sol4k:sol4k:0.5.4")
}
```

### After

```kotlin
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
}
```

## Concept mapping

### Wallet connection

Before (MWA clientlib):

```kotlin
val sender = ActivityResultSender(activity)

transact(sender) { authResult ->
    val publicKey = authResult.publicKey
    // use authResult inside this block
}
```

After (Artemis, full stack):

```kotlin
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico",
    identityName = "My App",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

artemis.wallet.connect()
```

After (Artemis, manual wiring if you want only the wallet adapter):

```kotlin
val adapter = MwaWalletAdapter(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico", // absolute HTTPS URI
    identityName = "My App"
)
adapter.connect()

val wallet = WalletSession.fromAdapter(adapter, txEngine)
```

The session is reusable across the app lifetime. `WalletSessionManager` at [../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt](../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt) caches the auth token, retries on session expiration, and exposes `onDisconnect / onAccountChanged / onSessionExpired` callbacks.

### Signing and sending

Before (MWA clientlib):

```kotlin
transact(sender) { authResult ->
    val signedTx = signTransactions(listOf(serializedTx))
    // manually submit via RPC
}
```

After (Artemis):

```kotlin
// Single instruction
val result = wallet.send(
    SystemProgram.transfer(wallet.publicKey, recipient, lamports)
)

// SOL transfer convenience
val result = wallet.sendSol(to = recipient, lamports = 1_000_000_000L)
```

### RPC access

Before (solana-kmp or sol4k):

```kotlin
// solana-kmp
val connection = Connection(URL("https://api.mainnet-beta.solana.com"))
val balance    = connection.getBalance(publicKey)

// sol4k
val connection = Connection(RpcUrl.MAINNET)
val balance    = connection.getBalance(publicKey)
```

After (Artemis, ArtemisClient DSL):

```kotlin
val client = ArtemisClient {
    rpc        = "https://api.mainnet-beta.solana.com"
    commitment = Commitment.CONFIRMED
}
val balance = client.rpc().getBalance(pubkey)
```

After (Artemis, plain `RpcApi`):

```kotlin
val rpc     = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))
val balance = rpc.getBalance(pubkey)
```

`ArtemisClient` is a thin DSL wrapper over `Connection`, `BlockhashCache`, and `TxEngine`. Source at [../mobile/artemis-wallet/src/jvmMain/kotlin/com/selenus/artemis/wallet/ArtemisClient.kt](../mobile/artemis-wallet/src/jvmMain/kotlin/com/selenus/artemis/wallet/ArtemisClient.kt).

### Transaction building

Before (solana-kmp):

```kotlin
val tx = Transaction()
tx.add(transferInstruction)
tx.setRecentBlockHash(blockhash)
tx.sign(listOf(keypair))
val serialized = tx.serialize()
```

After (Artemis, fluent):

```kotlin
val result = client.tx()
    .add(transferIx)
    .send(signer)
```

After (Artemis, direct engine):

```kotlin
val engine = TxEngine(rpc)
val result = engine.execute(
    instructions = listOf(transferIx),
    signer       = keypair,
    config       = TxConfig(retries = 3, simulate = true)
)
```

The engine handles blockhash caching, simulation, signing, sending, confirmation, and retry. Source at [../foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt](../foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt).

## Status by capability

Status vocabulary is defined in [PARITY_MATRIX.md](PARITY_MATRIX.md). Only claims backed by shipped code are listed here.

| Capability | Status | Source |
| --- | --- | --- |
| MWA 2.0 client (authorize with addresses/features/auth_token/signInPayload) | Verified | [protocol/](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/) |
| MWA session lifecycle (unified `authorize(auth_token=...)` path, legacy reauthorize fallback) | Verified | [WalletSessionManager.kt](../mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt), [MwaClient.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/MwaClient.kt) |
| MWA session close fails pending futures + cancels receive loop | Verified | [MwaSession.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/MwaSession.kt) |
| MWA WS server hardened (loopback, Origin policy, frame cap, fragmentation, ping/pong) | Verified | [MwaWebSocketServer.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/protocol/MwaWebSocketServer.kt) |
| AuthToken Keystore-backed AES-256-GCM store, fail-closed on init failure | Verified | [AuthTokenStore.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/AuthTokenStore.kt) |
| Sign-and-send wallet-unsupported path returns typed `SignedButNotBroadcast` + supports `RpcBroadcaster` injection | Verified | [MwaWalletAdapter.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletAdapter.kt) |
| Seed Vault contract/provider split (`SeedVaultContractClient` + typed `*Provider`) | Verified | [SeedVaultContract.kt](../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultContract.kt), [SeedVaultProviders.kt](../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultProviders.kt) |
| Seed Vault strict auth-token parsing, 30s IPC timeout, provider trust checks | Verified | [SeedVaultManager.kt](../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultManager.kt), [SeedVaultCheck.kt](../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/internal/SeedVaultCheck.kt) |
| X25519 ECDH + HKDF-SHA256 session key derivation | Verified | [SeedVaultCrypto.kt](../mobile/artemis-seed-vault/src/main/kotlin/com/selenus/artemis/seedvault/SeedVaultCrypto.kt) |
| Pubkey, Keypair, Base58, PDA, Bip32 derivation | Verified | [runtime/](../foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/runtime/) |
| Legacy transactions | Verified | [artemis-tx/](../foundation/artemis-tx/) |
| v0 versioned transactions + ALT parse/serialize | Verified | [artemis-vtx/](../foundation/artemis-vtx/) |
| JSON-RPC client (110 methods, typed batch results, classified retry) | Verified | [RpcApi.kt](../foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcApi.kt), [RpcClient.kt](../foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcClient.kt) |
| WebSocket transport with real ping frames + reconnect-waits-for-onOpen | Verified | [SolanaWsClient.kt](../foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/SolanaWsClient.kt) |
| Polling fallback (account/signature/program keys; typed logs-unavailable event) | Partial | [HttpPollingFallback.kt](../foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/HttpPollingFallback.kt) |
| Typed `ConnectionState` StateFlow | Verified | [ConnectionState.kt](../foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt) |
| System, Token, ATA, Compute Budget, Stake programs | Verified | [artemis-programs/](../foundation/artemis-programs/) |
| DAS primary + fallback router with cooldown | Verified | [CompositeDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt) |
| Marketplace preflight + ATA ensurer | Verified | [MarketplacePreflight.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplacePreflight.kt), [AtaEnsurer.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt) |
| Compressed NFT (Bubblegum) transfer | Verified | [BubblegumInstructions.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/BubblegumInstructions.kt) |
| Framework event bus (`ArtemisEvent`) | Verified | [ArtemisEvent.kt](../foundation/artemis-core/src/commonMain/kotlin/com/selenus/artemis/core/ArtemisEvent.kt) |
| Compat API-diff snapshot (`./gradlew dumpApi`) | Verified | [build.gradle.kts](../build.gradle.kts) |
| MWA client live routing through `MwaSessionBridge` | Verified | [MwaSessionBridge.kt](../interop/artemis-mwa-compat/src/main/kotlin/com/solana/mobilewalletadapter/clientlib/MwaSessionBridge.kt) |
| `LocalAssociationScenario` with real P-256 keypair + base64url association token | Verified | [Scenario.kt](../interop/artemis-mwa-clientlib-compat/src/main/kotlin/com/solana/mobilewalletadapter/clientlib/scenario/Scenario.kt) |
| React Native adapter with platform-aware `readyState` | Partial | [MobileWalletAdapter.ts](../advanced/artemis-react-native/MobileWalletAdapter.ts) |

## What differs

| Area | Old stack | Artemis |
| --- | --- | --- |
| Wallet auth | Per-call `transact {}` block | Persistent `WalletSession` with cached auth token |
| Transaction pipeline | Manual build, sign, send, retry | `TxEngine` pipeline with simulation, retry, priority fees, durable nonce |
| RPC | Basic HTTP call site | Endpoint pool, circuit breaker, batch DSL, `BlockhashCache` |
| Realtime | None or raw websocket | `RealtimeEngine` with typed callbacks, deterministic resubscribe, `ConnectionState` StateFlow |
| Events | Per-module callbacks | Single `ArtemisEvent` flow across wallet, tx, realtime, DAS |
| MWA constructor | `ActivityResultSender` | `MwaWalletAdapter(activity, identityUri, iconPath, identityName)` or `ArtemisMobile.create()` |
| Lifecycle | Manual `whenResumed {}` handling | Session-based; lifecycle integration is opt-in |

## Migration steps

1. Replace dependencies in `build.gradle.kts` per the table above.
2. Replace imports: `com.solana.*` and `org.sol4k.*` become `com.selenus.artemis.*`.
3. Replace `transact {}` blocks with `ArtemisMobile.create()` (or, if you want manual wiring, `MwaWalletAdapter` plus `WalletSession.fromAdapter`).
4. Replace direct RPC calls with `ArtemisClient { rpc = ... }` or `RpcApi(JsonRpcClient(url))`.
5. Replace manual transaction build, sign, and send with `wallet.send(ix)` or `TxEngine.execute(...)`.
6. Add `RealtimeEngine` for any account, signature, or program subscriptions you used to poll.
7. Add `ArtemisEventBus` collectors anywhere you used to wire per-module listeners.
8. Run the test suite. The semantics are similar to the official stack but not identical, and the integration test module at [../testing/artemis-devnet-tests/](../testing/artemis-devnet-tests/) is a good reference for the new shape.
