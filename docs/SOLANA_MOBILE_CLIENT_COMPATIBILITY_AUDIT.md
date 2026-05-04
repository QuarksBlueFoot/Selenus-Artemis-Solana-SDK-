# Solana Mobile client compatibility audit

This is the claim boundary for Artemis as a replacement for Solana client SDK dependencies.

## Final framing

Artemis is a drop-in replacement for Solana client SDK dependencies where a source-compatible `interop/artemis-*-compat` module is listed as `Verified` below. It is fully compatible with Solana Mobile Stack primitives at the client boundary.

Artemis does **not** replace:

- the Mobile Wallet Adapter protocol specification;
- wallet applications or wallet approval UX;
- Seed Vault custody, secure hardware, TEE/enclave behaviour, or device policy;
- Solana Mobile OS/platform services;
- on-chain programs such as SPL Token, Token-2022, Token Metadata, or Candy Machine.

## Dependency audit

| Upstream dependency | Artemis module | Audited pin | Status | Evidence | Remaining boundary |
|---|---|---:|---|---|---|
| MWA clientlib-ktx | `artemis-mwa-compat` | 1.4.3 | Verified | `MwaCompatParityTest`, API snapshot, behavior gate | Client library only; MWA protocol and wallet UX remain upstream/platform concerns |
| MWA clientlib | `artemis-mwa-clientlib-compat` | 1.4.3 | Verified | `MwaCompatParityTest`, `MwaCompatResultsTest`, API snapshot | Requires a live session bridge for real wallet flows |
| MWA walletlib | `artemis-mwa-walletlib-compat` | 1.4.3 | Verified / Partial | `MwaWalletlibCompatParityTest`, API snapshot | `RemoteWebSocketServerScenario` remains a typed partial stub until reflector support lands |
| MWA common | `artemis-mwa-common-compat` | 1.4.3 | Verified | `MwaCommonCompatTest`, API snapshot; constants are re-published at upstream FQNs | Constants/protocol contract only; does not alter MWA wire semantics |
| Seed Vault SDK | `artemis-seedvault-compat` | 0.4.0 | Verified client surface | `SeedVaultCompatTest`, API snapshot, `SeedVaultContractClient` tests in native module | Seed Vault service/custody remains device-provided and cannot be replaced by Artemis |
| rpc-core | `artemis-rpc-core-compat` | main@2026-01-09 | Verified / Partial | `RpcCoreCompatTest`, API snapshot | `KtorNetworkDriver` / `OkioNetworkDriver` FQNs are not ported; use `ArtemisHttpNetworkDriver` or any custom `HttpNetworkDriver` |
| web3-solana / web3-core | `artemis-web3-solana-compat` | main@2025-08 | Verified / Partial | `Web3SolanaCompatProgramTest`, API snapshot, tx/vtx byte fixtures | Source-compatible for pinned snapshot. Newer web3-core 0.3.x Token-2022 and ATA idempotent surfaces need a future pin refresh |
| Sol4k | `artemis-sol4k-compat` | 0.7.0 | Verified | `Sol4kCompatTest`, `Sol4kCompatExtraTest`, API snapshot | Claims are scoped to Sol4k 0.7.0 public surface |
| solana-kmp / SolanaKT family | `artemis-solana-kmp-compat` | main@2024-06-05 | Verified | `SolanaKmpCompatTest`, API snapshot | Upstream is dormant; claims target the pinned snapshot, not untracked forks |
| Metaplex Android / KMM | `artemis-metaplex-android-compat` plus Artemis NFT modules | main@2024-04-06 | Partial | `MetaplexAndroidCompatTest`, NFT compatibility tests | NFT read, tokens, DAS, and selected metadata builders are covered. Auction House and full Candy Machine mutation surfaces are not claimed |

## Changes from the final pass

- Expanded `artemis-web3-solana-compat` `TokenProgram` routing so existing Artemis-native SPL Token builders are available through the `com.solana.programs.TokenProgram` FQN: `initializeMint2`, `initializeMint`, `approve`, `revoke`, `burn`, `closeAccount`, `transferChecked`, and `syncNative`.
- Added `Web3SolanaCompatProgramTest` to exercise those program helpers and `Message.Builder` compilation.
- Added `RpcCoreCompatTest` to exercise JSON-RPC request serialization, typed result/error decoding, rpc-core model shapes, and `SolanaRpcClient` constructors.
- Added `MwaCommonCompatTest` and `SeedVaultCompatTest` so common protocol constants and Seed Vault client shim types are covered by runtime tests in addition to API snapshots.
- Expanded CI, release, and local verification gates to run MWA clientlib, MWA walletlib, rpc-core, web3-solana, Sol4k, solana-kmp, and Metaplex Android compatibility tests.
- Regenerated compat API snapshots so public surface drift is intentional and reviewable.

## What Solana Mobile can adopt with minimal code changes

Solana Mobile or a Solana Mobile app can adopt Artemis in three modes:

1. **Native Artemis APIs**: use `RpcApi`, `Transaction`, `VersionedTransaction`, `WalletSession`, `MwaWalletAdapter`, `SeedVaultContractClient`, and ecosystem modules directly.
2. **Source-compatible client migration**: replace Maven coordinates with `xyz.selenus:artemis-*-compat` modules for the verified upstream package FQNs. Existing imports continue compiling within the pinned surfaces.
3. **Mixed mode**: keep upstream MWA / Seed Vault platform flows and use Artemis for RPC, transaction construction, program helpers, NFT/DAS, retries, endpoint failover, and wallet-session ergonomics.

The honest claim is therefore:

> Artemis is a drop-in replacement for Solana client SDK dependencies (`web3.js`-style transaction primitives, Sol4k, solana-kmp/SolanaKT, MWA client libraries, rpc-core, Seed Vault client helpers, and supported Metaplex Android surfaces), fully compatible with Solana Mobile Stack primitives. Artemis does not replace the Solana Mobile Stack platform, MWA protocol, wallet apps, or Seed Vault custody boundary.

## Remaining follow-up work before stronger claims

- Refresh `artemis-web3-solana-compat` against Funkatronics web3-core 0.3.x and add fixtures for any changed Token-2022 / ATA idempotent APIs.
- Add native SPL Token builders before claiming `setAuthority`, freeze, or thaw parity.
- Add rpc-core `KtorNetworkDriver` / `OkioNetworkDriver` FQN adapters only if zero-import-change migration for those concrete transports becomes a release goal.
- Keep Metaplex Android/KMM language partial until Auction House and full Candy Machine mutations are implemented and tested.
- Keep iOS language explicit: React Native can expose shared TypeScript calls, but MWA and Seed Vault primitives are Android Solana Mobile features with fallback hooks on iOS.
