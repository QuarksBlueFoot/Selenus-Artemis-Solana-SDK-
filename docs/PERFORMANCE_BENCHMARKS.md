# Performance benchmarks

Do not claim Artemis is faster, lighter, or more mobile-native than a named SDK without reproducible measurements. This document defines the benchmark evidence required for public performance claims.

## Required baselines

| Baseline | Use for |
| --- | --- |
| `@solana/web3.js` in React Native or Expo | JavaScript mobile client baseline |
| Solana Kit in React Native or Expo | Modern JavaScript client baseline |
| solana-kmp or Sol4k | Kotlin client baseline |
| Raw MWA clientlib flow | Wallet roundtrip baseline |
| Artemis native path | Target implementation |

Record exact package versions, device model, Android version, build type, network, RPC endpoint, and thermal state for every run.

## Metrics

| Metric | Measurement |
| --- | --- |
| Startup cost | Time from app launch to SDK initialized and ready for first RPC call |
| Transaction build time | Time to construct a common transfer, token transfer, and v0 ALT transaction |
| Serialization time | Time to serialize legacy and v0 transactions |
| Memory use | Peak Java/Kotlin heap and native heap after common wallet + tx flows |
| RPC latency overhead | Client overhead around a fixed RPC endpoint, excluding server latency where possible |
| WebSocket recovery | Time from network loss to resubscribed account/signature stream |
| Wallet roundtrip | Time from app request to wallet result for authorize, sign message, sign tx, and sign-and-send |
| Battery-sensitive loops | Allocations per repeated tx build and websocket event handling |

## Minimum benchmark table

| Operation | Baseline | Artemis | Device | Network | Runs | Result file |
| --- | --- | --- | --- | --- | --- | --- |
| SDK startup | TBD | TBD | TBD | N/A | TBD | TBD |
| Build SOL transfer | TBD | TBD | TBD | N/A | TBD | TBD |
| Build SPL transfer | TBD | TBD | TBD | N/A | TBD | TBD |
| Build v0 ALT tx | TBD | TBD | TBD | N/A | TBD | TBD |
| Serialize legacy tx | TBD | TBD | TBD | N/A | TBD | TBD |
| Serialize v0 tx | TBD | TBD | TBD | N/A | TBD | TBD |
| MWA authorize roundtrip | TBD | TBD | TBD | devnet | TBD | TBD |
| MWA sign tx roundtrip | TBD | TBD | TBD | devnet | TBD | TBD |
| WebSocket reconnect | TBD | TBD | TBD | devnet | TBD | TBD |

`TBD` means no public performance claim should be made for that row.

## Methodology rules

1. Run release builds for app-level benchmarks.
2. Warm up each operation before measuring.
3. Use at least 30 measured iterations for CPU-only microbenchmarks.
4. Use at least 10 measured iterations for wallet flows because they include human or wallet UI delays.
5. Separate client overhead from RPC/network latency when possible by measuring with a fixed local or controlled endpoint.
6. Publish raw result files and benchmark harness code with the claim.
7. Do not compare debug JavaScript bundles against release Kotlin binaries.
8. Do not mix emulator and physical-device results in the same table.

## Claim policy

- "Mobile-first" is acceptable as architecture language when it refers to Kotlin, coroutines, lifecycle-aware sessions, and low-dependency module design.
- "Faster than web3.js", "lower memory", or similar comparative claims require the table above to be filled for the exact baseline version.
- If only one device was tested, say so.
- If a result depends on wallet behavior, name the wallet and version.
