# Selenus Artemis: Kotlin Solana SDK

## Overview
Tired of juggling five different half-baked libraries? Artemis fixes that. It's a modular, Android-ready Kotlin Solana SDK that gives you a single, strong, foundation.

Artemis is **SDK-only**:
- no hosted services
- no RPC lock-in
- no wallet UI assumptions

It is designed for **mobile reliability**, **v0-first transactions**, and **modern Solana standards**.

## Core Design Principles
- Versioned (v0) transactions are first-class
- Address Lookup Tables (ALT) are explicit and controllable
- Token-2022 is baseline, not optional
- Metaplex Core over legacy metadata-first NFTs
- We assume mobile networks are flaky, because they are.
- No silent retries or hidden state

## Module Breakdown 

### artemis-core
Shared domain models and constants used across the SDK.

### artemis-runtime
Core primitives:
- PublicKey utilities
- PDA derivation
- hashing / encoding helpers

### artemis-rpc
Typed JSON-RPC client with explicit commitment handling.

### artemis-tx / artemis-vtx
Transaction and instruction layer:
- legacy + v0 tx support
- ALT integration
- signer abstraction
- compute budget helpers

### artemis-tx-presets
Predefined transaction presets for common compute/fee scenarios.

##Investigating the Class Name

I've verified that the class name CandyGuardMintV2Safe is indeed present in the relevant file, specifically in the file CandyGuardMintSafe.kt, as object CandyGuardMintV2Safe. The class name seems correct. I'm now proceeding to investigate the unresolved reference error.

Debugging Imports

I've pinpointed a possible cause of the unresolved reference: the import statement for CandyGuardMintV2Safe might be incorrect given the package structure. The initial investigation indicated the correct class and file. However, my latest check reveals that the importing file, CandyMachineMintPresets.kt, is in a subdirectory. I'm now verifying the import path to make sure it accurately reflects the file's location.

Pinpointing# artemis-token2022
Full Token-2022 support:
- instruction builders
- TLV decoding
- extension parsing

### artemis-cnft
Compressed NFT utilities:
- Bubblegum-compatible helpers
- DAS-oriented data models
- marketplace helpers

### artemis-mplcore
Metaplex Core (v2) helpers:
- asset creation flows
- plugin utilities

### artemis-metaplex
Legacy Metaplex compatibility utilities (opt-in).

### artemis-candy-machine
Candy Machine v3 + Candy Guard instruction builders.

### artemis-candy-machine-presets
High-level presets for Candy Machine workflows.

### artemis-ws
WebSocket subscriptions:
- reconnect + resubscribe
- dedupe protection
- mobile-safe lifecycle

### artemis-gaming
Session keys, batching helpers, compute presets.

### artemis-replay
Deterministic replay and frame recording utilities.

### artemis-wallet / artemis-wallet-mwa-android
Wallet abstractions and Solana Mobile Wallet Adapter bindings.

## What Artemis Replaces
- sol4k (partial tx + limited Token-2022)
- Metaplex KMM SDK (NFT-only scope)
- Solana Mobile clientlib (core tx/rpc logic)

Artemis is designed to sit *under* MWA and wallet UX layers.
