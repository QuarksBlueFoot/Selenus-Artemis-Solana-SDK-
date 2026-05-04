# Adopting Artemis with Solana Mobile

Artemis is a unified, mobile-first Solana client SDK layer for apps that build on Solana Mobile. It consolidates transaction construction, RPC, realtime subscriptions, wallet session handling, tokens, NFTs, DAS, Jupiter, Anchor, and other client-side logic into one Kotlin-first implementation.

Artemis does **not** replace the Solana Mobile Stack platform. It uses the Solana Mobile primitives that matter:

- Mobile Wallet Adapter (MWA) remains the wallet-to-app protocol.
- Seed Vault remains the hardware-backed custody and signing boundary on supported devices.
- dApp Store and device platform services remain outside Artemis.

The clean positioning is:

> Solana Mobile Stack provides the secure platform and protocols. Artemis provides the client SDK layer developers build with.

## Layer model

```text
+------------------------------------------------+
| Solana Mobile Stack                            |
| Seed Vault + Mobile Wallet Adapter + platform  |
+-----------------------+------------------------+
                        |
+-----------------------v------------------------+
| Artemis                                        |
| Unified mobile client SDK layer                |
| - RPC and websocket subscriptions              |
| - Transaction construction and simulation       |
| - Wallet session abstraction over MWA           |
| - Token, NFT, DAS, DeFi, Anchor, Actions        |
+-----------------------+------------------------+
                        |
+-----------------------v------------------------+
| Mobile apps                                    |
| Android / React Native / future native targets  |
+------------------------------------------------+
```

## Safe replacement scope

Artemis replaces fragmented client-side SDK layers, not the platform primitives.

| Artemis can replace | Artemis does not replace |
| --- | --- |
| `web3.js` usage in mobile client code | Solana Mobile Stack as a platform |
| `solana-kmp`, Sol4k, and similar Kotlin client SDKs | Mobile Wallet Adapter as a protocol |
| custom transaction builders | Seed Vault as a secure custody service |
| duplicated RPC, websocket, retry, and blockhash glue | wallet approval UX or wallet security policy |
| inconsistent mobile wallet wrappers | dApp Store or device services |

Use language like "client SDK layer", "source-compatible shim", and "MWA-compatible wallet abstraction". Avoid language that implies Artemis is a new wallet protocol, a replacement for Seed Vault, or a replacement for the Solana Mobile platform.

## Adoption tracks

### 1. Artemis-native client layer

Use this for new apps or apps that can update call sites once.

- `ArtemisMobile.create(...)` wires RPC, MWA wallet adapter, `WalletSessionManager`, `TxEngine`, realtime, optional DAS, and marketplace helpers.
- `WalletSessionManager.withWallet { ... }` keeps wallet lifecycle logic centralized and retries only for session-expiry sentinels.
- `TxEngine` owns blockhash management, simulation, priority fees, retry policy, signing, sending, and confirmation.

### 2. Source-compatible client-library migration

Use this for apps that need existing imports to keep compiling while moving implementation to Artemis.

- `interop/artemis-*-compat` modules publish upstream package and class names.
- The shims target client libraries such as MWA clientlib, MWA walletlib, Seed Vault static helpers, sol4k, solana-kmp, rpc-core, and web3-solana.
- These shims preserve the upstream API shape but route behavior through Artemis engines where implemented.
- Exact upstream pins, tests, and remaining partials are listed in [SOLANA_MOBILE_CLIENT_COMPATIBILITY_AUDIT.md](SOLANA_MOBILE_CLIENT_COMPATIBILITY_AUDIT.md).

This track is still a client-library migration. It does not replace MWA, Seed Vault, or Solana Mobile OS services.

## Dependency mapping

| Existing client dependency | Artemis target | Notes |
| --- | --- | --- |
| `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` | `xyz.selenus:artemis-wallet-mwa-android` | Native MWA dApp client plus managed wallet session layer |
| `com.solanamobile:seedvault-wallet-sdk` | `xyz.selenus:artemis-seed-vault` | Integrates with the Seed Vault provider; keys stay in the device custody boundary |
| `solana-kmp` or `rpc-core` for RPC | `xyz.selenus:artemis-rpc` | Typed JSON-RPC, endpoint pool, batch DSL, blockhash cache |
| `solana-kmp` `Transaction` / `PublicKey` | `xyz.selenus:artemis-core`, `xyz.selenus:artemis-tx` | Core types and legacy transaction model |
| `solana-kmp` `VersionedTransaction` | `xyz.selenus:artemis-vtx` | v0 versioned transactions with address lookup tables |
| Sol4k primitives | `xyz.selenus:artemis-core` | Pubkey, keypair, Base58, Ed25519, PDA |
| Custom websocket glue | `xyz.selenus:artemis-ws` | Realtime engine with reconnect and typed connection state |
| Custom NFT / DAS glue | `xyz.selenus:artemis-cnft` | DAS interface, Helius primary, RPC fallback, marketplace helpers |

The current published Artemis version is `2.3.1`. The source of truth is the `version` field in [../gradle.properties](../gradle.properties).

## Gradle migration

### Before

```kotlin
dependencies {
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")
    implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
    implementation("foundation.metaplex:solana-kmp:0.3.0")
    // or
    implementation("org.sol4k:sol4k:0.7.0")
}
```

### After: Artemis-native

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-core:2.3.1")
    implementation("xyz.selenus:artemis-rpc:2.3.1")
    implementation("xyz.selenus:artemis-ws:2.3.1")
    implementation("xyz.selenus:artemis-vtx:2.3.1")
    implementation("xyz.selenus:artemis-programs:2.3.1")

    implementation("xyz.selenus:artemis-wallet:2.3.1")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.1")
    implementation("xyz.selenus:artemis-seed-vault:2.3.1") // only when Seed Vault is needed

    implementation("xyz.selenus:artemis-cnft:2.3.1") // optional NFT / DAS helpers
}
```

### After: source-compatible migration

Use the matching `interop/artemis-*-compat` artifacts when import stability matters. Keep the compatibility scope narrow and verify the exact upstream version pin in the module's `build.gradle.kts` before claiming no code changes.

## Wallet lifecycle guidance

Artemis exposes two levels so teams can choose the amount of abstraction they want.

| Level | Use when | Behavior |
| --- | --- | --- |
| Protocol-faithful adapter path | You need direct MWA control or are validating wallet behavior | `MwaWalletAdapter` and the compat client APIs keep session state visible and return typed wallet/user errors. |
| Managed session path | You want app-level ergonomics | `WalletSessionManager` centralizes connect, auth-token reuse, session-expiry recovery, and lifecycle callbacks. |

Required rules for MWA-safe apps:

- Treat every wallet action as user-approved; do not hide approval boundaries.
- Retry by opening a new compliant wallet session after session expiration.
- Normalize wallet rejection, wallet unavailable, session expired, and transport closed into distinct app-visible errors.
- Use an absolute HTTPS icon URI, for example `https://myapp.example.com/favicon.ico`.
- Keep deep link and intent handling separate from `setContent { ... }`; do not rebuild the Compose tree from `onNewIntent()` during an active wallet session.

## Evidence required for stronger adoption claims

Do not claim broad Solana Mobile adoption readiness until the evidence below is present for the exact release being pitched.

| Evidence | Required artifact |
| --- | --- |
| Real wallet coverage | [WALLET_COMPATIBILITY_TESTING.md](WALLET_COMPATIBILITY_TESTING.md) with device, wallet, and edge-case results |
| Transaction byte correctness | [TRANSACTION_CORRECTNESS.md](TRANSACTION_CORRECTNESS.md) with byte-level comparisons against a trusted reference encoder |
| Performance claims | [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md) with reproducible device and benchmark setup |
| API migration scope | [PARITY_MATRIX.md](PARITY_MATRIX.md) and [migration-solana-mobile.md](migration-solana-mobile.md) |
| Architecture boundaries | [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) and [adr/0001-engine-architecture.md](adr/0001-engine-architecture.md) |

## Migration checklist

1. Pick Artemis-native or source-compatible migration before changing code.
2. Replace dependencies with the Artemis modules for the chosen track.
3. Keep MWA and Seed Vault language explicit: Artemis uses them, it does not replace them.
4. Replace manual transaction build/sign/send/retry logic with `TxEngine` or `WalletSession` flows.
5. Add real wallet tests for Phantom, Backpack, Solflare, and the official MWA reference wallet before making public compatibility claims.
6. Add byte-level transaction serialization checks before claiming equivalence with another SDK.
7. Add performance measurements before claiming mobile-native performance improvements over a specific baseline.
