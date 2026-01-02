# Architecture Overview

Artemis is organized into six rings. Each ring has a clear purpose and strict dependency rules. This is not a suggestion. It is how the repo is structured.

## Platform strategy

**Ring 1 (Foundation) is Kotlin Multiplatform.** Common code compiles for JVM, Android, and future targets. No `java.*` imports in common source sets. Platform-specific implementations (crypto, transport, encoding) live behind `expect/actual` seams in platform source sets.

**Ring 2 (Mobile) stays platform-native where appropriate.** `artemis-wallet` is KMP-safe as the portable signing abstraction. `artemis-wallet-mwa-android` and `artemis-seed-vault` remain Android-only.

**Rings 3–5** sit on top of the portable foundation and migrate to KMP in waves.

**Ring 6 (Interop)** provides explicit legacy compatibility shims. Thin wrappers only.

## The rings

```
┌─────────────────────────────────────────────────────────────────┐
│                        Ring 6: Interop                          │
│   legacy shims (solana-mobile-compat, seedvault-compat, etc.)  │
├─────────────────────────────────────────────────────────────────┤
│                        Ring 5: Presets                           │
│   discriminators, nft-compat, tx-presets, presets               │
├─────────────────────────────────────────────────────────────────┤
│                        Ring 4: Advanced                         │
│   privacy, streaming, simulation, batch, scheduler, offline,   │
│   portfolio, replay, gaming, depin, nlp, intent, universal     │
├─────────────────────────────────────────────────────────────────┤
│                        Ring 3: Ecosystem                        │
│   token2022, metaplex, mplcore, cnft, candy-machine,           │
│   solana-pay, anchor, jupiter, actions                         │
├─────────────────────────────────────────────────────────────────┤
│                        Ring 2: Mobile                           │
│   wallet, wallet-mwa-android, seed-vault                       │
├─────────────────────────────────────────────────────────────────┤
│                        Ring 1: Foundation (KMP)                  │
│   core, rpc, ws, tx, vtx, programs, errors, logging, compute   │
└─────────────────────────────────────────────────────────────────┘
```

Higher rings may depend on lower rings. Lower rings never depend on higher rings. That is the whole model.

## Allowed dependency matrix

| From \ To | Foundation | Mobile | Ecosystem | Advanced | Presets | Interop |
|-----------|-----------|--------|-----------|----------|---------|---------|
| **Foundation** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Mobile** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Ecosystem** | ✅ | ✅ (wallet interface only) | ✅ | ❌ | ❌ | ❌ |
| **Advanced** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Presets** | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ |
| **Interop** | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |

Arrows point downward only. Any edge going upward is a bug.

## Ring 1: Foundation (KMP)

This is the base layer. Every Solana app built with Artemis uses some or all of these modules. **Foundation is Kotlin Multiplatform.** Common code contains no `java.*` imports and compiles for JVM, Android, and future targets. Platform-specific crypto, transport, and encoding live in `jvmMain`/`androidMain` source sets behind `expect/actual` seams.

**Modules:** `artemis-core`, `artemis-rpc`, `artemis-ws`, `artemis-tx`, `artemis-vtx`, `artemis-programs`, `artemis-errors`, `artemis-logging`, `artemis-compute`

**What it covers:**
- PublicKey, Keypair, PDA derivation, Base58/Base64 encoding
- Full JSON-RPC client (broad method coverage, typed wrappers, batch DSL)
- WebSocket subscriptions with auto-reconnect and polling fallback
- Legacy and versioned (v0) transaction construction and serialization
- Address lookup tables
- System, Token, Associated Token, Compute Budget, Stake program instructions
- Compute unit estimation and priority fee helpers
- Structured error types and on-chain error decoding
- Durable nonce support
- Blockhash lifecycle management

**Rules:**
- Foundation modules depend only on other foundation modules
- No dependencies on ecosystem, mobile, advanced, or compatibility modules
- Must remain stable, lightweight, and dependency-minimal
- These modules are the primary replacement path for covered low-level functionality currently spread across solana-kmp, sol4k, and basic Solana Mobile dependencies

## Ring 2: Mobile

This is the Solana Mobile Stack replacement layer. If you are building an Android Solana app, this ring plus foundation is your core dependency set.

**Modules:** `artemis-wallet`, `artemis-wallet-mwa-android`, `artemis-seed-vault`

**What it covers:**
- Wallet abstraction with pluggable signing strategies (Local keypair, MWA Adapter, Raw signer)
- Mobile Wallet Adapter 2.0 client
- Seed Vault integration for Saga hardware-backed key custody
- Convenience methods: `sendSol()`, `sendToken()`
- Android lifecycle safety

**Rules:**
- Mobile modules depend on Foundation only
- Never depend on ecosystem or advanced modules
- This ring exists so mobile teams can adopt Artemis without pulling in the entire SDK

## Ring 3: Ecosystem

Optional protocol clients built on top of Foundation. Use them when you need specific on-chain integrations.

**Modules:** `artemis-token2022`, `artemis-metaplex`, `artemis-mplcore`, `artemis-cnft`, `artemis-candy-machine`, `artemis-solana-pay`, `artemis-anchor`, `artemis-jupiter`, `artemis-actions`

**What it covers:**
- Token-2022 extensions (transfer fees, interest bearing, metadata pointer, confidential transfers, CPI guard, and more)
- Token Metadata, editions, collections
- MPL Core (Asset) program
- Compressed NFTs via Bubblegum
- Candy Machine v3
- Solana Pay protocol
- Anchor IDL parsing, Borsh serialization, type-safe program client
- Jupiter DEX aggregator (quotes, swaps, routing)
- Solana Actions / Blinks

**Rules:**
- Ecosystem modules depend on Foundation
- May depend on selected Mobile abstractions (e.g., wallet signing interface) if needed
- Should not depend on advanced modules
- Each module is independently optional

## Ring 4: Advanced

Power features and experimental modules. These are real capabilities, not required for core adoption. Teams that need them know they need them.

**Modules:** `artemis-privacy`, `artemis-streaming`, `artemis-universal`, `artemis-simulation`, `artemis-batch`, `artemis-scheduler`, `artemis-offline`, `artemis-portfolio`, `artemis-replay`, `artemis-gaming`, `artemis-depin`, `artemis-nlp`, `artemis-intent`, `artemis-preview`

**What it covers:**
- Privacy: stealth addresses, encrypted memos, confidential transfers
- Zero-copy account streaming
- Transaction simulation and analysis
- Automatic transaction batching
- Network-aware transaction scheduling
- Offline transaction queuing
- Real-time portfolio tracking
- Transaction replay
- Gaming: session keys, verifiable randomness, state proofs
- DePIN device attestation
- Natural language transaction parsing
- Human-readable transaction intent decoding
- IDL-less program discovery
- Transaction preview CLI (simulates and renders transaction effects)

**Rules:**
- Advanced modules may depend on Foundation, Mobile, and Ecosystem
- Advanced modules must never leak into the core adoption path
- Nothing in Ring 1, 2, or 3 should ever depend on an advanced module

## Ring 5: Presets

Artemis-native convenience helpers and bundled patterns. These improve ergonomics. They are not legacy API shims.

**Modules:** `artemis-discriminators`, `artemis-nft-compat`, `artemis-tx-presets`, `artemis-candy-machine-presets`, `artemis-presets`

**What it covers:**
- Program discriminator utilities
- NFT compatibility layer across Metaplex, MPL Core, and cNFT
- Pre-composed transaction patterns (ATA creation + priority fees + resend)
- Candy Machine mint presets
- Preset registry for composing multiple optional modules

**Rules:**
- May depend on Foundation and Ecosystem
- Nothing critical depends on Presets modules
- Useful, not foundational

## Ring 6: Interop / Legacy Shims

Explicit source-compat or migration-compat surface for teams migrating from other SDKs. Thin wrappers only. Nothing depends on this ring.

**Modules (planned):** `artemis-solana-mobile-compat`, `artemis-seedvault-compat`

**What it covers:**
- Source compatibility wrappers for selected Solana Mobile SDK APIs
- Source compatibility wrappers for selected Seed Vault APIs
- Migration helpers with documented version/scope coverage

**Rules:**
- May depend on Foundation, Mobile, and Ecosystem
- Cannot depend on Advanced or Presets
- Nothing else depends on Interop modules
- Shims wrap public APIs only — no copied internals
- Every shimmed class/method must have a compile-proof test
- Compatibility targets documented by version and scope

## Directory layout

```
selenus-artemis-solana-sdk/
├── foundation/          Ring 1 (KMP)
│   ├── artemis-core/
│   │   ├── src/commonMain/kotlin/    Portable code
│   │   ├── src/commonTest/kotlin/    Portable tests
│   │   ├── src/jvmMain/kotlin/       JVM/Android actuals
│   │   └── src/jvmTest/kotlin/       JVM-specific tests
│   ├── artemis-rpc/
│   ├── artemis-ws/
│   ├── artemis-tx/
│   ├── artemis-vtx/
│   ├── artemis-programs/
│   ├── artemis-errors/
│   ├── artemis-logging/
│   └── artemis-compute/
├── mobile/              Ring 2
│   ├── artemis-wallet/
│   ├── artemis-wallet-mwa-android/
│   └── artemis-seed-vault/
├── ecosystem/           Ring 3
│   ├── artemis-token2022/
│   ├── artemis-metaplex/
│   ├── artemis-mplcore/
│   ├── artemis-cnft/
│   ├── artemis-candy-machine/
│   ├── artemis-solana-pay/
│   ├── artemis-anchor/
│   ├── artemis-jupiter/
│   └── artemis-actions/
├── advanced/            Ring 4
│   ├── artemis-privacy/
│   ├── artemis-streaming/
│   ├── artemis-universal/
│   └── ... (14 modules)
├── compatibility/       Ring 5 (Presets)
│   ├── artemis-discriminators/
│   ├── artemis-nft-compat/
│   ├── artemis-tx-presets/
│   ├── artemis-candy-machine-presets/
│   └── artemis-presets/
├── interop/             Ring 6 (planned)
│   ├── artemis-solana-mobile-compat/
│   └── artemis-seedvault-compat/
├── testing/
│   ├── artemis-integration-tests/
│   └── artemis-devnet-tests/
├── samples/
│   └── solana-mobile-compose-mint-app/
└── docs/
```

## What a typical mobile app needs

Most Android Solana apps need exactly this:

```kotlin
// Foundation
implementation("xyz.selenus:artemis-core:2.1.1")
implementation("xyz.selenus:artemis-rpc:2.1.1")
implementation("xyz.selenus:artemis-tx:2.1.1")
implementation("xyz.selenus:artemis-programs:2.1.1")

// Mobile
implementation("xyz.selenus:artemis-wallet:2.1.1")
implementation("xyz.selenus:artemis-wallet-mwa-android:2.1.1")
```

Add versioned transactions if your app uses address lookup tables:

```kotlin
implementation("xyz.selenus:artemis-vtx:2.1.1")
```

Add ecosystem modules based on what your app does:

```kotlin
// Token-2022 support
implementation("xyz.selenus:artemis-token2022:2.1.1")

// Jupiter swaps
implementation("xyz.selenus:artemis-jupiter:2.1.1")

// NFT operations
implementation("xyz.selenus:artemis-metaplex:2.1.1")
implementation("xyz.selenus:artemis-cnft:2.1.1")
```

Everything else is optional. You pull it in when you need it.
