# Drop-in compatibility

This page groups migration surfaces by how much code a consumer should expect to change.

## Fully source-compatible for the documented scope

| Upstream imports | Artemis module | Scope |
|---|---|---|
| `org.sol4k.*` | `artemis-sol4k-compat` | RPC, PublicKey, Keypair, transactions, SPL/Token-2022 helpers, constants, exceptions. |
| `foundation.metaplex.*` solana-kmp shapes | `artemis-solana-kmp-compat` | PublicKey, Base58, Amount, clusters, RPC configs, transaction/message models, System/Memo, Keypair, EDDSA, DAS read interfaces. |
| `com.solana.rpccore.*`, `com.solana.networking.*` | `artemis-rpc-core-compat` | JSON-RPC envelope, network driver, request/response/error model, client model. |
| `com.solana.mobilewalletadapter.clientlib.*` | `artemis-mwa-compat`, `artemis-mwa-clientlib-compat` | MWA 2.1.0 client API shape, high-level transact flow, low-level protocol bridge. |
| `com.solana.mobilewalletadapter.walletlib.*` | `artemis-mwa-walletlib-compat` | walletlib scenarios, AssociationUri, server exceptions, JSON-RPC helpers, auth repository shape. |
| `com.solana.mobilewalletadapter.common.*` | `artemis-mwa-common-compat` | common protocol, SIWS payload/result, NotifyOnComplete helpers. |
| `com.funkatronics.encoders.Base58` | `artemis-multimult-compat` | multimult Base58 encode/decode and upstream invalid-input exception shapes. |
| `io.github.funkatronics.multimult.Base58` | `artemis-multimult-compat` | group-style alias for migration notes that use the Gradle group as a package. |

## API-compatible with small migration

| Use case | Native Artemis path | Change |
|---|---|---|
| Build, simulate, sign, and send mobile wallet transactions | `ArtemisMobile.create()` plus `sessionManager.withWallet { }` | Replace manual MWA/RPC wiring with one session manager. |
| Sol4k-style RPC calls with stronger transport behavior | `artemis-rpc` | Use suspend APIs instead of blocking wrappers where possible. |
| Token-2022 flows | `artemis-token2022` | Use typed extension/config builders instead of generic instruction bytes. |
| NFT/DAS/cNFT flows | `artemis-cnft`, `artemis-metaplex`, `artemis-candy-machine` | Use selected native clients; unsupported Metaplex areas are not claimed. |

## Compile-checked migration examples

| Artifact | Covers |
|---|---|
| `testing/artemis-conformance-suite` | Source-import conformance across MWA, Seed Vault, Sol4k, solana-kmp, web3-solana, rpc-core, Metaplex Android, and multimult. |
| `samples/mwa-compat-migration` | Opt-in Android sample for upstream-import MWA compat, native Artemis MWA, and mixed Artemis/web3-solana transaction-building flows. |

## Native replacement, not source-compatible

| Area | Artemis module | Notes |
|---|---|---|
| Reliability layer | `artemis-rpc`, `artemis-ws`, `artemis-vtx` | Endpoint pool, circuit breaker, replaying WebSocket, blockhash cache, and retry pipeline are Artemis-native. |
| Wallet session orchestration | `artemis-wallet`, `artemis-wallet-mwa-android` | Session manager owns auth token reuse, session expiry retry, event emission, and persisted MWA session secret. |
| Zero-copy account/TLV reads | `artemis-token2022`, `artemis-streaming` | Optimized Artemis APIs, not upstream import-compatible. |

## Device-gated surfaces

Seed Vault and real wallet compatibility require Android devices or wallet apps. Unit tests cover source/API shape and local protocol behavior; device gates must cover Phantom, Solflare, Backpack, the MWA reference wallet, and Seed Vault flows before any stronger live-wallet wording is used.