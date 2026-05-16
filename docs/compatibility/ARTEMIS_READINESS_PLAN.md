# Artemis readiness plan

This document tracks the implementation plan that previously lived in `docs/PARITY_MATRIX.md`. The matrix is now back to being the evidence source of truth; this file keeps the execution plan and the remaining gated work visible.

## Goal

Artemis is a mobile-first Kotlin Solana SDK that consolidates app-side Solana client dependencies while integrating with Solana Mobile primitives. Artemis should replace or wrap the libraries app developers import: Sol4k, SolanaKT/solana-kmp, `rpc-core`, `web3-solana`, MWA client libraries, transaction builders, token helpers, wallet session code, and selected ecosystem integrations.

Artemis does not replace Solana Mobile Stack itself. It uses Mobile Wallet Adapter and Seed Vault; it does not bypass wallet approval UX or hardware custody.

## Completed in this sweep

| Plan item | Result |
|---|---|
| Hard replacement matrix | Added `docs/compatibility/replacement-matrix.md` and restored `docs/PARITY_MATRIX.md` as evidence. |
| MWA 2.x stale pin | Updated MWA compat metadata from `1.4.3` to `2.1.0`; existing source already carries MWA 2.x fields and flow coverage. |
| `multimult` decision | Added `artemis-multimult-compat` with `com.funkatronics.encoders.Base58` plus `io.github.funkatronics.multimult.Base58` alias. |
| Source/API drift gate | Added `artemis-multimult-compat` to `dumpApi` and `verifyApiSnapshots`; generated its API snapshot. |
| BOM | Added publishable `artemis-bom` Java Platform module and local staging publish gate. |
| Claims docs | Added `docs/claims.md`, `docs/drop-in-compatibility.md`, `docs/solana-mobile.md`, and `docs/ecosystem-support.md`. |
| Transaction parity docs | Added `docs/conformance/transaction-parity.md`. |
| Compile conformance suite | Added `testing/artemis-conformance-suite` to compile and exercise source-compatible imports across MWA, Seed Vault, Sol4k, solana-kmp, web3-solana, rpc-core, Metaplex Android, and multimult. |
| MWA migration sample | Added opt-in Android sample `samples/mwa-compat-migration` covering upstream-import compat, native Artemis MWA, and mixed Artemis/web3-solana transaction building. |
| Metaplex silent stubs | Replaced Android compat query placeholders for Auction House and Candy Machine lookup helpers with typed `UnsupportedMetaplexFeature` sentinels and tests. |

## Remaining gated work

| Area | Required before stronger public claim | Gate |
|---|---|---|
| Real MWA wallets | Phantom, Solflare, Backpack, reference wallet authorize/sign/sign-and-send/reject/no-wallet cases | Android instrumentation/device lab |
| Seed Vault | Saga/Seeker or simulator public-key/signing/rejection/unavailable cases | Device or service-backed integration |
| web3-solana freshness | Refresh pinned upstream snapshot when Funkatronics publishes a newer stable package surface | API diff plus compile tests |
| Devnet | SOL/SPL/Token-2022/ALT/WebSocket end-to-end flows | opt-in devnet gate |

## Release gates

| Gate | Status |
|---|---|
| Focused build and API snapshots | Passing for the new shim and BOM sweep. |
| Conformance suite | Passing for Android source-import conformance. |
| MWA migration sample build | Passing with `-PenableAndroidSamples=true`. |
| Broad JVM/Android tests | Previously passing in release sweep; rerun before publishing. |
| Local Maven staging | Passing for the new BOM module. |
| Remote Central publish | Requires `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, and signing material. |
| Public claims | Restricted by `docs/claims.md`. |