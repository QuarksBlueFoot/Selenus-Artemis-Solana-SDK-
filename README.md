# Artemis Solana SDK

Mobile-first Kotlin Multiplatform SDK for Solana. Portable foundation layer (KMP) for RPC, transactions, wallet integration, and Android mobile wallet support. Optional modules for tokens, NFTs, DeFi, privacy, gaming, and advanced tooling.

[![Maven Central](https://img.shields.io/maven-central/v/xyz.selenus/artemis-core?style=flat-square)](https://central.sonatype.com/search?q=xyz.selenus)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)

## What Artemis provides

- Solana core types, serialization, and cryptographic primitives
- JSON-RPC + WebSocket clients with retry, failover, and batching
- Transaction and versioned transaction model (v0 + legacy)
- Wallet abstraction layer with pluggable signing strategies
- Android Mobile Wallet Adapter 2.0 integration
- Seed Vault integration for secure key custody
- Common program clients (System, Token, Associated Token, Compute Budget, Stake)
- Optional ecosystem modules for Token-2022, Metaplex, Jupiter, and more

## What Artemis replaces

For covered capabilities, you do not need separate dependencies such as:

- **solana-kmp** (Funkatronics/Metaplex) — RPC, public keys, transactions
- **sol4k** — JVM Solana primitives
- **mobile-wallet-adapter-clientlib-ktx** — MWA protocol
- **seedvault-wallet-sdk** — Seed Vault integration
- **Metaplex KMM** — token metadata and NFT operations (where covered)

See [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md) for the full feature comparison.

## Architecture

Artemis is organized into five rings. Each ring has strict dependency rules. Foundation modules never depend on ecosystem or advanced modules. Mobile depends only on foundation. Advanced modules never leak into the core adoption path.

See [docs/ARCHITECTURE_OVERVIEW.md](docs/ARCHITECTURE_OVERVIEW.md) for the full breakdown.

```
Ring 1: Foundation    core, rpc, ws, tx, vtx, programs, errors, logging, compute       [KMP]
Ring 2: Mobile        wallet [KMP], wallet-mwa-android [Android], seed-vault [Android]
Ring 3: Ecosystem     token2022, metaplex, mplcore, cnft, candy-machine, solana-pay, anchor, jupiter, actions  [KMP]
Ring 4: Advanced      privacy, streaming, simulation, batch, scheduler, offline, portfolio, replay, gaming, depin, nlp, intent, universal, preview
Ring 5: Compat        discriminators, nft-compat, tx-presets, candy-machine-presets, presets
```

## Modules

### Foundation (required for most apps)

| Module | Purpose |
|--------|---------|
| `artemis-core` | PublicKey, Keypair, Base58, PDA derivation, cryptographic primitives |
| `artemis-rpc` | JSON-RPC client, broad method coverage, typed wrappers, batch DSL, endpoint pool with circuit breaker |
| `artemis-ws` | WebSocket subscriptions, auto-reconnect, polling fallback |
| `artemis-tx` | Legacy transaction construction, serialization, signing, durable nonce support |
| `artemis-vtx` | Versioned transactions (v0), address lookup tables |
| `artemis-programs` | System, Token, Associated Token, Compute Budget, Stake program instructions |
| `artemis-errors` | Structured error types, on-chain error decoding |
| `artemis-logging` | Lightweight structured logging |
| `artemis-compute` | Compute unit estimation, priority fee optimization |

### Mobile (Solana Mobile Stack replacement)

| Module | Purpose |
|--------|---------|
| `artemis-wallet` | Wallet abstraction: Local, Adapter, and Raw signing strategies via WalletSession |
| `artemis-wallet-mwa-android` | Mobile Wallet Adapter 2.0 client for Android |
| `artemis-seed-vault` | Saga Seed Vault integration for hardware-backed key custody |

### Ecosystem (optional protocol clients)

| Module | Purpose |
|--------|---------|
| `artemis-token2022` | Token-2022 extensions: transfer fees, interest bearing, metadata, confidential transfers, CPI guard |
| `artemis-metaplex` | Token Metadata program: metadata, editions, collections |
| `artemis-mplcore` | MPL Core (Asset) program support |
| `artemis-cnft` | Compressed NFTs via Bubblegum |
| `artemis-candy-machine` | Candy Machine v3 minting |
| `artemis-solana-pay` | Solana Pay protocol |
| `artemis-anchor` | Anchor IDL parsing, Borsh serialization, type-safe program client |
| `artemis-jupiter` | Jupiter DEX aggregator: quotes, swaps, route optimization |
| `artemis-actions` | Solana Actions and Blinks |

### Advanced (optional, not required for core adoption)

| Module | Purpose |
|--------|---------|
| `artemis-privacy` | Stealth addresses, encrypted memos, confidential transfers |
| `artemis-streaming` | Zero-copy account streaming via WebSocket |
| `artemis-simulation` | Transaction simulation and analysis |
| `artemis-batch` | Automatic transaction batching |
| `artemis-scheduler` | Network-aware transaction scheduling |
| `artemis-offline` | Offline transaction queue with automatic retry |
| `artemis-portfolio` | Real-time portfolio tracking via WebSocket |
| `artemis-replay` | Transaction replay and analysis |
| `artemis-gaming` | Session keys, verifiable randomness, state proofs |
| `artemis-depin` | DePIN device attestation |
| `artemis-nlp` | Natural language transaction parsing |
| `artemis-intent` | Human-readable transaction intent decoding |
| `artemis-universal` | IDL-less program discovery and interaction |
| `artemis-preview` | Transaction preview CLI: simulates and renders transaction effects |

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Foundation (pick what you need)
    implementation("xyz.selenus:artemis-core:2.1.1")
    implementation("xyz.selenus:artemis-rpc:2.1.1")
    implementation("xyz.selenus:artemis-tx:2.1.1")
    implementation("xyz.selenus:artemis-vtx:2.1.1")
    implementation("xyz.selenus:artemis-programs:2.1.1")

    // Mobile wallet (Android)
    implementation("xyz.selenus:artemis-wallet:2.1.1")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.1.1")
    implementation("xyz.selenus:artemis-seed-vault:2.1.1")

    // Add ecosystem/advanced modules as needed
    implementation("xyz.selenus:artemis-token2022:2.1.1")
    implementation("xyz.selenus:artemis-jupiter:2.1.1")
}
```

## Quick start

### Connect wallet and send SOL

```kotlin
val client = ArtemisClient {
    rpc = "https://api.mainnet-beta.solana.com"
    commitment = Commitment.CONFIRMED
}

// Connect via Mobile Wallet Adapter
val adapter = MwaWalletAdapter(
    activity = this,
    identityUri = Uri.parse("https://myapp.example.com"),
    iconPath = "https://myapp.example.com/favicon.ico",
    identityName = "My App"
)
adapter.connect()
val wallet = WalletSession.fromAdapter(adapter, client.engine())

// Send SOL
val signature = wallet.sendSol(
    to = recipient,
    lamports = 1_000_000_000L
)
```

### Build and send a transaction

```kotlin
val ix = SystemProgram.transfer(
    from = wallet.publicKey,
    to = recipient,
    lamports = 500_000_000L
)

// Option 1: Send via WalletSession (uses TxEngine pipeline)
val signature = wallet.send(ix)

// Option 2: Build a full transaction with the DSL
val tx = artemisTransaction {
    feePayer(wallet.publicKey)
    instruction(ix)
}
```

### Use the transaction engine

```kotlin
val engine = client.engine()

val result = engine.builder()
    .add(ix)
    .config {
        simulate = true
        retries = 3
        computeUnitPrice = 1000L
    }
    .send(signer)
```

### Versioned transaction with lookup table

```kotlin
val signer = wallet.signer()
val blockhash = client.rpc().getLatestBlockhash()

val vtx = versionedTransaction(signer, blockhash) {
    addInstruction(ix)
    withLookupTables(lookupTableAccount)
}
```

## Documentation

| Document | What it covers |
|----------|---------------|
| [Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md) | Ring structure, dependency rules, module hierarchy |
| [Module Map](docs/MODULE_MAP.md) | Every module, its ring, purpose, and adoption context |
| [Parity Matrix](docs/PARITY_MATRIX.md) | Feature-by-feature comparison vs solana-kmp, sol4k, Solana Mobile SDK, Metaplex KMM |
| [Solana Mobile Migration](docs/REPLACE_SOLANA_MOBILE_STACK.md) | Step-by-step migration from Solana Mobile Stack to Artemis |
| [Dependency Rules](docs/DEPENDENCY_RULES.md) | What can depend on what, and why |
| [Adoption Bundles](docs/ADOPTION_BUNDLES.md) | Recommended dependency sets for common use cases |
| [Contributing](CONTRIBUTING.md) | How to contribute |
| [Changelog](CHANGELOG.md) | Release history |

## Build

```bash
./gradlew build
```

```bash
./gradlew test
```

```bash
./run-devnet-tests.sh
```

## License

Apache License 2.0. See [LICENSE](LICENSE).

**Maintained by [Bluefoot Labs](https://bluefootlabs.xyz)**

