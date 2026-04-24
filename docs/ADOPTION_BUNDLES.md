# Adoption Bundles

Artemis is modular. You include only what you need. These bundles show which modules to add for common use cases.

## Foundation Bundle

Everything a basic Solana app needs. No mobile wallet, no NFTs, no DeFi.

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-core:2.3.0")
    implementation("xyz.selenus:artemis-rpc:2.3.0")
    implementation("xyz.selenus:artemis-ws:2.3.0")
    implementation("xyz.selenus:artemis-tx:2.3.0")
    implementation("xyz.selenus:artemis-vtx:2.3.0")
    implementation("xyz.selenus:artemis-programs:2.3.0")
    implementation("xyz.selenus:artemis-errors:2.3.0")
    implementation("xyz.selenus:artemis-logging:2.3.0")
    implementation("xyz.selenus:artemis-compute:2.3.0")
}
```

Covers: key types, Ed25519, Base58, RPC client, WebSocket subscriptions, legacy and versioned transactions, system/token/ATA/memo programs, compute budget, error decoding, structured logging.

Replaces: solana-kmp core, Sol4k core, solanaKT primitives.

## Mobile Stack Bundle

Foundation plus wallet integration. This is the Solana Mobile Stack replacement set.

```kotlin
dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.3.0")
    implementation("xyz.selenus:artemis-rpc:2.3.0")
    implementation("xyz.selenus:artemis-ws:2.3.0")
    implementation("xyz.selenus:artemis-tx:2.3.0")
    implementation("xyz.selenus:artemis-vtx:2.3.0")
    implementation("xyz.selenus:artemis-programs:2.3.0")
    implementation("xyz.selenus:artemis-errors:2.3.0")
    implementation("xyz.selenus:artemis-compute:2.3.0")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.3.0")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.0")
    implementation("xyz.selenus:artemis-seed-vault:2.3.0")
}
```

Covers: everything in Foundation, plus WalletSession abstraction (local/adapter/raw signing), MWA 2.0 protocol, Seed Vault integration, Android lifecycle handling.

Replaces: solana-kmp + mobile-wallet-adapter-clientlib-ktx + seedvault-wallet-sdk.

## NFT Stack Bundle

Mobile Stack plus token and NFT modules.

```kotlin
dependencies {
    // Mobile Stack (above)
    // ...

    // NFT / Tokens
    implementation("xyz.selenus:artemis-token2022:2.3.0")
    implementation("xyz.selenus:artemis-metaplex:2.3.0")
    implementation("xyz.selenus:artemis-mplcore:2.3.0")
    implementation("xyz.selenus:artemis-cnft:2.3.0")
    implementation("xyz.selenus:artemis-candy-machine:2.3.0")
}
```

Covers: Token-2022 full extension support, Metaplex Token Metadata, MPL Core assets, compressed NFTs (Bubblegum), Candy Machine v3 minting with guard validation.

Replaces: Metaplex KMM modules for supported areas.

## DeFi Stack Bundle

Mobile Stack plus DeFi and payment modules.

```kotlin
dependencies {
    // Mobile Stack (above)
    // ...

    // DeFi / Ecosystem
    implementation("xyz.selenus:artemis-jupiter:2.3.0")
    implementation("xyz.selenus:artemis-actions:2.3.0")
    implementation("xyz.selenus:artemis-solana-pay:2.3.0")
    implementation("xyz.selenus:artemis-anchor:2.3.0")
    implementation("xyz.selenus:artemis-simulation:2.3.0")
}
```

Covers: Jupiter swap routing, Solana Actions/Blinks, Solana Pay URI building and transaction requests, type-safe Anchor program clients from IDL, transaction simulation.

## Choosing Your Bundle

| Building... | Start with |
|---|---|
| Backend/server Solana app | Foundation |
| Android wallet or dApp | Mobile Stack |
| NFT marketplace or minting app | NFT Stack |
| DeFi app or payment integration | DeFi Stack |
| All of the above | Pick modules from each stack |

You can always add individual modules later. Every ecosystem and advanced module works independently on top of Foundation.
