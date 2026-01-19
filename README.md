# Selenus Artemis Kotlin Solana SDK

> **Modular. Drop-in. Mobile-first. No paid assumptions.**

**Maintained by [Bluefoot Labs](https://bluefootlabs.com) and [Selenus](https://selenus.xyz).**

Artemis is the modular Kotlin Solana SDK I wish I had when I started building for Android. It's built for 2025, so you're not stuck patching legacy code.

It hits all the modern Solana patterns out of the box:

- v0 transactions and ALT workflows (because legacy txs are painful)
- Token-2022 with TLV decoding
- Compressed NFTs (Bubblegum-compatible utilities)
- MPL Core (v2 lane) create flows and marketplace utilities
- **Native Android Seed Vault & MWA implementation** (No wrappers)
- If you need Helius stuff, check out LunaSDK. Artemis stays pure.

## Build

Run:

```bash
./gradlew build
```

### Android sample app (optional)

v64 includes an optional Solana Mobile Candy Machine mint sample app. It is excluded from the default build.

```bash
./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
```

## Install

Add `mavenCentral()` to your repositories, then add the modules you need:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core
    implementation("xyz.selenus:artemis-core:1.3.0")
    implementation("xyz.selenus:artemis-rpc:1.3.0")
    implementation("xyz.selenus:artemis-core:1.3.0")

    // Transaction Building
    implementation("xyz.selenus:artemis-tx:1.3.0")
    implementation("xyz.selenus:artemis-vtx:1.3.0") // Versioned transactions & ALTs
    implementation("xyz.selenus:artemis-tx-presets:1.3.0")
    implementation("xyz.selenus:artemis-programs:1.3.0") // System, Token, Token2022 program builders
    implementation("xyz.selenus:artemis-compute:1.3.0") // Compute budget utilities
    implementation("xyz.selenus:artemis-presets:1.3.0")

    // Tokens
    implementation("xyz.selenus:artemis-token2022:1.3.0")

    // NFT & Metaplex
    implementation("xyz.selenus:artemis-metaplex:1.3.0") // Token Metadata Program
    implementation("xyz.selenus:artemis-mplcore:1.3.0")  // MPL Core v2
    implementation("xyz.selenus:artemis-cnft:1.3.0") // Compressed NFTs (Bubblegum)
    implementation("xyz.selenus:artemis-nft-compat:1.3.0") // Cross-standard NFT helpers
    implementation("xyz.selenus:artemis-candy-machine:1.3.0")
    implementation("xyz.selenus:artemis-candy-machine-presets:1.3.0")

    // Mobile Features
    implementation("xyz.selenus:artemis-seed-vault:1.3.0") // Pure Kotlin Seed Vault
    implementation("xyz.selenus:artemis-wallet-mwa-android:1.3.0") // Native MWA 2.0
    implementation("xyz.selenus:artemis-wallet:1.3.0") // Wallet abstractions
    implementation("xyz.selenus:artemis-solana-pay:1.3.0")

    // React Native: npm install artemis-solana-sdk

    // Real-time & WebSocket
    implementation("xyz.selenus:artemis-ws:1.3.0") // WebSocket subscriptions

    // Utilities
    implementation("xyz.selenus:artemis-discriminators:1.3.0")
    implementation("xyz.selenus:artemis-errors:1.3.0")
    implementation("xyz.selenus:artemis-logging:1.3.0")
    implementation("xyz.selenus:artemis-privacy:1.3.0")

    // Gaming & DePIN
    implementation("xyz.selenus:artemis-gaming:1.3.0")
    implementation("xyz.selenus:artemis-depin:1.3.0")
    implementation("xyz.selenus:artemis-replay:1.3.0") // Session recording & playback
}
```

## Quick start

### 1) Basic RPC

Artemis uses Kotlin Coroutines for all network operations.

```kotlin
// Create the transport layer
val client = JsonRpcClient("https://api.mainnet-beta.solana.com")

// Wrap it in the API surface
val api = RpcApi(client)

// Usage (must be in a suspend function or coroutine scope)
val blockhash = api.getLatestBlockhash()
val balance = api.getBalance("...pubkey...")
```

### 2) Token-2022 TLV decode

```kotlin
val parsed = Token2022Tlv.decodeMint(accountData)
val transferFee = parsed.extensions.transferFeeConfig
```

### 3) cNFT transfer using DAS proof

```kotlin
val das = DasClient(client) // Requires JsonRpcClient
val asset = das.getAsset(assetId)
val proof = das.getAssetProof(assetId)

val proofArgs = DasProofParser.parseProofArgs(asset, proof)
val proofAccounts = DasProofParser.proofAccountsFromProof(proof)

val ix = BubblegumInstructions.transfer(
  treeConfig = BubblegumPdas.treeConfig(merkleTree),
  merkleTree = merkleTree,
  leafOwner = owner,
  leafDelegate = delegate,
  newLeafOwner = newOwner,
  args = BubblegumArgs.TransferArgs(proofArgs),
  proofAccounts = proofAccounts
)
```

## Mobile & React Native

Artemis offers a complete, original replacement for the standard mobile SDKs.

### Seed Vault (Kotlin)

A cleaner, Coroutine-first API for the Seed Vault.

```kotlin
val manager = SeedVaultManager(context)

// Create a seed (returns Intent for result contract)
val intent = manager.buildCreateSeedIntent(purpose = "sign_transaction")
startActivityForResult(intent, REQUEST_CODE)

// Sign Transaction (background, no callbacks)
val signatures = manager.signTransactions(authToken, txBytes)
```

Also includes drop-in compatibility for existing libraries using `com.solanamobile` namespaces, powered by Artemis internals:
```kotlin
// Works exactly like the official SDK but uses Artemis engine
wallet.authorizeSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
```

### React Native Usage

```javascript
import Artemis from 'artemis-solana-sdk';

// Mobile Wallet Adapter (SIWS)
const result = await Artemis.connectWithSignIn({
  domain: 'your.dapp',
  statement: 'Sign in to Artemis'
});
console.log(result.address, result.signature);

// Seed Vault (Android Wallet Apps)
const authToken = await Artemis.seedVaultAuthorize("sign_transaction");
const accounts = await Artemis.seedVaultGetAccounts(authToken);
```

## Modules

### Core
- **artemis-core**: Core utilities and shared primitives
- **artemis-core**: Pubkeys, base58, hashing, address derivation
- **artemis-rpc**: RpcApi (Suspend functions) and JsonRpcClient

### Transaction Building
- **artemis-tx**: Instructions, transaction building, v0 and ALT support
- **artemis-vtx**: Versioned transaction builder with ALT resolution
- **artemis-tx-presets**: Pre-built transaction patterns
- **artemis-programs**: System, Token, and Token2022 program instruction builders
- **artemis-compute**: Compute budget presets and utilities
- **artemis-presets**: High-level SDK presets and configuration

### Tokens
- **artemis-token2022**: Token-2022 builders and TLV decoding

### NFT & Metaplex
- **artemis-metaplex**: Token Metadata Program utilities
- **artemis-mplcore**: MPL Core v2 create flows, plugins, marketplace utilities
- **artemis-cnft**: Bubblegum cNFT builders, DAS helpers, marketplace toolkit
- **artemis-nft-compat**: Cross-standard NFT compatibility helpers
- **artemis-candy-machine**: Candy Machine v3 and Candy Guard instruction builders
- **artemis-candy-machine-presets**: Pre-built Candy Machine configurations

### Mobile & Wallet
- **artemis-seed-vault**: 100% Kotlin Solana Seed Vault SDK (with `com.solanamobile.seedvault` compatibility)
- **artemis-wallet-mwa-android**: Native Mobile Wallet Adapter (MWA 2.0) for Android with SIWS support
- **artemis-wallet**: Abstract wallet interface and signing utilities
- **artemis-solana-pay**: Solana Pay URL parsing and transaction request handling
- **artemis-react-native**: React Native bridge for Seed Vault and MWA

### Real-time
- **artemis-ws**: Solana WebSocket subscriptions with reconnect, resubscribe, dedupe, and Flow events

### Utilities
- **artemis-discriminators**: Versioned discriminator registry for Anchor programs
- **artemis-errors**: Standardized error types and handling
- **artemis-logging**: SDK logging utilities
- **artemis-privacy**: Privacy-preserving utilities
- **artemis-preview**: SDK preview and experimental features

### Gaming & DePIN
- **artemis-gaming**: Merkle verification, compute presets, session keys, ArcanaFlow batching
- **artemis-depin**: Location proof and device identity generation
- **artemis-replay**: Session recording and playback for debugging and telemetry

## Gaming features (2025)

Artemis includes a gaming oriented module focused on performance and player experience:

- **PriorityFeeOracle**: adaptive compute unit price suggestions based on recent confirmation feedback
- **AltSessionCache**: collect session addresses to prebuild and reuse ALTs across matches
- **ArcanaFlow**: deterministic frame batching lane for mobile game actions
- **Replay**: record and replay frame metadata for debugging and telemetry

### Priority fee advisor

```kotlin
val oracle = PriorityFeeOracle(scope)
val microLamports = oracle.suggest(programId = gameProgramId, tier = ComputeBudgetPresets.Tier.COMPETITIVE)
val feeIxs = listOf(
  ComputeBudgetPresets.setComputeUnitLimit(ComputeBudgetPresets.Tier.COMPETITIVE.units),
  ComputeBudgetPresets.setComputeUnitPrice(microLamports)
)
```

## License

Artemis is licensed under Apache-2.0. See LICENSE and NOTICE.

Optional modules:
- :artemis-nft-compat (Metaplex-compatible NFT helpers)
