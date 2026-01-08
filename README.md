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
    implementation("xyz.selenus:artemis-core:1.0.8")
    implementation("xyz.selenus:artemis-rpc:1.0.8")

    // Features
    implementation("xyz.selenus:artemis-tx:1.0.8")
    implementation("xyz.selenus:artemis-token2022:1.0.8")
    implementation("xyz.selenus:artemis-cnft:1.0.8")

    // Mobile Features (Refactored in 1.0.6!)
    implementation("xyz.selenus:artemis-seed-vault:1.0.8") // Pure Kotlin Seed Vault
    implementation("xyz.selenus:artemis-wallet-mwa-android:1.0.8") // Native MWA 2.0
    implementation("xyz.selenus:artemis-solana-pay:1.0.8")

    // React Native
    // npm install artemis-solana-sdk
    
    // Niche Features
    implementation("xyz.selenus:artemis-depin:1.0.8")
    implementation("xyz.selenus:artemis-gaming:1.0.8")
    implementation("xyz.selenus:artemis-mplcore:1.0.8")
    implementation("xyz.selenus:artemis-candy-machine:1.0.8")
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

- **artemis-runtime**: Pubkeys, base58, hashing, address derivation
- **artemis-rpc**: RpcApi (Suspend functions) and JsonRpcClient
- **artemis-tx**: instructions, transaction building, v0 and ALT support
- **artemis-token2022**: Token-2022 builders and TLV decoding
- **artemis-cnft**: Bubblegum cNFT builders, DAS helpers, marketplace toolkit
- **artemis-mplcore**: MPL Core create flows, plugins, marketplace utilities
- **artemis-candy-machine**: Candy Machine v3 and Candy Guard instruction builders
- **artemis-discriminators**: versioned discriminator registry for Anchor programs
- **artemis-ws**: Solana WebSocket subscriptions with reconnect, resubscribe, dedupe, and Flow events
- **artemis-seed-vault**: 100% Kotlin implementation of the Solana Seed Vault SDK (with `com.solanamobile.seedvault` compatibility). Contains `SeedVaultManager` and secure Intent resolution logic.
- **artemis-wallet-mwa-android**: Native implementation of the Mobile Wallet Adapter (MWA 2.0) protocol for Android. Supports `SignInWithSolana` (SIWS) and `signAndSend` with fallback.
- **artemis-react-native**: High-performance React Native bridge exposing full Seed Vault and MWA capabilities.
- **artemis-depin**: Location proof and device identity generation utilities.
- **artemis-gaming**: Merkle tree verification tools for on-chain gaming distributions, compute presets, session keys, ArcanaFlow batching lane.

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
