# Architecture overview

Artemis is organized into six rings. Each ring has a clear purpose and strict dependency rules. This is not a suggestion. It is how the repo is structured and the build enforces it via `./gradlew checkDependencyRings`.

## Platform strategy

Ring 1 (Foundation) is Kotlin Multiplatform. Common code compiles for JVM, Android, and future targets. No `java.*` imports in `commonMain` source sets. Platform-specific implementations (crypto, transport, encoding) live behind `expect/actual` seams in `jvmMain` and `androidMain`.

Ring 2 (Mobile) stays platform-native where appropriate. `artemis-wallet` is KMP-safe as the portable signing abstraction. `artemis-wallet-mwa-android` and `artemis-seed-vault` are Android-only.

Rings 3 through 5 sit on top of the portable foundation. Most ecosystem modules are KMP. A small number of advanced modules remain JVM/Android only because their underlying dependencies are.

Ring 6 (Interop) provides explicit legacy compatibility shims. Thin wrappers only.

## The rings

```text
+-------------------------------------------------------------------+
|                       Ring 6: Interop                              |
|   legacy shims (artemis-mwa-compat, artemis-seedvault-compat)      |
+-------------------------------------------------------------------+
|                       Ring 5: Presets                              |
|   discriminators, nft-compat, tx-presets, presets                  |
+-------------------------------------------------------------------+
|                       Ring 4: Advanced                             |
|   privacy, streaming, simulation, batch, scheduler, offline,       |
|   portfolio, replay, gaming, depin, nlp, intent, universal         |
+-------------------------------------------------------------------+
|                       Ring 3: Ecosystem                            |
|   token2022, metaplex, mplcore, cnft, candy-machine,               |
|   solana-pay, anchor, jupiter, actions                             |
+-------------------------------------------------------------------+
|                       Ring 2: Mobile                               |
|   wallet, wallet-mwa-android, seed-vault                           |
+-------------------------------------------------------------------+
|                       Ring 1: Foundation (KMP)                     |
|   core, rpc, ws, tx, vtx, programs, errors, logging, compute       |
+-------------------------------------------------------------------+
```

Higher rings may depend on lower rings. Lower rings never depend on higher rings. That is the whole model.

## Allowed dependency matrix

| From / To | Foundation | Mobile | Ecosystem | Advanced | Presets | Interop |
| --- | --- | --- | --- | --- | --- | --- |
| **Foundation** | yes | no | no | no | no | no |
| **Mobile** | yes | yes | no | no | no | no |
| **Ecosystem** | yes | yes (wallet interface only) | yes | no | no | no |
| **Advanced** | yes | yes | yes | yes | no | no |
| **Presets** | yes | no | yes | no | yes | no |
| **Interop** | yes | yes | yes | no | no | yes |

Arrows point downward only. Any edge going upward is a bug.

## Ring 1: Foundation (KMP)

This is the base layer. Every Solana app built with Artemis uses some or all of these modules. Foundation is Kotlin Multiplatform. Common code contains no `java.*` imports and compiles for JVM, Android, and future targets. Platform-specific crypto, transport, and encoding live in `jvmMain` and `androidMain` source sets behind `expect/actual` seams.

**Modules:** `artemis-core`, `artemis-rpc`, `artemis-ws`, `artemis-tx`, `artemis-vtx`, `artemis-programs`, `artemis-errors`, `artemis-logging`, `artemis-compute`

**What it covers:**

- Pubkey, Keypair, PDA derivation, Base58, Base64
- Ed25519, SHA-256, secure random behind `expect/actual`
- JSON-RPC client with 110 methods declared on `RpcApi`, typed result wrappers, batch DSL, blockhash cache, endpoint pool, circuit breaker
- Websocket subscriptions with auto-reconnect, deterministic resubscribe, polling fallback, and a typed `ConnectionState` StateFlow
- Legacy and v0 versioned transaction construction and serialization
- Address lookup tables
- System (12 instruction builders), Token (10), Associated Token, Compute Budget, Stake (5)
- Compute unit estimation and priority fee helpers
- Structured Solana error types and on-chain error decoding
- Durable nonce support
- Blockhash lifecycle management
- `ArtemisEvent` and `ArtemisEventBus` for unified subsystem events

**Rules:**

- Foundation modules depend only on other foundation modules
- No dependencies on ecosystem, mobile, advanced, or compatibility modules
- Must remain stable, lightweight, and dependency-minimal

## Ring 2: Mobile

This is the Artemis mobile client layer for apps that use Solana Mobile primitives. If you are building an Android Solana app, this ring plus Foundation is your core dependency set. MWA remains the wallet protocol and Seed Vault remains the custody boundary.

**Modules:** `artemis-wallet`, `artemis-wallet-mwa-android`, `artemis-wallet-mwa-walletlib-android`, `artemis-seed-vault`

**What it covers:**

- `WalletAdapter` interface and `WalletSession` with pluggable signing strategies (Local keypair, Adapter, Raw signer)
- `WalletSessionManager` with lazy connect, auth token caching, retry on session expiration, and `onDisconnect / onAccountChanged / onSessionExpired` callbacks
- Mobile Wallet Adapter 2.0 **client** (dApp side): P-256 association, AES-128-GCM session cipher, HKDF-SHA256, MWA RPC, websocket transport, Sign-In With Solana
- Mobile Wallet Adapter 2.0 **wallet-side** runtime (`artemis-wallet-mwa-walletlib-android`): `Scenario` / `LocalScenario`, JSON-RPC dispatcher, chain-gated reauthorize, wallet-driven `DeauthorizedEvent.complete()`, sign-messages address-set check, `AuthRepository.start/stop` lifecycle hooks
- `ArtemisMobile.create()` for one-call setup
- Saga Seed Vault integration for hardware-backed key custody
- Convenience methods: `sendSol()`, `sendToken()`

**Rules:**

- Mobile modules depend on Foundation only
- Never depend on ecosystem or advanced modules
- This ring exists so mobile teams can adopt Artemis without pulling in the entire SDK

## Ring 3: Ecosystem

Optional protocol clients built on top of Foundation. Use them when you need specific on-chain integrations.

**Modules:** `artemis-token2022`, `artemis-metaplex`, `artemis-mplcore`, `artemis-cnft`, `artemis-candy-machine`, `artemis-solana-pay`, `artemis-anchor`, `artemis-jupiter`, `artemis-actions`

**What it covers:**

- Token-2022 mint and account extensions: transfer fees, interest bearing, non-transferable, permanent delegate, default account state, transfer hook, metadata pointer, group and member pointers, confidential transfers, CPI guard, immutable owner, mint close authority
- Token Metadata: metadata account, master edition, collection
- MPL Core (Asset) program
- Compressed NFTs via Bubblegum, plus `ArtemisDas`, `HeliusDas`, `RpcFallbackDas`, `CompositeDas`, `MarketplaceEngine`, `MarketplacePreflight`, `AtaEnsurer`
- Candy Machine v3 mintV2 builder, guard accounts planner, manifest parser
- Solana Pay URL parsing, `SolanaPayManager`
- Anchor IDL parsing, Borsh serializer, type-safe `AnchorProgram` client
- Jupiter DEX aggregator: quotes, swaps, routing
- Solana Actions and Blinks fetch/execute

**Rules:**

- Ecosystem modules depend on Foundation
- May depend on the wallet signing interface in `artemis-wallet`
- Should not depend on advanced modules
- Each module is independently optional

## Ring 4: Advanced

Power features and experimental modules. These are real capabilities but not required for core adoption. Some are full implementations and some are interface-plus-helpers starting points. None of them are pulled in unless an app explicitly asks for them.

**Modules:** `artemis-privacy`, `artemis-streaming`, `artemis-universal`, `artemis-simulation`, `artemis-batch`, `artemis-scheduler`, `artemis-offline`, `artemis-portfolio`, `artemis-replay`, `artemis-gaming`, `artemis-depin`, `artemis-nlp`, `artemis-intent`, `artemis-preview`

**What it covers (status varies, see [../README.md](../README.md) for the per-module table):**

- Privacy: stealth addresses, encrypted memos, confidential transfers, ring signatures, Shamir secret sharing
- Gaming: session keys, verifiable randomness wrappers, state proof helpers
- Intent: per-program human-readable intent decoders
- Portfolio: live portfolio fetcher and snapshot
- Offline: offline transaction queue with persistent store and retry
- Streaming, simulation, batch, scheduler, replay, depin, nlp, universal, preview: helper-level primitives intended as a starting point for app-specific extensions

**Rules:**

- Advanced modules may depend on Foundation, Mobile, and Ecosystem
- Advanced modules must never leak into the core adoption path
- Nothing in Ring 1, 2, or 3 should ever depend on an advanced module

## Ring 5: Presets

Artemis-native convenience helpers and bundled patterns. These improve ergonomics. They are not legacy API shims.

**Modules:** `artemis-discriminators`, `artemis-nft-compat`, `artemis-tx-presets`, `artemis-candy-machine-presets`, `artemis-presets`

**What it covers:**

- Program discriminator utilities
- NFT metadata parsing and PDA derivation that is independent of `artemis-metaplex`
- Pre-composed transaction patterns: `Artemis.transferToken`, ATA creation plus priority fees plus resend
- Candy Machine mint presets
- Preset registry for composing multiple optional modules

**Rules:**

- May depend on Foundation and Ecosystem
- Nothing critical depends on Presets modules
- Useful, not foundational

## Ring 6: Interop / Legacy shims

Explicit source-compat or migration-compat surface for teams migrating from other SDKs. These are facades over Artemis-native implementations where possible, with partials called out per module. Nothing in the core path depends on this ring.

**Modules:**

- `artemis-mwa-compat` (source-compatible shim for `com.solana.mobilewalletadapter:clientlib-ktx` 1.4.3)
- `artemis-mwa-clientlib-compat` (source-compatible shim for `com.solana.mobilewalletadapter:clientlib` 1.4.3)
- `artemis-mwa-walletlib-compat` (source-compatible shim for `com.solana.mobilewalletadapter:walletlib` 1.4.3)
- `artemis-mwa-common-compat` (source-compatible shim for `com.solana.mobilewalletadapter:common` 1.4.3)
- `artemis-seedvault-compat` (source-compatible shim for `com.solanamobile:seedvault-wallet-sdk` 0.4.0)
- `artemis-sol4k-compat` (source-compatible shim for `org.sol4k:sol4k` 0.7.0; includes Token-2022 instructions and the upstream `RpcException` data-class shape)
- `artemis-solana-kmp-compat` (source-compatible shim for `foundation.metaplex:solana-kmp`, snapshot of upstream `main`@2024-06-05; upstream dormant)
- `artemis-metaplex-android-compat` (source-compatible shim for `com.metaplex.lib:lib`, snapshot of upstream `main`@2024-04-06; upstream dormant; Partial coverage)
- `artemis-rpc-core-compat` (source-compatible shim for `com.solana:rpc-core`, snapshot @2026-01-09; `ArtemisHttpNetworkDriver` replaces concrete Ktor/Okio driver FQNs)
- `artemis-web3-solana-compat` (source-compatible shim for `com.solana:web3-solana` (Funkatronics), snapshot @2025-08; newer web3-core 0.3.x additions remain partial)

**What it covers:**

- Source compatibility wrappers for the upstream MWA clientlib + walletlib + common packages, sol4k, solana-kmp, Metaplex Android, rpc-core, and web3-solana
- Source compatibility wrappers for the Seed Vault static surface (`com.solanamobile.seedvault.Wallet.*` + `WalletContractV1`)
- Migration helpers with documented upstream version pins (`extra["upstream.version"]`) and scope coverage in `docs/PARITY_MATRIX.md` plus `docs/SOLANA_MOBILE_CLIENT_COMPATIBILITY_AUDIT.md`

**Rules:**

- May depend on Foundation, Mobile, and Ecosystem
- Cannot depend on Advanced or Presets
- Nothing else depends on Interop modules
- Shims wrap public APIs only. No copied internals.
- Every shimmed class and method has a compile-proof test
- Compatibility targets pinned in `build.gradle.kts` (`extra["upstream.version"]`) and gated by CI's `verifyApiSnapshots` task. `dumpApi` must produce the same output the committed `interop/<module>/api/<module>.api` snapshot carries, otherwise the build fails

## Directory layout

```text
selenus-artemis-solana-sdk/
|-- foundation/              Ring 1 (KMP)
|   |-- artemis-core/
|   |   |-- src/commonMain/kotlin/   Portable code
|   |   |-- src/commonTest/kotlin/   Portable tests
|   |   |-- src/jvmMain/kotlin/      JVM and Android actuals
|   |   `-- src/jvmTest/kotlin/      JVM-specific tests
|   |-- artemis-rpc/
|   |-- artemis-ws/
|   |-- artemis-tx/
|   |-- artemis-vtx/
|   |-- artemis-programs/
|   |-- artemis-errors/
|   |-- artemis-logging/
|   `-- artemis-compute/
|-- mobile/                  Ring 2
|   |-- artemis-wallet/
|   |-- artemis-wallet-mwa-android/
|   `-- artemis-seed-vault/
|-- ecosystem/               Ring 3
|   |-- artemis-token2022/
|   |-- artemis-metaplex/
|   |-- artemis-mplcore/
|   |-- artemis-cnft/
|   |-- artemis-candy-machine/
|   |-- artemis-solana-pay/
|   |-- artemis-anchor/
|   |-- artemis-jupiter/
|   `-- artemis-actions/
|-- advanced/                Ring 4
|   |-- artemis-privacy/
|   |-- artemis-streaming/
|   |-- artemis-universal/
|   `-- ...
|-- compatibility/           Ring 5 (Presets)
|   |-- artemis-discriminators/
|   |-- artemis-nft-compat/
|   |-- artemis-tx-presets/
|   |-- artemis-candy-machine-presets/
|   `-- artemis-presets/
|-- interop/                 Ring 6
|   |-- artemis-mwa-compat/
|   `-- artemis-seedvault-compat/
|-- testing/
|   |-- artemis-integration-tests/
|   `-- artemis-devnet-tests/
|-- samples/
|   `-- solana-mobile-compose-mint-app/
`-- docs/
```

## What a typical mobile app needs

Most Android Solana apps need exactly this:

```kotlin
// Foundation
implementation("xyz.selenus:artemis-core:2.3.1")
implementation("xyz.selenus:artemis-rpc:2.3.1")
implementation("xyz.selenus:artemis-ws:2.3.1")
implementation("xyz.selenus:artemis-tx:2.3.1")
implementation("xyz.selenus:artemis-vtx:2.3.1")
implementation("xyz.selenus:artemis-programs:2.3.1")

// Mobile
implementation("xyz.selenus:artemis-wallet:2.3.1")
implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.1")
```

Add ecosystem modules based on what the app does:

```kotlin
// Token-2022
implementation("xyz.selenus:artemis-token2022:2.3.1")

// Jupiter swaps
implementation("xyz.selenus:artemis-jupiter:2.3.1")

// NFTs, DAS, marketplace
implementation("xyz.selenus:artemis-metaplex:2.3.1")
implementation("xyz.selenus:artemis-cnft:2.3.1")
```

The current published version is `2.3.1`. The `version` field in [../gradle.properties](../gradle.properties) is the source of truth.

Everything else is optional. You pull it in when you need it.
