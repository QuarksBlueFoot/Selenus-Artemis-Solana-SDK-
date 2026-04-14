# Artemis Solana SDK

Kotlin Multiplatform SDK for Solana. Covers RPC, transactions, wallet signing, WebSocket subscriptions, NFT/DAS queries, and Android Mobile Wallet Adapter in one dependency set. No fragmented third-party SDK stack required.

[![Maven Central](https://img.shields.io/maven-central/v/xyz.selenus/artemis-core?style=flat-square)](https://central.sonatype.com/search?q=xyz.selenus)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)

## What Artemis provides

- Solana core types, serialization, and Ed25519 cryptographic primitives
- JSON-RPC client: 65+ methods, typed wrappers, batch DSL, endpoint pool with circuit breaker
- WebSocket subscriptions with `RealtimeEngine`: typed account, signature, and program callbacks
- Legacy and versioned (v0) transaction construction and signing
- Wallet abstraction: local keypair, MWA adapter, raw signer, unified `WalletSession`
- Android Mobile Wallet Adapter 2.0 integration
- Seed Vault integration for hardware-backed key custody on Saga
- DAS (Digital Asset Standard) queries via `ArtemisDas` and `HeliusDas`
- Compressed NFT (cNFT) Bubblegum transfers via `MarketplaceEngine`
- Ecosystem modules for Token-2022, Metaplex, Jupiter, Anchor, and more
- `ArtemisMobile.create()`: single call wires RPC, wallet, TxEngine, realtime, DAS, and marketplace

## What Artemis replaces

For covered capabilities, you do not need separate dependencies:

- **solana-kmp** (Funkatronics/Metaplex): RPC, public keys, transactions
- **sol4k**: JVM Solana primitives
- **mobile-wallet-adapter-clientlib-ktx**: MWA 2.0 protocol
- **seedvault-wallet-sdk**: Seed Vault integration
- **Metaplex KMM**: token metadata and NFT operations (where covered)

See [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md) for the full feature comparison.

## Architecture

Six rings with strict downward-only dependencies. Foundation is KMP. Mobile is Android-native where required. Ecosystem and advanced modules never leak into the core path.

See [docs/ARCHITECTURE_OVERVIEW.md](docs/ARCHITECTURE_OVERVIEW.md) for the full breakdown.

```
Ring 1: Foundation    core, rpc, ws, tx, vtx, programs, errors, logging, compute       [KMP]
Ring 2: Mobile        wallet [KMP], wallet-mwa-android [Android], seed-vault [Android]
Ring 3: Ecosystem     token2022, metaplex, mplcore, cnft, candy-machine, solana-pay, anchor, jupiter, actions  [KMP]
Ring 4: Advanced      privacy, streaming, simulation, batch, scheduler, offline, portfolio, replay, gaming, depin, nlp, intent, universal, preview
Ring 5: Compat        discriminators, nft-compat, tx-presets, candy-machine-presets, presets
```

## Modules

### Foundation

| Module | Purpose |
|--------|---------|
| `artemis-core` | Pubkey, Keypair, Base58, PDA derivation, Ed25519, SHA-256, Base64 |
| `artemis-rpc` | JSON-RPC client: 65+ methods, typed wrappers, batch DSL, endpoint pool, circuit breaker, blockhash cache |
| `artemis-ws` | WebSocket client with auto-reconnect, polling fallback, and `RealtimeEngine` for typed subscriptions |
| `artemis-tx` | Legacy transaction construction, serialization, signing, durable nonce |
| `artemis-vtx` | Versioned transactions (v0), address lookup tables |
| `artemis-programs` | System (12 ix), Token, Associated Token, Compute Budget, Stake (5 ix) |
| `artemis-errors` | Structured error types, on-chain error decoding |
| `artemis-logging` | Lightweight structured logging |
| `artemis-compute` | Compute unit estimation, priority fee helpers |

### Mobile

| Module | Purpose |
|--------|---------|
| `artemis-wallet` | Wallet abstraction: Local, Adapter, Raw signing via `WalletSession` |
| `artemis-wallet-mwa-android` | MWA 2.0 client for Android; `ArtemisMobile.create()` for full-stack setup |
| `artemis-seed-vault` | Saga Seed Vault integration for hardware-backed key custody |

### Ecosystem

| Module | Purpose |
|--------|---------|
| `artemis-token2022` | Token-2022 extensions: transfer fees, interest bearing, metadata, confidential transfers, CPI guard |
| `artemis-metaplex` | Token Metadata: metadata, editions, collections |
| `artemis-mplcore` | MPL Core (Asset) program |
| `artemis-cnft` | Compressed NFTs: Bubblegum transfers, `ArtemisDas` interface, `HeliusDas` DAS client, `MarketplaceEngine` |
| `artemis-candy-machine` | Candy Machine v3 minting |
| `artemis-solana-pay` | Solana Pay protocol |
| `artemis-anchor` | Anchor IDL parsing, Borsh serialization, type-safe program client |
| `artemis-jupiter` | Jupiter DEX: quotes, swaps, route optimization |
| `artemis-actions` | Solana Actions and Blinks |

### Advanced

| Module | Purpose |
|--------|---------|
| `artemis-privacy` | Stealth addresses, encrypted memos, confidential transfers |
| `artemis-streaming` | Zero-copy account streaming via WebSocket |
| `artemis-simulation` | Transaction simulation and analysis |
| `artemis-batch` | Automatic transaction batching |
| `artemis-scheduler` | Network-aware transaction scheduling |
| `artemis-offline` | Offline transaction queue with automatic retry |
| `artemis-portfolio` | Live WebSocket portfolio tracking |
| `artemis-replay` | Transaction replay and analysis |
| `artemis-gaming` | Session keys, verifiable randomness, state proofs |
| `artemis-depin` | DePIN device attestation |
| `artemis-nlp` | Natural language transaction parsing |
| `artemis-intent` | Human-readable transaction intent decoding |
| `artemis-universal` | IDL-less program discovery and interaction |
| `artemis-preview` | Transaction preview CLI: simulates and renders effects |

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.2.0")
    implementation("xyz.selenus:artemis-rpc:2.2.0")
    implementation("xyz.selenus:artemis-tx:2.2.0")
    implementation("xyz.selenus:artemis-vtx:2.2.0")
    implementation("xyz.selenus:artemis-programs:2.2.0")

    // Mobile (Android)
    implementation("xyz.selenus:artemis-wallet:2.2.0")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.2.0")
    implementation("xyz.selenus:artemis-seed-vault:2.2.0")

    // NFT + DAS + marketplace
    implementation("xyz.selenus:artemis-cnft:2.2.0")

    // Ecosystem (add as needed)
    implementation("xyz.selenus:artemis-token2022:2.2.0")
    implementation("xyz.selenus:artemis-jupiter:2.2.0")
}
```

## Quick start

### Full mobile stack in one call

`ArtemisMobile.create()` wires RPC, MWA wallet, TxEngine, realtime WebSocket, DAS, and marketplace together.

```kotlin
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico",  // must be absolute HTTPS
    identityName = "My App",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"  // null = no DAS
)

artemis.wallet.connect()
val result = artemis.session.sendSol(recipient, 1_000_000_000L)
```

### Real-time account subscriptions

```kotlin
artemis.realtime.connect()

val handle = artemis.realtime.subscribeAccount(
    pubkey = artemis.session.publicKey.toBase58(),
    commitment = "confirmed"
) { info ->
    println("lamports: ${info.lamports}, slot: ${info.slot}")
}

// Signature confirmation
artemis.realtime.subscribeSignature(txSignature) { confirmed ->
    println("confirmed: $confirmed")
}

// Unsubscribe
handle.close()
artemis.realtime.close()
```

### NFT queries via DAS

```kotlin
// All NFTs owned by a wallet
val nfts: List<DigitalAsset> = artemis.das?.assetsByOwner(walletPubkey) ?: emptyList()

// Single asset lookup
val asset: DigitalAsset? = artemis.das?.asset("GdR7...")

// Collection assets
val collection = artemis.das?.assetsByCollection("CollMintAddress...")
```

### Transfer a compressed NFT

```kotlin
val result = artemis.marketplace.transferCnft(
    wallet     = artemis.wallet,
    dasClient  = myDasClient,
    assetId    = "GdR7...",
    merkleTree = Pubkey.fromBase58("tree..."),
    newOwner   = recipientPubkey
)
println("signature: ${result.signature}, confirmed: ${result.confirmed}")
```

### Build and send a transaction

```kotlin
val ix = SystemProgram.transfer(
    from     = wallet.publicKey,
    to       = recipient,
    lamports = 500_000_000L
)

val signature = wallet.send(ix)
```

### Transaction engine with custom config

```kotlin
val engine = TxEngine(rpc)

val result = engine.execute(
    instructions = listOf(ix),
    signer       = keypair,
    config       = TxConfig(retries = 3, simulate = true, computeUnitPrice = 1000L)
)
```

### Versioned transaction with lookup table

```kotlin
val blockhash = rpc.getLatestBlockhash()

val vtx = versionedTransaction(signer, blockhash) {
    addInstruction(ix)
    withLookupTables(lookupTableAccount)
}
```

## Documentation

| Document | What it covers |
|----------|---------------|
| [Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md) | Ring structure, dependency rules, module hierarchy |
| [Module Map](docs/MODULE_MAP.md) | Every module, ring, purpose, and adoption context |
| [Parity Matrix](docs/PARITY_MATRIX.md) | Feature-by-feature comparison vs solana-kmp, sol4k, Solana Mobile SDK, Metaplex KMM |
| [Solana Mobile Migration](docs/REPLACE_SOLANA_MOBILE_STACK.md) | Migration from Solana Mobile Stack to Artemis |
| [Mobile App Guide](docs/MOBILE_APP_GUIDE.md) | Android mobile app integration walkthrough |
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

