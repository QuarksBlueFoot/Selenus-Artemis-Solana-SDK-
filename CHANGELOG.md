# Changelog

## 1.0.8

Patch release focusing on code hygiene and legacy cleanup.

### Changes
- **Refactor**: Defined Artemis-native `SeedVaultConstants` with full documentation, ensuring clean protocol implementation without blind code copying.
- **Cleanup**: Removed legacy `com.solana` packages and duplicate `SignInWithSolana` logic from MWA module.
- **Verification**: Validated MWA and Seed Vault implementations are self-contained.

## 65

Hardening + Metaplex parity pass.

### Changes

- Added `@ExperimentalArtemisApi` annotation for clarifying stability boundaries.
- Added `:artemis-metaplex` one-stop `Metaplex` facade that composes supported Metaplex program workflows:
  - Token Metadata reads via `:artemis-nft-compat`
  - Candy Machine v3 mint presets via `:artemis-candy-machine-presets`
  - Bubblegum/cNFT flows via `:artemis-cnft`
- Added `docs/metaplex-parity.md` checklist.

- Added Metaplex-style NFT query helpers to `:artemis-nft-compat` (indexer-free):
  - `NftClient.findByMint`
  - `NftClient.findAllByMintList`
  - `NftClient.findAllByOwner` (heuristic: token accounts amount == 1)
  - `NftClient.findAllByCreator` (memcmp filter on metadata creator slots; may be heavy)
  - `NftClient.findAllByCandyMachineV2` (best-effort creator-slot filter)

These mirror the common Metaplex Android SDK “find” methods but keep Artemis indexer-free.

### Non-goals

- Auction House / Gumdrop are not included in default surface area. They can land as optional modules if/when needed.

## 64: Solana Mobile Compile-Proof Sample + CI Fix

### Changes

1. **CI now provisions Android SDK**
   - Adds `android-actions/setup-android@v3`
   - Installs `platforms;android-35` and `build-tools;35.0.0`

2. **Optional Android sample app** (`:samples:solana-mobile-compose-mint-app`)
   - Demonstrates:
     - `MwaWalletAdapter.connect()`
     - `CandyMachineMintPresets.mintNewWithSeed(...)`
   - Excluded from the default build unless enabled with:
     `-PenableAndroidSamples=true`

3. **CONTRIBUTING.md**
   - Defines build and review commands

### Tenets audit

- No new required dependencies in core modules
- Sample is optional and does not pollute Artemis core
- No TODOs or placeholders added

## 63

This release hardens Artemis for Solana Kit / Solana Mobile review by making builds reproducible from a fresh checkout.

### Added

- Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) so contributors and CI can build without a local Gradle install.
- GitHub Actions CI workflow that runs `./gradlew build` on push and pull requests.
- `scripts/verify.sh` for local verification.

### Notes

- The wrapper is intentionally lightweight and reads `gradle/wrapper/gradle-wrapper.properties` for the Gradle distribution URL.

## 62

Hardening pass for Solana Kit / Solana Mobile review readiness.

### Changes

- Docs: replaced stale package map with a module-accurate `docs/packages.md`.
- Docs: added `docs/public-api.md` to name the intended stable entrypoints.
- Docs: added `docs/why-artemis-vs-kotlin-sdks.md` for reviewer context.
- Samples: added `samples/solana-mobile-compose-mint` walkthrough for MWA + Candy Machine mint preset.

### No breaking changes

This release is documentation, clarity, and integration hardening. No runtime behavior changes.

## 60

### Added

- `artemis-candy-machine-presets`: optional one-call Candy Machine mint preset for mobile.
  - Uses v58 planner + safe builder to construct `mint_v2` deterministically.
  - Uses v59 transaction composer preset for optional ATA creation, priority fees, and resend/confirm.

### Docs

- Added: `docs/candy-machine-mint-presets.md`

## 59

Transaction composer presets.

### Added

- **artemis-tx-presets**: optional composer presets for mobile and games
  - create missing ATAs (only when needed)
  - priority fees and compute limits injected via `SendPipeline`
  - resend and confirm loop for flaky mobile networks

### Docs

- `docs/tx-composer-presets.md`

## 56 highlights

Metaplex parity upgrades (optional module)

pNFT:
- Token record PDA + tolerant token record parser
- NftClient helper to fetch token records for UX (locked state, delegates, rule sets)

Collections:
- Collection authority record PDA + parser + fetch helper
- Token Metadata instruction builders:
  - approveCollectionAuthority
  - revokeCollectionAuthority
  - setAndVerifyCollection
  - unverifyCollection
  - verifySizedCollectionItem

Metadata parser now also surfaces collectionDetails (sized collections).

## 55 highlights

Metaplex-compatible capabilities (optional module)

- Token Metadata PDAs (metadata, master edition, edition marker, token record, collection authority record)
- Metadata parser expanded (collection + uses)
- MasterEditionV2 parser
- Token Metadata instruction builders:
  - createMetadataAccountV3
  - createMasterEditionV3
  - updateMetadataAccountV2
  - signMetadata
  - verifyCollection
- RPC-only wallet NFT listing helper:
  - listWalletNfts(owner) derives metadata PDAs and uses getMultipleAccounts for batch fetch

## 54 highlights

1) Token program helpers
- SPL Token builders (transfer, mintTo, burn, closeAccount, transferChecked, syncNative, initializeMint2)
- Token-2022 builders (initializeMint2, transferChecked, mintToChecked, closeAccount)
- Associated Token Account create instruction builder (supports Token and Token-2022)

2) Optional Metaplex-compatible NFT module
- PDA derivations for metadata and master edition
- Metadata fetch + minimal Borsh parse for name/symbol/uri/sellerFee/creators

3) Solana Mobile migration doc
- Replace Solana Mobile Kotlin clientlib with Artemis native MWA in 15 minutes

## 53 highlights

- Batch-aware MWA routing based on get_capabilities limits
- Per-batch retry helper for wallet operations
- Docs for batching behavior

## 52 highlights

- MWA feature detection via get_capabilities
- Automatic fallback routing between sign-and-send and sign-only
- Adapter helper signThenSendViaRpc for seamless routing

## 50 highlights

### Wallet and mobile app wiring

- Android MWA adapter adds optional sign-and-send fast path when wallet supports it
- DataStoreAuthTokenStore for authToken persistence
- Android sample includes a minimal Send SOL + Memo flow using Artemis transaction builder

Arcana and Arcane remain optional and do not affect core SDK usage.

## 49 highlights

- Android MWA wallet adapter module (reference implementation)
- Local signer wallet adapter for tests and server-side
- Wallet integration docs for Android and generic usage

## 48 highlights

Core SDK improvements focused on being a general-purpose Solana Kotlin SDK.

### RPC reliability and ergonomics

- JsonRpcClient now supports retry and backoff via RpcClientConfig
- RpcFacade groups RPC into smaller surfaces
- new docs: rpc-reliability.md and rpc-facade.md

### Game presets expansion

Arcane rollup presets now include CASINO and LOTTERY styles.
Arcane remains optional and separate from the core Solana replacement SDK.

## 47 highlights

- RPC "long tail" completion helpers and typed filter builders
- Confirmation utilities for mobile reliability
- Token and stake query helpers

### What shipped

- RpcFilters: memcmp and dataSize builders for getProgramAccounts
- RpcApi.confirmTransaction: signature status polling
- RpcApi.sendAndConfirmRawTransaction: one-call send + confirm
- additional wrappers for token, stake, fee, and slot/commitment methods
- callRaw remains available for any RPC not explicitly wrapped yet

This release reduces the need for `callRaw` in common apps while keeping full coverage.

## 46 highlights

- Stable error taxonomy for mobile UX (artemis-errors)
- RpcApi expanded toward full Solana JSON-RPC coverage
- RpcApi.callRaw added for any remaining methods not wrapped yet
- SendPipeline convenience overload uses default blockhash error detection

## 45 highlights

SolanaKT parity sweep for RPC.

### What shipped

- artemis-rpc rebuilt for correctness and maintainability
- expanded RPC method coverage used by mobile apps
- Ktor parity via HttpTransport remains supported
- compatibility map: docs/compat/solanakt.md
- request fixture unit tests for critical methods

### Next

- expand RPC methods used by token/NFT stacks (more filters, parsed variants)
- add mobile-friendly error taxonomy across RPC and wallet

## 44 highlights

- artemis-logging: optional SLF4J logging facade (reflection bridge) with stdout fallback
- artemis-rpc: pluggable HttpTransport for Ktor parity without extra dependencies
- audit checklist added (docs/audit.md)

This release improves mobile and app-stack compatibility while keeping Artemis modular.

## 43 highlights

Core SDK upgrade: Wallet capability layer and unified send pipeline facade.

### What shipped

- artemis-wallet:
  - WalletAdapter contract
  - WalletCapabilities + caching
  - request hints (sign, re-sign, fee payer swap)
  - SendPipeline facade with compute tuning and re-sign handling

### Why it matters

Android wallets differ in:
- signing formats
- batch support
- re-sign behavior
- fee payer swap and partial signing

This layer normalizes the differences so apps can drop in with fewer custom branches.

### Example

```kotlin
val result = SendPipeline.send(
  config = SendPipeline.Config(desiredPriority0to100 = 60),
  adapter = walletAdapter,
  getLatestBlockhash = { rpc.getLatestBlockhash() },
  compileLegacyMessage = { bh, computeIxs ->
    txCompiler.compileLegacy(blockhash = bh, computeIxs = computeIxs)
  },
  sendSigned = { signed -> rpc.sendRawTransaction(signed) },
  isBlockhashFailure = { err -> err.message?.contains("blockhash", ignoreCase = true) == true }
)
```

## 42 highlights

Core SDK upgrade: Compute budget helpers.

### What shipped

- artemis-compute: ComputeBudgetProgram builders and ComputeBudgetBuilder
- integration with TxBudgetAdvisor advice (v41)

### ComputeBudgetBuilder

Use game friendly presets or your own priority:

```kotlin
val instructions = ComputeBudgetBuilder()
  .forGameAction(ComputeBudgetBuilder.GamePriority.PLAYER_ACTION)
  .withLegacySizeBytes(advice.legacySizeBytes)
  .withV0SizeBytes(advice.v0SizeBytes)
  .buildInstructions()
```

Or build from the TxBudgetAdvisor output:

```kotlin
val instructions = ComputeBudgetBuilder()
  .fromAdvice(advice)
  .buildInstructions()
```

This reduces failed sends on mobile by keeping compute and fees consistent.

## 41 highlights

Core SDK upgrades focused on mobile reliability:

- wallet re-sign primitives (refresh + re-sign loop interfaces)
- transaction budget advisor (size and compute heuristics for v0 + ALTs)
- package map updated so modules stay easy to remember

### Wallet resilience

WalletResilience provides:
- WalletSigner interface
- SignerCapabilities
- withReSign() helper for blockhash refresh flows

This reduces "send failed" incidents on mobile when users background apps or approve slowly.

### TxBudgetAdvisor

TxBudgetAdvisor.advise() returns:
- legacy and v0 message size
- shouldPreferV0 decision
- recommended compute unit limit and price

Games can tune priority from 0..100 depending on user action value.

## 40 highlights

- zone rate policy helper for cost predictability
- package map to keep modules easy to remember

## 39 highlights

- websocket microblock stream can request delta-first payloads

MicroblockStreamClient.connect(, preferDelta = true, )

When preferDelta is true and the server has deltaB64:
- framesCjsonB64 is nulled
- deltaB64 is included

## 38 highlights

- preferDelta support for cheaper microblock fetches
- tuning client helper for Arcane Cloud recommendations
- retention guidance for studios that want low infra costs

### preferDelta

ArcaneMicroblockPager.fetch(, preferDelta = true)

This requests:
- preferDelta=true

Arcane Cloud will return:
- framesCjsonB64 = null
- deltaB64 filled when available

Decode:
- DeltaCodec.decodeDeltaB64Url(deltaB64)

### Retention

Arcane Cloud can delete old raw frames after microblocks exist.
Studios can keep a safety window for debugging and audits.

### Tuning

TuningClient.recommendations() returns JSON that includes:
- recommended ticks
- suggested retention window

## 37 highlights

- delta microblock decode support (gzip)
- genre preset helpers for cadence
- docs for cost and tuning

### Delta

Arcane Cloud may return deltaB64 on microblocks.

Decode:
- DeltaCodec.decodeDeltaB64Url(deltaB64)

This reduces egress and keeps Arcane Cloud costs comparable to other engines.

### Presets

GenrePresets provides recommended defaults for:
- shooters
- runners
- platformers
- RPG and MMORPG

## 36 highlights

### Arcane Cloud

- live microblock websocket stream pushes new microblocks as they are flushed
- microblock cadence is configurable for cost control

Environment:
- ARCANE_MICROBLOCK_RT_TICKS (default 30)
- ARCANE_MICROBLOCK_DUR_TICKS (default 60)

### Artemis SDK

- SessionMigration helper for rollover + gateway affinity
- Microblock stream client continues to work unchanged
- ReadModels expands with token, NFT, and program read model interfaces

This keeps the SDK neutral:
- raw RPC implementations work
- Luna (Helius) implementations work
- Arcane Cloud paid bundles work

## 35 highlights

- rollup microblock paging client
- rollup microblock stream client (websocket)
- memo helper for commit anchoring
- core read model interfaces (provider-agnostic)

### Rollup stream

MicroblockStreamClient connects to:
- /v1/rollup/microblocks/stream

It is newline-delimited json.

### Anchoring

RollupMemo.memosForCommit(commit) returns memo strings.
