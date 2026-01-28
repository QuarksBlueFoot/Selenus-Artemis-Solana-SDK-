# Selenus Artemis Kotlin Solana SDK

> **Modular. Drop-in. Mobile-first. No paid assumptions.**

**Maintained by [Bluefoot Labs](https://bluefootlabs.com) and [Selenus](https://selenus.xyz).**

[![Maven Central](https://img.shields.io/maven-central/v/xyz.selenus/artemis-core?style=flat-square)](https://central.sonatype.com/search?q=xyz.selenus)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)

Artemis is the modular Kotlin Solana SDK built for 2026 Android architecture. Modern coroutines, Jetpack Compose ready, and mobile-first design patterns.

## Why Artemis?

**Artemis provides complete API parity with `solana-kmp` and Solana Mobile SDK**, plus exclusive 2026 innovations:

| Feature | solana-kmp | Solana Mobile | Sol4k | **Artemis** |
|---------|------------|---------------|-------|-------------|
| Active Maintenance | ✅ | ✅ | ✅ | ✅ **v1.5.0** |
| MWA 2.0 (P-256 ECDH) | ❌ | ✅ | ❌ | ✅ **Parity** |
| Seed Vault Integration | ❌ | ✅ | ❌ | ✅ **Parity** |
| RPC Client | ✅ | ❌ | ✅ | ✅ **Parity + Retry** |
| Token-2022 | ⚠️ | ❌ | ❌ | ✅ **Full** |
| Versioned Transactions | ✅ | ❌ | ✅ | ✅ |
| WebSocket Subscriptions | ❌ | ❌ | ❌ | ✅ **Exclusive** |
| React Native | ⚠️ | ❌ | ❌ | ✅ |
| Coroutine-first | ⚠️ | ❌ | ⚠️ | ✅ **Native** |
| Privacy (Confidential TX) | ❌ | ❌ | ❌ | ✅ **2026** |
| Gaming (VRF, State Proofs) | ❌ | ❌ | ❌ | ✅ **2026** |
| Gaming/DePIN Utilities | ❌ | ❌ | ❌ | ✅ **Exclusive** |

📖 **[Solana Mobile Migration Guide](docs/MIGRATION_FROM_SOLANA_MOBILE.md)** | 📊 **[Full SDK Parity Analysis](docs/SDK_PARITY_ANALYSIS.md)**

## 🚀 Solana Mobile Developers: Drop-In Replacement

**Artemis replaces the entire Solana Mobile Kotlin stack with fewer dependencies:**

```kotlin
// BEFORE: Current Solana Mobile approach (3+ dependencies)
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.7")
implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
implementation("foundation.metaplex:solana-kmp:0.3.0")  // For SolanaPublicKey, SolanaRpcClient

// AFTER: Artemis unified SDK (2 dependencies, more features)
implementation("xyz.selenus:artemis-core:1.5.0")
implementation("xyz.selenus:artemis-wallet-mwa-android:1.5.0")  // Includes MWA + Seed Vault
```

**Import mapping (update your imports, keep your code):**

| Current Import | Artemis Import |
|----------------|----------------|
| `com.solana.publickey.SolanaPublicKey` | `com.selenus.artemis.core.Pubkey` |
| `com.solana.rpc.SolanaRpcClient` | `com.selenus.artemis.rpc.RpcClient` |
| `com.solana.networking.KtorNetworkDriver` | Not needed (built-in) |
| `com.solana.mobilewalletadapter.clientlib.*` | `com.selenus.artemis.wallet.mwa.*` |
| `com.solanamobile.seedvault.*` | `com.selenus.artemis.seedvault.*` |

**What you gain:**
- ✅ WebSocket subscriptions (solana-kmp doesn't have this!)
- ✅ Token-2022 extensions
- ✅ Gaming & DePIN utilities
- ✅ Compute budget management
- ✅ Simpler APIs with Flows

## Features

It hits all the modern Solana patterns out of the box:

- v0 transactions and ALT workflows (because legacy txs are painful)
- Token-2022 with TLV decoding
- Compressed NFTs (Bubblegum-compatible utilities)
- MPL Core (v2 lane) create flows and marketplace utilities
- **Native Android Seed Vault & MWA 2.0 implementation** (No wrappers)
- WebSocket subscriptions via `artemis-ws`
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
    implementation("xyz.selenus:artemis-core:1.5.0")
    implementation("xyz.selenus:artemis-rpc:1.5.0")

    // Transaction Building
    implementation("xyz.selenus:artemis-tx:1.5.0")
    implementation("xyz.selenus:artemis-vtx:1.5.0") // Versioned transactions & ALTs
    implementation("xyz.selenus:artemis-tx-presets:1.5.0")
    implementation("xyz.selenus:artemis-programs:1.5.0") // System, Token, Token2022 program builders
    implementation("xyz.selenus:artemis-compute:1.5.0") // Compute budget utilities
    implementation("xyz.selenus:artemis-presets:1.5.0")

    // Tokens
    implementation("xyz.selenus:artemis-token2022:1.5.0") // Full Token-2022 extensions

    // NFT & Metaplex
    implementation("xyz.selenus:artemis-metaplex:1.5.0") // Token Metadata Program + Batch minting
    implementation("xyz.selenus:artemis-mplcore:1.5.0")  // MPL Core v2
    implementation("xyz.selenus:artemis-cnft:1.5.0") // Compressed NFTs (Bubblegum)
    implementation("xyz.selenus:artemis-nft-compat:1.5.0") // Cross-standard NFT helpers
    implementation("xyz.selenus:artemis-candy-machine:1.5.0")
    implementation("xyz.selenus:artemis-candy-machine-presets:1.5.0")

    // Mobile Features
    implementation("xyz.selenus:artemis-seed-vault:1.5.0") // Pure Kotlin Seed Vault
    implementation("xyz.selenus:artemis-wallet-mwa-android:1.5.0") // Native MWA 2.0
    implementation("xyz.selenus:artemis-wallet:1.5.0") // Wallet abstractions + SendTransactionOptions
    implementation("xyz.selenus:artemis-solana-pay:1.5.0")

    // React Native: npm install artemis-solana-sdk@1.5.0

    // Real-time & WebSocket
    implementation("xyz.selenus:artemis-ws:1.5.0") // WebSocket subscriptions

    // Utilities
    implementation("xyz.selenus:artemis-discriminators:1.5.0")
    implementation("xyz.selenus:artemis-errors:1.5.0")
    implementation("xyz.selenus:artemis-logging:1.5.0")
    implementation("xyz.selenus:artemis-privacy:1.5.0") // ⭐ NEW: Confidential transfers, Ring signatures

    // Gaming & DePIN
    implementation("xyz.selenus:artemis-gaming:1.5.0") // ⭐ ENHANCED: VRF, State proofs, Reward distribution
    implementation("xyz.selenus:artemis-depin:1.5.0")
    implementation("xyz.selenus:artemis-replay:1.5.0") // Session recording & playback
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
- **artemis-token2022**: ⭐ **ENHANCED v1.5.0** Token-2022 builders and TLV decoding
  - **All 8 Extensions**: Interest-bearing, Non-transferable (soulbound), Permanent delegate, Transfer hooks, Metadata pointer, Confidential transfers, CPI guard, Default account state
  - **Comprehensive API**: Initialize, update, and manage all extension types
  - Mobile-optimized serialization and instruction building

### NFT & Metaplex
- **artemis-metaplex**: ⭐ **ENHANCED v1.5.0** Token Metadata Program utilities
  - **Batch NFT Operations**: Mint up to 4 NFTs per transaction
  - **Dynamic Metadata**: Time-based metadata updates with state hashing
  - **Collection Management**: Advanced collection creation and verification
  - Standard metadata operations (create, update, verify)
- **artemis-mplcore**: MPL Core v2 create flows, plugins, marketplace utilities
- **artemis-cnft**: Bubblegum cNFT builders, DAS helpers, marketplace toolkit
- **artemis-nft-compat**: Cross-standard NFT compatibility helpers
- **artemis-candy-machine**: Candy Machine v3 and Candy Guard instruction builders
- **artemis-candy-machine-presets**: Pre-built Candy Machine configurations

### Mobile & Wallet
- **artemis-seed-vault**: 100% Kotlin Solana Seed Vault SDK (with `com.solanamobile.seedvault` compatibility)
- **artemis-wallet-mwa-android**: ⭐ **ENHANCED v1.5.0** Native Mobile Wallet Adapter (MWA 2.0) for Android with SIWS support
  - Full `SendTransactionOptions` support (commitment levels, preflight, retries)
  - Batch transaction support with ordered execution
  - P-256 ECDH encryption
- **artemis-wallet**: Abstract wallet interface and signing utilities with `SendTransactionOptions` API
- **artemis-solana-pay**: Solana Pay URL parsing and transaction request handling
- **artemis-react-native**: React Native bridge for Seed Vault and MWA

### Real-time
- **artemis-ws**: Solana WebSocket subscriptions with reconnect, resubscribe, dedupe, and Flow events

### Utilities
- **artemis-discriminators**: Versioned discriminator registry for Anchor programs
- **artemis-errors**: Standardized error types and handling
- **artemis-logging**: SDK logging utilities
- **artemis-privacy**: ⭐ **NEW v1.5.0** Privacy-preserving cryptography
  - **Confidential Transfers**: Pedersen commitments, range proofs, encrypted amounts
  - **Ring Signatures**: SAG signatures for anonymous group signing
  - **Mixing Pools**: CoinJoin-style transaction mixing
- **artemis-preview**: SDK preview and experimental features

### Gaming & DePIN
- **artemis-gaming**: ⭐ **ENHANCED v1.5.0** Production-ready gaming utilities
  - **Verifiable Randomness**: VRF and commit-reveal for provably fair gaming
  - **Game State Proofs**: Merkle state trees, fraud proofs, state channels
  - **Reward Distribution**: 4 payout strategies (Winner Takes All, Linear, Exponential, Poker-style) with Merkle claims
  - **ArcanaFlow**: Deterministic frame batching for mobile games
  - **Priority Fee Oracle**: Adaptive compute unit pricing
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

## API Reference

### Correct Import Paths

Below are the correct import paths for commonly used Artemis classes:

#### Core & RPC
```kotlin
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.runtime.Keypair
import com.selenus.artemis.runtime.Base58
import com.selenus.artemis.runtime.Pda
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.AccountInfo       // Typed account info wrapper
import com.selenus.artemis.rpc.TokenAccountInfo  // Typed token account wrapper
import com.selenus.artemis.rpc.MintInfo          // Typed mint info wrapper
```

#### Token-2022
```kotlin
import com.selenus.artemis.token2022.Token2022Tlv        // TLV decoding utilities
import com.selenus.artemis.token2022.Token2022Instructions
import com.selenus.artemis.token2022.Token2022Extensions
```

#### Compute Budget
```kotlin
import com.selenus.artemis.compute.ComputeBudgetPresets  // Standard/Enhanced/Priority tiers
import com.selenus.artemis.compute.ComputeBudgetProgram  // Low-level instruction builders
import com.selenus.artemis.compute.ComputeOptimizer
import com.selenus.artemis.compute.PriorityFees
```

#### Gaming (Verifiable Randomness, State Proofs, Rewards)
```kotlin
// NOTE: Class is VerifiableRandomness, not VrfUtils
import com.selenus.artemis.gaming.VerifiableRandomness  // VRF and commit-reveal
import com.selenus.artemis.gaming.GameStateProofs       // Merkle proofs, fraud proofs
import com.selenus.artemis.gaming.RewardDistribution    // Payout strategies
import com.selenus.artemis.gaming.PriorityFeeOracle     // Adaptive fee suggestions
import com.selenus.artemis.gaming.ComputeBudgetPresets as GamingPresets  // Gaming-specific presets
import com.selenus.artemis.gaming.ArcanaFlow            // Frame batching
import com.selenus.artemis.gaming.AltSessionCache       // ALT caching
```

#### Privacy
```kotlin
import com.selenus.artemis.privacy.ConfidentialTransfer  // Encrypted amounts, range proofs
import com.selenus.artemis.privacy.RingSignature         // Anonymous group signing
import com.selenus.artemis.privacy.MixingPool            // CoinJoin-style mixing
import com.selenus.artemis.privacy.ZeroKnowledgeProofs   // ZK proof utilities
```

#### DePIN (Device Identity & Location)
```kotlin
import com.selenus.artemis.depin.DeviceIdentity      // Device key derivation
import com.selenus.artemis.depin.DeviceAttestation   // Attestation proofs & challenges
import com.selenus.artemis.depin.LocationProof       // Signed location proofs (in DeviceIdentity.kt)
import com.selenus.artemis.depin.TelemetryBatcher    // Telemetry batching
```

#### WebSocket
```kotlin
import com.selenus.artemis.ws.SolanaWebSocket
import com.selenus.artemis.ws.SubscriptionManager
```

### Typed RPC Responses

Artemis v1.5.0 provides typed account info responses:

```kotlin
val api = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))

// Get typed account info with .owner, .lamports, .data properties
val accountInfo = api.getAccountInfoParsed("...pubkey...")
println("Owner: ${accountInfo?.owner}")
println("Lamports: ${accountInfo?.lamports}")
println("Data size: ${accountInfo?.data?.size} bytes")

// Get typed token account info
val tokenInfo = api.getTokenAccountInfoParsed("...token-account...")
println("Mint: ${tokenInfo?.mint}")
println("Owner: ${tokenInfo?.owner}")
println("Amount: ${tokenInfo?.amount}")

// Get typed mint info
val mintInfo = api.getMintInfoParsed("...mint...")
println("Decimals: ${mintInfo?.decimals}")
println("Supply: ${mintInfo?.supply}")
```

### Token-2022 TLV Decoding

```kotlin
// Decode TLV extensions from raw account data
val accountData = api.getAccountInfoBase64("...token2022-mint...")
val entries = Token2022Tlv.decode(accountData!!)

// Check for specific extensions
if (Token2022Tlv.hasExtension(entries, Token2022Tlv.ExtensionType.TRANSFER_FEE_CONFIG)) {
    val feeEntries = Token2022Tlv.findByType(entries, Token2022Tlv.ExtensionType.TRANSFER_FEE_CONFIG)
    // Process transfer fee config...
}

// List all extensions
entries.forEach { entry ->
    println("Extension: ${Token2022Tlv.getTypeName(entry.type.toInt())}")
}
```

### Compute Budget Presets

```kotlin
// Use preset tiers
val instructions = ComputeBudgetPresets.preset(ComputeBudgetPresets.Tier.PRIORITY)

// Custom compute settings
val customIxs = listOf(
    ComputeBudgetPresets.setComputeUnitLimit(500_000),
    ComputeBudgetPresets.setComputeUnitPrice(2_000)
)

// Transaction-type based estimation
val estimatedUnits = ComputeBudgetPresets.estimateForTransaction(
    ComputeBudgetPresets.TransactionType.TOKEN_TRANSFER
)
```

### Gaming: Verifiable Randomness

```kotlin
// Commit-reveal pattern for provably fair randomness
val commitment = VerifiableRandomness.commit(secretSeed = mySecret.toByteArray())
// ... submit commitment on-chain ...

// Later, reveal and verify
val randomValue = VerifiableRandomness.reveal(secretSeed = mySecret.toByteArray())
val isValid = VerifiableRandomness.verifyReveal(commitment, mySecret.toByteArray())
```

### Privacy: Confidential Transfers

```kotlin
// Create encrypted transfer amount
val encrypted = ConfidentialTransfer.encryptAmount(
    amount = 1_000_000L,
    recipientPubkey = recipientKey
)

// Generate range proof (amount is within valid range)
val rangeProof = ConfidentialTransfer.generateRangeProof(
    amount = 1_000_000L,
    blindingFactor = encrypted.blindingFactor
)
```

### DePIN: Device Attestation

```kotlin
// Create device identity
val identity = DeviceIdentity.fromDeviceId("unique-device-id", networkId = "myNetwork")

// Create and respond to attestation challenge
val challenge = DeviceAttestation.createChallenge(networkId = "myNetwork")
val proof = DeviceAttestation.respondToChallenge(identity, challenge)

// Verify attestation
val isValid = DeviceAttestation.verifyAttestation(proof, expectedDeviceId = "unique-device-id")
```

## License

Artemis is licensed under Apache-2.0. See LICENSE and NOTICE.

Optional modules:
- :artemis-nft-compat (Metaplex-compatible NFT helpers)
