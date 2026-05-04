# Adoption Bundles

Artemis is modular. You include only what you need. These bundles show which modules to add for common use cases.

## Foundation Bundle

Everything a basic Solana app needs. No mobile wallet, no NFTs, no DeFi.

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-core:2.3.1")
    implementation("xyz.selenus:artemis-rpc:2.3.1")
    implementation("xyz.selenus:artemis-ws:2.3.1")
    implementation("xyz.selenus:artemis-tx:2.3.1")
    implementation("xyz.selenus:artemis-vtx:2.3.1")
    implementation("xyz.selenus:artemis-programs:2.3.1")
    implementation("xyz.selenus:artemis-errors:2.3.1")
    implementation("xyz.selenus:artemis-logging:2.3.1")
    implementation("xyz.selenus:artemis-compute:2.3.1")
}
```

Covers: key types, Ed25519, Base58, RPC client, WebSocket subscriptions, legacy and versioned transactions, system/token/ATA/memo programs, compute budget, error decoding, structured logging.

Consolidates: solana-kmp core, Sol4k core, solanaKT primitives.

## Solana Mobile Client Bundle

Foundation plus wallet integration. This is the Artemis client layer for apps that use Solana Mobile primitives; MWA and Seed Vault remain the protocol and custody boundaries.

```kotlin
dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.3.1")
    implementation("xyz.selenus:artemis-rpc:2.3.1")
    implementation("xyz.selenus:artemis-ws:2.3.1")
    implementation("xyz.selenus:artemis-tx:2.3.1")
    implementation("xyz.selenus:artemis-vtx:2.3.1")
    implementation("xyz.selenus:artemis-programs:2.3.1")
    implementation("xyz.selenus:artemis-errors:2.3.1")
    implementation("xyz.selenus:artemis-compute:2.3.1")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.3.1")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.1")
    implementation("xyz.selenus:artemis-seed-vault:2.3.1")
}
```

Covers: everything in Foundation, plus WalletSession abstraction (local/adapter/raw signing), MWA 2.0 client integration, Seed Vault integration, Android lifecycle handling.

Consolidates client SDK functionality from solana-kmp, mobile-wallet-adapter-clientlib-ktx, and seedvault-wallet-sdk without replacing MWA or Seed Vault.

## NFT Stack Bundle

Solana Mobile Client Bundle plus token and NFT modules.

```kotlin
dependencies {
    // Solana Mobile Client Bundle (above)
    // ...

    // NFT / Tokens
    implementation("xyz.selenus:artemis-token2022:2.3.1")
    implementation("xyz.selenus:artemis-metaplex:2.3.1")
    implementation("xyz.selenus:artemis-mplcore:2.3.1")
    implementation("xyz.selenus:artemis-cnft:2.3.1")
    implementation("xyz.selenus:artemis-candy-machine:2.3.1")
}
```

Covers: Token-2022 full extension support, Metaplex Token Metadata, MPL Core assets, compressed NFTs (Bubblegum), Candy Machine v3 minting with guard validation.

Consolidates supported Metaplex KMM use cases into Artemis ecosystem modules. Unsupported Metaplex Android surfaces remain marked `Partial` in [PARITY_MATRIX.md](PARITY_MATRIX.md).

## DeFi Stack Bundle

Solana Mobile Client Bundle plus DeFi and payment modules.

```kotlin
dependencies {
    // Solana Mobile Client Bundle (above)
    // ...

    // DeFi / Ecosystem
    implementation("xyz.selenus:artemis-jupiter:2.3.1")
    implementation("xyz.selenus:artemis-actions:2.3.1")
    implementation("xyz.selenus:artemis-solana-pay:2.3.1")
    implementation("xyz.selenus:artemis-anchor:2.3.1")
    implementation("xyz.selenus:artemis-simulation:2.3.1")
}
```

Covers: Jupiter swap routing, Solana Actions/Blinks, Solana Pay URI building and transaction requests, type-safe Anchor program clients from IDL, transaction simulation.

## Choosing Your Bundle

| Building... | Start with |
|---|---|
| Backend/server Solana app | Foundation |
| Android wallet or dApp | Solana Mobile Client Bundle |
| NFT marketplace or minting app | NFT Stack |
| DeFi app or payment integration | DeFi Stack |
| All of the above | Pick modules from each stack |

You can always add individual modules later. Every ecosystem and advanced module works independently on top of Foundation.
