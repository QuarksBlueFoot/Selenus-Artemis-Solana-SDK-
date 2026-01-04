# Selenus Artemis Kotlin Solana SDK

> **Modular. Drop-in. Mobile-first. No paid assumptions.**

**Maintained by [Bluefoot Labs](https://bluefootlabs.com) and [Selenus](https://selenus.xyz).**

Artemis is the modular Kotlin Solana SDK I wish I had when I started building for Android. It's built for 2025, so you're not stuck patching legacy code.

It hits all the modern Solana patterns out of the box:

- v0 transactions and ALT workflows (because legacy txs are painful)
- Token-2022 with TLV decoding
- Compressed NFTs (Bubblegum-compatible utilities)
- MPL Core (v2 lane) create flows and marketplace utilities
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
    implementation("xyz.selenus:artemis-core:1.0.2")
    implementation("xyz.selenus:artemis-rpc:1.0.2")

    // Features
    implementation("xyz.selenus:artemis-tx:1.0.2")
    implementation("xyz.selenus:artemis-token2022:1.0.2")
    implementation("xyz.selenus:artemis-cnft:1.0.2")
    implementation("xyz.selenus:artemis-depin:1.0.2")
    implementation("xyz.selenus:artemis-gaming:1.0.2")
    implementation("xyz.selenus:artemis-solana-pay:1.0.2")
    implementation("xyz.selenus:artemis-seed-vault:1.0.2")
    
    // React Native
    // npm install artemis-solana-sdk
    implementation("xyz.selenus:artemis-mplcore:1.0.2")
    implementation("xyz.selenus:artemis-candy-machine:1.0.2")

    // Android Wallet Adapter
    implementation("xyz.selenus:artemis-wallet-mwa-android:1.0.2")
}
```

## Quick start

### 1) Basic RPC

```kotlin
val rpc = JsonRpcClient("https://your-rpc")
val latest = rpc.call(buildJsonObject {
  put("id","1"); put("jsonrpc","2.0"); put("method","getLatestBlockhash")
})
```

### 2) Token-2022 TLV decode

```kotlin
val parsed = Token2022Tlv.decodeMint(accountData)
val transferFee = parsed.extensions.transferFeeConfig
```

### 3) cNFT transfer using DAS proof

```kotlin
val das = DasClient(rpc)
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

### 4) MPL Core create flows

```kotlin
val createCollectionIx = MplCoreInstructions.createCollection(
  collection = collectionPubkey,
  payer = payer,
  authority = authority,
  args = MplCoreArgs.CreateCollectionArgs(
    name = "My Collection",
    uri = "https://example.com/collection.json",
    updateAuthority = authority
  )
)
```

### 5) Candy Machine mint (Candy Guard mint_v2)

This covers the common SOL payment mint flow. If your Candy Guard requires extra mint args
(for example, allow-list proofs), you can provide the additional accounts via `remainingAccounts`.

```kotlin
val ix = CandyGuardMintV2.build(
  args = CandyGuardMintV2.Args(group = null),
  accounts = CandyGuardMintV2.Accounts(
    candyGuard = candyGuard,
    candyMachine = candyMachine,
    payer = payer,
    minter = minter,
    nftMint = newNftMint,
    nftMetadata = nftMetadata,
    nftMasterEdition = nftMasterEdition,
    collectionDelegateRecord = collectionDelegateRecord,
    collectionMint = collectionMint,
    collectionMetadata = collectionMetadata,
    collectionMasterEdition = collectionMasterEdition,
    collectionUpdateAuthority = collectionUpdateAuthority,
  )
)
```

## Discriminator registry

Some Anchor programs ship with different method names across builds.
Use the registry to keep those differences out of your app logic.

```kotlin
val registry = DiscriminatorRegistry.builder()
  .put(programId, "mainnet", "mintToCollection", "mint_to_collection_v1")
  .put(programId, "mainnet", "transfer", "transfer")
  .build()

val disc = registry.discriminator(programId, "mainnet", "transfer")
```

## Modules

- artemis-runtime: Pubkeys, base58, hashing, address derivation
- artemis-rpc: JsonRpcClient
- artemis-tx: instructions, transaction building, v0 and ALT support
- artemis-token2022: Token-2022 builders and TLV decoding
- artemis-cnft: Bubblegum cNFT builders, DAS helpers, marketplace toolkit
- artemis-mplcore: MPL Core create flows, plugins, marketplace utilities
- artemis-candy-machine: Candy Machine v3 and Candy Guard instruction builders
- artemis-discriminators: versioned discriminator registry for Anchor programs

## Marketplace helpers

### cNFT batch delegate and transfer

```kotlin
val batch = BatchMarketplaceToolkit.fetchBatch(das, assetIds)
val ixs = BatchMarketplaceToolkit.buildDelegateMany(
  merkleTree = merkleTree,
  treeConfig = BubblegumPdas.treeConfig(merkleTree),
  leafOwner = owner,
  currentDelegate = null,
  newDelegate = marketplaceDelegate,
  batch = batch
)
```

### Core listing bundle

```kotlin
val ixs = CoreMarketplaceToolkit.buildListingBundle(
  asset = asset,
  ownerAuthority = owner,
  marketplaceDelegate = delegate
)
```

- artemis-ws: Solana WebSocket subscriptions with reconnect, resubscribe, dedupe, and Flow events

WebSocket notes: artemis-ws supports subscription bundling and optional HTTP fallback polling while disconnected.

- artemis-gaming: game focused utilities (compute presets, session keys, ArcanaFlow batching lane)


## Gaming features (2025)

Artemis includes a gaming oriented module focused on performance and player experience:

- Compute budget presets that match common game tiers
- Session keys for fast signing after a one time authorization
- ArcanaFlow, a batching lane that emits deterministic action frames

These are designed to work with v0 + ALT so games can keep transactions small and fast.

- artemis-replay: deterministic frame replay recorder and loader for games and interactive apps


### Gaming upgrades

- PriorityFeeOracle: adaptive compute unit price suggestions based on recent confirmation feedback
- AltSessionCache: collect session addresses to prebuild and reuse ALTs across matches
- ArcanaFlow: deterministic frame batching lane for mobile game actions
- Replay: record and replay frame metadata for debugging and telemetry

## License

Artemis is licensed under Apache-2.0. See LICENSE and NOTICE.


### Priority fee advisor

```kotlin
val oracle = PriorityFeeOracle(scope)
val microLamports = oracle.suggest(programId = gameProgramId, tier = ComputeBudgetPresets.Tier.COMPETITIVE)
val feeIxs = listOf(
  ComputeBudgetPresets.setComputeUnitLimit(ComputeBudgetPresets.Tier.COMPETITIVE.units),
  ComputeBudgetPresets.setComputeUnitPrice(microLamports)
)
```

### Replay recording

```kotlin
val rec = ReplayRecorder()
rec.recordFrame(frame.createdAtMs, frame.instructions, meta = mapOf("match" to matchId))

// after send
rec.attachSignature(index = 0, signature = sig, recentBlockhash = recentBlockhash)
rec.writeTo(File("replay.json"))
```


### ALT session planning for games

The gaming module includes an ALT session builder that produces deterministic address proposals
from ArcanaFlow frames. This helps you build lookup tables once and reuse them across a match.


### Gaming v22 upgrades

- AltSessionExecutor: build create and extend instruction bundles for Address Lookup Tables
- PriorityFeeOracle v22: rolling window samples, fast bump on failures, slow decay when healthy
- ArcanaFlowFrameComposer: create per-frame transaction plans (compute, fees, LUT hints)


### Gaming v23 upgrades

- AltTxScheduler: split ALT create and extend into deterministic multi-transaction batches
- ArcanaFlowV0Compiler: one-call compile for ArcanaFlow frame plans into signed v0 VersionedTransaction

ArcanaFlow guide: docs/arcanaflow.md

Game presets: docs/game-presets.md

Replay pagination: docs/replay-pagination.md

Changelog: [CHANGELOG.md](CHANGELOG.md)

packages: docs/packages.md

Optional modules:
- :artemis-nft-compat (Metaplex-compatible NFT helpers)

