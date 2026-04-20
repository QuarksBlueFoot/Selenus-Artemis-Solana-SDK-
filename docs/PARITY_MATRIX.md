# Parity matrix

Feature-by-feature honest status of Artemis against the Kotlin/Android Solana SDK ecosystem. Each status is gated on what the tests in the repo actually exercise.

## Legend

- **Verified** — implementation shipped and exercised by a test that would fail if the feature broke.
- **In Progress** — implementation shipped, more work scheduled; treat as a preview surface.
- **Partial** — feature works for the documented happy path; known edge cases or upstream behaviours are not covered yet.
- **Experimental** — surface exists; behaviour may shift before 1.0 of that module.
- **Planned** — on the roadmap, no code yet.
- **N/A** — not applicable to that SDK.

## Release labels

Two orthogonal labels appear throughout the docs:

- `Artemis-native ready` — safe to adopt using Artemis APIs directly (`ArtemisMobile.create()`, `WalletSession`, `TxEngine`, `RpcApi`). Everything `Verified` here qualifies.
- `SMS-drop-in ready` — safe to use the `interop/artemis-*-compat` shims as a drop-in replacement for the official Solana Mobile clientlib/walletlib/seedvault imports. Requires both the native path and the compat parity tests to pass. Today this label applies to `Verified` items in the Wallet/Mobile and RPC rows below; the rest is `Partial` or `In Progress`.

## Core primitives

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| PublicKey type | Yes | Yes | via solana-kmp | via solana-kmp | `Pubkey` | Verified |
| Keypair generation | Yes | Yes | N/A | N/A | `Keypair` | Verified |
| Ed25519 signing | Yes | Yes | N/A | N/A | `Crypto` | Verified |
| Base58 encode/decode | Yes | Yes | N/A | N/A | `Base58` | Verified |
| Base64 encode/decode | Partial | Yes | N/A | N/A | `PlatformBase64` | Verified |
| PDA derivation | Yes | No | N/A | via solana-kmp | `Pda.find()` | Verified |
| HD key derivation | No | No | N/A | N/A | `Bip32.deriveKeypair()` + `SolanaDerivation` | Verified (RFC + golden vectors) |
| X25519 ECDH | No | No | N/A | No | `SeedVaultCrypto.deriveX25519SharedSecret` | Verified (symmetric + context-separation tests) |
| HKDF-SHA256 | No | No | N/A | N/A | `HkdfSha256.derive` | Verified (RFC 5869 Appendix A test cases) |
| AES-128-GCM session cipher | No | No | N/A | N/A | `Aes128Gcm` | Verified (round-trip + tamper-reject) |
| P-256 ECDH + ECDSA P1363 | No | No | Yes (inside walletlib) | N/A | `EcP256` | Verified (round-trip + DER/P1363 tests) |

## Transaction model

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Legacy transactions | Yes | Yes | N/A | N/A | `Transaction` | Verified |
| Versioned transactions (v0) | Partial | Partial | N/A | N/A | `VersionedTransaction` | Verified |
| Address lookup tables | Partial | No | N/A | N/A | `VersionedMessage.addressTableLookups` | Verified (parse + serialize round-trip) |
| Durable nonce support | No | No | N/A | N/A | `DurableNonce` | Partial (build + send covered; rollback edge cases not fuzzed) |
| Transaction serialization | Yes | Yes | N/A | N/A | Yes | Verified |
| Multi-signer support | Yes | Yes | N/A | N/A | `SolanaSigner.signTransaction` | Verified (index-by-pubkey lookup) |

## RPC client

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| JSON-RPC methods | ~20 | ~15 | via solana-kmp | via solana-kmp | 110 methods declared on `RpcApi` | Verified |
| Typed response wrappers | Some | Minimal | N/A | N/A | `*Typed` helpers | Verified |
| Batch requests | No | No | N/A | N/A | `callBatch` + `callBatchTyped` | Verified |
| Per-item batch error surface | No | No | N/A | N/A | `BatchItemResult.Ok` / `.Err(code,message,data)` | Verified |
| Commitment config | Yes | Yes | N/A | N/A | Yes | Verified |
| Endpoint failover | No | No | N/A | N/A | `RpcEndpointPool` | Verified |
| Circuit breaker | No | No | N/A | N/A | Yes | Verified |
| Blockhash cache | No | No | N/A | N/A | `BlockhashCache` | Verified |
| Retry classification (sockets, TLS, EOF, timeouts) | No | No | N/A | N/A | `shouldRetry` typed branches | Verified |

## WebSocket

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Account subscriptions | No | No | N/A | N/A | Yes | Verified |
| Program subscriptions | No | No | N/A | N/A | Yes | Verified |
| Signature subscriptions | No | No | N/A | N/A | Yes | Verified |
| Slot subscriptions | No | No | N/A | N/A | Yes | In Progress |
| Real transport pings (OkHttp `pingInterval`) | N/A | N/A | N/A | N/A | Yes | Verified |
| Reconnect only on `onOpen` success | N/A | N/A | N/A | N/A | Yes | Verified |
| Deterministic resubscribe | N/A | N/A | N/A | N/A | Yes | Verified |
| HTTP polling fallback (acct/sig/prog) | N/A | N/A | N/A | N/A | Yes | Partial (logs-subscribe has no HTTP equivalent; surfaces a typed `logsPollingUnavailable` event) |
| Collector-job retention on reconnect | N/A | N/A | N/A | N/A | Yes | Verified |
| Typed `ConnectionState` StateFlow | No | No | N/A | N/A | `ConnectionState` | Verified |
| Endpoint rotation | No | No | N/A | N/A | Yes | Verified |

## Wallet / Mobile

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Wallet abstraction | No | No | No (raw protocol) | No | `WalletSession` | Verified |
| MWA 2.0 client (authorize/addresses/features/auth_token) | No | No | Yes | No | `artemis-wallet-mwa-android` | Verified |
| MWA auth lifecycle (unified `authorize(auth_token=...)`) | No | No | Yes | No | `WalletSessionManager` | Verified |
| `withWallet { }` retry narrowed to session-expiry sentinels | No | No | No | No | `SessionExpiredException` | Verified |
| Sign transactions (MWA 2.0 optional) | N/A | N/A | Yes | N/A | `MwaWalletAdapter.signMessages` | Verified |
| Sign and send transactions | N/A | N/A | Yes | N/A | Yes | Verified |
| Sign-and-send wallet-unsupported fallback (`SignedButNotBroadcast` + injectable `RpcBroadcaster`) | N/A | N/A | N/A | N/A | Yes | Verified |
| SIWS (Sign In With Solana) payload round-trip (incl. `resources`) | N/A | N/A | Partial | N/A | Yes | Verified |
| Keystore-backed auth token (AES-256-GCM, fail-closed) | No | No | Partial | No | `KeystoreEncryptedAuthTokenStore` | Verified |
| HMAC session secret persisted for reauthorize after process death | No | No | No | No | `SessionManager.installPersistedSecret` | In Progress (requires caller-side Keystore wiring) |
| MWA session sequencing (atomic counters, fail-on-close) | No | No | Yes | No | `MwaSession` | Verified |
| Local WebSocket server hardened (loopback, origin, ping/pong, frame cap, fragmentation) | N/A | N/A | Yes | N/A | `MwaWebSocketServer` | Verified |
| Seed Vault (system-service custody; secure EE) | No | No | Separate SDK | No | `artemis-seed-vault` | Partial (device required for full behaviour; wrapper Verified, service is upstream) |
| Seed Vault strict contract/provider split | N/A | N/A | N/A | N/A | `SeedVaultContractClient` + `*Provider` | Verified |
| Seed Vault provider trust checks (platform signature / allowlist) | N/A | N/A | N/A | N/A | `SeedVaultCheck.isTrustedProvider` | Verified |
| Seed Vault IPC timeouts + strict auth-token parsing | N/A | N/A | N/A | N/A | `parseAuthTokenStrict` + `withTimeout(30s)` | Verified |
| Local keypair signing | Yes | Yes | N/A | N/A | `WalletSession.Local` | Verified |
| React Native MWA wrapper | No | No | Yes | No | `advanced/artemis-react-native` | Partial (Android-only; RN platform/ready-state detection Verified) |

## MWA compat (drop-in path)

| Capability | Artemis module | Status |
|---|---|---|
| `com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter` (ktx) | `interop/artemis-mwa-compat` | Verified |
| `MobileWalletAdapterClient` low-level client with live `SessionBridge` | `interop/artemis-mwa-clientlib-compat` | Verified (no `UnsupportedOperationException` in the happy path) |
| `LocalAssociationScenario` with real P-256 keypair, loopback port reservation, base64url association token | `interop/artemis-mwa-clientlib-compat` | Verified |
| Nested result types (`AuthorizationResult`, `SignPayloadsResult`, `SignMessagesResult`, `SignAndSendTransactionsResult`) | `interop/artemis-mwa-clientlib-compat` | Verified |
| `LocalAdapterOperations` routes through live `MwaSessionBridge` | `interop/artemis-mwa-compat` | Verified |
| API-diff snapshot (`dumpApi` Gradle task) | `interop/artemis-mwa-*/api/*.api` | Verified |

## Seed Vault compat

| Capability | Artemis module | Status |
|---|---|---|
| `com.solanamobile.seedvault.Wallet` static surface | `interop/artemis-seedvault-compat` | Verified (every upstream static; `AuthTokenGuard` for the invalid-token edge) |
| `WalletContractV1` constants | `interop/artemis-seedvault-compat` | Verified (every public const; IntDef + `@Purpose`) |
| `SeedVault.isAvailable` / `AccessType` | `interop/artemis-seedvault-compat` | Verified |
| AIDL `ISeedVaultService` 9-method shape | `mobile/artemis-seed-vault/src/main/aidl` | Verified (reconciled with internal Kotlin proxy) |

## Common programs

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| System Program | Yes | Yes | N/A | N/A | 12 instructions | Verified |
| SPL Token | Partial | Partial | N/A | N/A | 10 builders | Verified |
| Associated Token | Partial | Partial | N/A | N/A | Yes | Verified |
| Compute Budget | No | No | N/A | N/A | Yes | Verified |
| Stake Program | No | No | N/A | N/A | 5 instructions | Verified |
| Memo Program | No | Partial | N/A | N/A | Yes | Verified |
| Address Lookup Table program | No | No | N/A | N/A | Yes | Partial (create + extend covered; close + freeze not yet) |

## Token-2022

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Transfer fees | No | No | N/A | No | Yes | Verified |
| Interest bearing | No | No | N/A | No | Yes | Verified |
| Permanent delegate | No | No | N/A | No | Yes | Verified |
| Default account state | No | No | N/A | No | Yes | Verified |
| Transfer hook | No | No | N/A | No | Yes | In Progress |
| Metadata pointer | No | No | N/A | No | Yes | Verified |
| Confidential transfers | No | No | N/A | No | Yes | Experimental |
| CPI guard | No | No | N/A | No | Yes | Verified |
| Immutable owner | No | No | N/A | No | Yes | Verified |
| Mint close authority | No | No | N/A | No | Yes | Verified |
| TLV decoding | No | No | N/A | No | Yes | Verified |

## NFT / Metaplex

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Token Metadata read | No | No | N/A | Yes | Yes | Verified |
| Token Metadata write | No | No | N/A | Yes | Yes | Partial (mint / update covered; burn / verify edge cases pending) |
| Editions | No | No | N/A | Yes | Yes | Partial |
| Collections | No | No | N/A | Yes | Yes | Verified |
| MPL Core | No | No | N/A | Partial | Yes | Partial |
| Compressed NFTs (Bubblegum) | No | No | N/A | Partial | `artemis-cnft` | Verified |
| cNFT transfer via MarketplaceEngine | No | No | N/A | No | `MarketplaceEngine` | Verified |
| Candy Machine v3 | No | No | N/A | No | `artemis-candy-machine` | In Progress |
| DAS by-owner / by-collection / single-asset | No | No | N/A | No | `ArtemisDas` / `HeliusDas` | Verified |
| DAS RPC fallback | No | No | N/A | No | `RpcFallbackDas` | Verified |
| DAS primary + fallback router with 30s cooldown | No | No | N/A | No | `CompositeDas` | Verified |
| Marketplace preflight (ownership, ATA, frozen) | No | No | N/A | No | `MarketplacePreflight` | Verified |
| Standalone ATA ensurer | No | No | N/A | No | `AtaEnsurer` | Verified |

## Developer tooling

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|---|---|---|---|---|---|---|
| Transaction simulation | No | No | N/A | N/A | `TxEngine` simulate stage | Verified |
| Compute estimation | No | No | N/A | N/A | `artemis-compute` | Verified |
| Priority fee helpers | No | No | N/A | N/A | Yes | Verified |
| Error decoding | Minimal | No | N/A | N/A | `artemis-errors` | Partial |
| Transaction engine (pipeline) | No | No | N/A | N/A | `TxEngine` | Verified |
| Retry pipeline with classified errors | No | No | N/A | N/A | `RetryPipeline` | Verified |
| Blockhash cache with TTL | No | No | N/A | N/A | `BlockhashCache` | Verified |
| Framework event bus | No | No | N/A | N/A | `ArtemisEventBus` | Verified |
| Compat API-diff snapshot | No | No | N/A | N/A | `./gradlew dumpApi` | Verified |

## Artemis-only capabilities

These are features no other Kotlin Solana SDK provides; none are required for the SMS-drop-in path.

| Capability | Module | Status |
|---|---|---|
| Typed WebSocket subscriptions (`RealtimeEngine`) | `artemis-ws` | Verified |
| DAS queries (Helius / RPC standard) | `artemis-cnft` | Verified |
| cNFT transfer with proof resolution (`MarketplaceEngine`) | `artemis-cnft` | Verified |
| Full mobile stack wiring (`ArtemisMobile.create()`) | `artemis-wallet-mwa-android` | Verified |
| Anchor IDL client | `artemis-anchor` | Partial |
| Jupiter DEX integration | `artemis-jupiter` | Partial |
| Solana Actions / Blinks | `artemis-actions` | In Progress |
| Privacy toolkit | `artemis-privacy` | Experimental |
| Zero-copy streaming | `artemis-streaming` | Experimental |
| Transaction batching | `artemis-batch` | In Progress |
| Offline queue | `artemis-offline` | In Progress |
| Portfolio tracking | `artemis-portfolio` | In Progress |
| Gaming primitives (session keys, VRF, state proofs) | `artemis-gaming` | Experimental |
| DePIN attestation | `artemis-depin` | Experimental |
| NLP transactions | `artemis-nlp` | Experimental |
| Intent decoding | `artemis-intent` | Verified |
| Universal program client | `artemis-universal` | Experimental |
