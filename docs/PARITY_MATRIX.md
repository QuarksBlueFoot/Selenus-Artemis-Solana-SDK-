# Parity matrix

Feature-by-feature status of Artemis against the Kotlin/Android Solana SDK ecosystem. A row is marked `Verified` only when the repo contains code plus a focused test, API snapshot, or build gate that would fail if the claim regressed.

## Legend

- **Verified**: implementation shipped and exercised by a test, API snapshot, or focused build gate.
- **Partial**: implementation exists, but at least one upstream edge case or device-backed flow still needs coverage.
- **Experimental**: public surface exists, but behavior may still change.
- **Planned**: documented roadmap, no shipping code yet.
- **N/A**: not applicable to that upstream project.

## Release labels

- `Artemis-native ready`: safe to adopt through Artemis APIs directly.
- `SMS-client-compat ready`: safe to adopt through source-compatible interop modules for app-side Solana Mobile dependencies. This does not mean Artemis replaces Mobile Wallet Adapter, Seed Vault, wallet approval UX, or Solana Mobile platform services.

## Dependency replacement matrix

| Upstream dependency | Artemis path | Replacement type | Status | Evidence |
|---|---|---|---|---|
| Sol4k 0.7.0 | `artemis-sol4k-compat` plus native Foundation modules | Source/API compat plus native replacement | Verified | `Sol4kCompatTest`, `Sol4kCompatExtraTest`, `artemis-sol4k-compat.api` |
| SolanaKT / solana-kmp | `artemis-solana-kmp-compat` plus native Foundation modules | Source/API compat plus native replacement | Verified | `artemis-solana-kmp-compat.api`, compat tests |
| Solana Mobile MWA clientlib / clientlib-ktx 2.1.0 | `artemis-mwa-compat`, `artemis-mwa-clientlib-compat`, `artemis-mwa-common-compat` | Source compat backed by Artemis MWA | Verified for compile/API and local protocol behavior; real-wallet matrix remains device-gated | MWA tests, API snapshots, `:artemis-mwa-compat:testDebugUnitTest` |
| Solana Mobile MWA walletlib 2.1.0 | `artemis-mwa-walletlib-compat` plus walletlib Android module | Source compat backed by Artemis wallet-side MWA | Verified for compile/API and local scenarios; real-wallet matrix remains device-gated | `MwaWalletlibCompatParityTest`, API snapshot |
| Seed Vault SDK | `artemis-seed-vault`, `artemis-seedvault-compat` | Compat wrapper over platform service | Partial | static/API surface verified; full behavior requires Saga/Seeker or simulator |
| `rpc-core` | `artemis-rpc-core-compat`, `artemis-rpc` | Source compat plus native transport | Verified | `RpcCoreCompatTest`, API snapshot |
| `web3-solana` | `artemis-web3-solana-compat`, `artemis-tx`, `artemis-vtx`, `artemis-programs` | Source compat plus native transaction/program builders | Verified for pinned snapshot | `Web3SolanaCompatProgramTest`, transaction byte fixtures, API snapshot |
| `io.github.funkatronics:multimult` 0.2.6 | `artemis-multimult-compat` | Source compat for Base58 APIs | Verified | `MultimultCompatTest`, `artemis-multimult-compat.api` |
| SPL Token | `artemis-programs` | Native replacement | Verified | program builder tests |
| Token-2022 | `artemis-token2022` | Native replacement | Verified except confidential transfer remains experimental | Token-2022 tests, TLV/zero-copy tests |
| Metaplex Android | `artemis-metaplex-android-compat`, `artemis-metaplex`, `artemis-candy-machine` | Selected source compat and native coverage | Partial | NFT/DAS/Candy Machine bridge tests plus typed unsupported query sentinel tests |
| Jupiter | `artemis-jupiter` | Native integration | Partial | module tests and API surface, broader route conformance pending |
| DAS / cNFT | `artemis-cnft` | Native integration | Verified for shipped flows | DAS fallback and cNFT tests |

## Core primitives

| Capability | solana-kmp | Sol4k | Solana Mobile app deps | Artemis | Status |
|---|---|---|---|---|---|
| Public key type | Yes | Yes | via app-side SDK | `Pubkey` and compat aliases | Verified |
| Keypair generation | Yes | Yes | N/A | `Keypair` | Verified |
| Ed25519 signing | Yes | Yes | N/A | `Crypto` | Verified |
| Base58 encode/decode | Yes | Yes | via `multimult` in some examples | `Base58`, `artemis-multimult-compat` | Verified |
| PDA derivation | Yes | Partial | via app-side SDK | `Pda.find()` | Verified |
| Base64 URL/session helpers | Partial | Partial | MWA protocol requirement | `PlatformBase64`, MWA protocol helpers | Verified |

## Transactions and programs

| Capability | Artemis module | Status | Evidence |
|---|---|---|---|
| Legacy transaction serialization | `artemis-tx` | Verified | internal round-trip plus web3.js byte fixtures |
| Versioned transaction v0 | `artemis-vtx` | Verified | v0 parser/serializer fixtures, ALT coverage |
| Address lookup tables | `artemis-vtx`, `artemis-programs` | Verified | create/extend/freeze/deactivate/close builders and v0 tests |
| Account ordering parity | `artemis-tx`, `artemis-vtx` | Verified for committed fixtures | transaction parity notes and fixtures |
| Compute budget | `artemis-compute`, `artemis-programs` | Verified | instruction builder tests |
| SPL Token | `artemis-programs` | Verified | native builders and compat tests |
| Token-2022 transfer hook and TLV views | `artemis-token2022` | Verified | transfer hook builders, zero-copy TLV/account extension views |
| Anchor enum args | `artemis-anchor` | Verified | enum IDL parse/serialize/deserialize tests |

## RPC and realtime

| Capability | Artemis module | Status | Evidence |
|---|---|---|---|
| Typed JSON-RPC methods | `artemis-rpc` | Verified | typed method tests and compat shims |
| Batch requests with per-item errors | `artemis-rpc` | Verified | batch result tests |
| Endpoint pool and circuit breaker | `artemis-rpc` | Verified | `CircuitBreakerTest`, endpoint tests |
| WebSocket account/signature/program/slot subscriptions | `artemis-ws` | Verified | realtime tests |
| Deterministic reconnect/resubscribe | `artemis-ws` | Verified | reconnect tests |
| HTTP polling fallback | `artemis-ws` | Partial | account/signature/program covered; logs have no HTTP equivalent |

## Wallet and Solana Mobile

| Capability | Artemis module | Status | Evidence |
|---|---|---|---|
| MWA 2.1.0 client API shape | `artemis-mwa-compat`, `artemis-mwa-clientlib-compat`, `artemis-mwa-common-compat` | Verified | 2.1.0 metadata, tests, API snapshots |
| `transact(sender, signInPayload, block)` high-level flow | `artemis-mwa-compat` | Verified | source surface and behavior tests |
| MWA sign messages / sign transactions / sign and send | `artemis-wallet-mwa-android`, compat modules | Verified | MWA tests and batch result tests |
| Sign-In With Solana payload/result | `artemis-wallet-mwa-android`, compat modules | Verified | SIWS tests, result byte-preservation tests |
| Persisted session secret for process death reauthorize | `artemis-wallet-mwa-android` | Verified | session secret store tests and `ArtemisMobile.create` install path |
| MWA migration sample build | `samples/mwa-compat-migration` | Verified | opt-in Android sample builds with upstream-import compat, native Artemis, and mixed transaction-building flows |
| Phantom/Solflare/Backpack/reference-wallet live device matrix | docs and planned instrumentation | Partial | device/app lab required |
| Seed Vault contract wrapper | `artemis-seed-vault`, `artemis-seedvault-compat` | Partial | API and provider checks verified; platform signing requires device/service |

## Drop-in compat shims

| Compat surface | Module | Status |
|---|---|---|
| `com.solana.mobilewalletadapter.clientlib.*` | `artemis-mwa-compat`, `artemis-mwa-clientlib-compat` | Verified |
| `com.solana.mobilewalletadapter.common.*` | `artemis-mwa-common-compat` | Verified |
| `com.solana.mobilewalletadapter.walletlib.*` | `artemis-mwa-walletlib-compat` | Verified |
| `com.solanamobile.seedvault.*` | `artemis-seedvault-compat` | Partial, device-gated |
| `org.sol4k.*` | `artemis-sol4k-compat` | Verified |
| `foundation.metaplex.*` / solana-kmp shapes | `artemis-solana-kmp-compat` | Verified |
| `com.solana.rpccore.*` and networking shims | `artemis-rpc-core-compat` | Verified |
| web3-solana public key, transaction, and program helpers | `artemis-web3-solana-compat` | Verified for pinned snapshot |
| `com.funkatronics.encoders.Base58` | `artemis-multimult-compat` | Verified |
| `io.github.funkatronics.multimult.Base58` group-style alias | `artemis-multimult-compat` | Verified |

## Ecosystem coverage

| Area | Artemis module | Status |
|---|---|---|
| Token Metadata read/write selected flows | `artemis-metaplex`, `artemis-nft-compat` | Partial |
| DAS asset lookup and fallback | `artemis-cnft` | Verified |
| Compressed NFT transfers with proof resolution | `artemis-cnft` | Verified |
| Candy Machine v3 mint_v2 builder and Metaplex compat bridge | `artemis-candy-machine`, `artemis-metaplex-android-compat` | Verified for mint_v2 path; broader lifecycle partial; unsupported query helpers return typed sentinels |
| MPL Core selected asset flows | `artemis-mplcore` | Partial |
| Jupiter quote/swap | `artemis-jupiter` | Partial |
| Solana Pay transfer/transaction URLs | `artemis-solana-pay` | Verified for shipped parser/builder flows |
| Solana Actions / Blinks | `artemis-actions` | In Progress |

## Packaging

| Capability | Module | Status | Evidence |
|---|---|---|---|
| Maven Central POM descriptions | root publishing config | Verified | per-module description map |
| Local staging publish | all publishable modules | Verified | `publish...ToLocalStagingRepository` gates |
| BOM | `artemis-bom` | Verified | Java Platform module published to local staging |
| Compat API snapshots | root `dumpApi` / `verifyApiSnapshots` | Verified | 11 compat snapshots including multimult |
| Source-import conformance suite | `artemis-conformance-suite` | Verified | `:artemis-conformance-suite:testDebugUnitTest` |

## Known hard limits

- Artemis complements Solana Mobile Stack. It does not replace MWA, Seed Vault, Android wallet approval UX, device hardware custody, or platform services.
- Seed Vault signing and real-wallet MWA behavior require Android devices or wallet apps. Local JVM/Android unit tests cover API shape, protocol serialization, and shim behavior, not every wallet vendor path.
- Metaplex support is selected-flow support, not a full replacement for every Metaplex SDK surface.