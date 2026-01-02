# Parity Matrix

Feature-by-feature comparison of Artemis against the Kotlin/Android Solana SDK ecosystem. Honest status for each capability.

**Legend:** Full = complete coverage, Partial = some coverage with gaps, Planned = on roadmap, N/A = not applicable to that SDK

## Core Primitives

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| PublicKey type | Yes | Yes | Via solana-kmp | Via solana-kmp | `Pubkey` | Full |
| Keypair generation | Yes | Yes | N/A | N/A | `Keypair` | Full |
| Ed25519 signing | Yes | Yes | N/A | N/A | `Crypto` | Full |
| Base58 encode/decode | Yes | Yes | N/A | N/A | `Base58` | Full |
| Base64 encode/decode | Partial | Yes | N/A | N/A | Yes | Full |
| PDA derivation | Yes | No | N/A | Via solana-kmp | `Pda.find()` | Full |
| HD key derivation | No | No | N/A | N/A | `Bip32.deriveKeypair()` | Full |

## Transaction Model

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Legacy transactions | Yes | Yes | N/A | N/A | `Transaction` | Full |
| Versioned transactions (v0) | Partial | Partial | N/A | N/A | `VersionedTransaction` | Full |
| Address lookup tables | Partial | No | N/A | N/A | Yes | Full |
| Durable nonce support | No | No | N/A | N/A | `DurableNonce` | Full |
| Transaction serialization | Yes | Yes | N/A | N/A | Yes | Full |
| Multi-signer support | Yes | Yes | N/A | N/A | Yes | Full |

## RPC Client

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| JSON-RPC methods | ~20 | ~15 | Via solana-kmp | Via solana-kmp | 65+ | Full |
| Typed response wrappers | Some | Minimal | N/A | N/A | Yes (`*Typed`) | Full |
| Batch requests | No | No | N/A | N/A | `batchRpc {}` DSL | Full |
| Commitment config | Yes | Yes | N/A | N/A | Yes | Full |
| Endpoint failover | No | No | N/A | N/A | `RpcEndpointPool` | Full |
| Circuit breaker | No | No | N/A | N/A | Yes | Full |
| Blockhash cache | No | No | N/A | N/A | `BlockhashCache` | Full |
| Rate limit handling | No | No | N/A | N/A | Yes | Full |

## WebSocket

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Account subscriptions | No | No | N/A | N/A | Yes | Full |
| Program subscriptions | No | No | N/A | N/A | Yes | Full |
| Signature subscriptions | No | No | N/A | N/A | Yes | Full |
| Slot subscriptions | No | No | N/A | N/A | Yes | Full |
| Auto-reconnect | N/A | N/A | N/A | N/A | Yes | Full |
| HTTP polling fallback | N/A | N/A | N/A | N/A | Yes | Full |

## Wallet / Mobile

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Wallet abstraction | No | No | No (raw protocol) | No | `WalletSession` | Full |
| MWA 2.0 client | No | No | Yes | No | `artemis-wallet-mwa-android` | Full |
| MWA auth lifecycle | No | No | Yes | No | Yes | Full |
| Sign transaction | N/A | N/A | Yes | N/A | Yes | Full |
| Sign and send | N/A | N/A | Yes | N/A | Yes | Full |
| Seed Vault | No | No | Separate SDK | No | `artemis-seed-vault` | Full |
| Local keypair signing | Yes | Yes | N/A | N/A | `WalletSession.Local` | Full |
| Android lifecycle safety | N/A | N/A | Partial | N/A | Session management | Partial |

## Common Programs

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| System Program | Yes | Yes | N/A | N/A | 12 instructions | Full |
| SPL Token | Partial | Partial | N/A | N/A | Full coverage | Full |
| Associated Token | Partial | Partial | N/A | N/A | Yes | Full |
| Compute Budget | No | No | N/A | N/A | Yes | Full |
| Stake Program | No | No | N/A | N/A | 5 instructions | Full |
| Memo Program | No | Partial | N/A | N/A | Yes | Full |

## Token-2022

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Transfer fees | No | No | N/A | No | Yes | Full |
| Interest bearing | No | No | N/A | No | Yes | Full |
| Permanent delegate | No | No | N/A | No | Yes | Full |
| Default account state | No | No | N/A | No | Yes | Full |
| Transfer hook | No | No | N/A | No | Yes | Full |
| Metadata pointer | No | No | N/A | No | Yes | Full |
| Confidential transfers | No | No | N/A | No | Yes | Full |
| CPI guard | No | No | N/A | No | Yes | Full |
| Immutable owner | No | No | N/A | No | Yes | Full |
| Mint close authority | No | No | N/A | No | Yes | Full |
| TLV decoding | No | No | N/A | No | Yes | Full |

## NFT / Metaplex

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Token Metadata read | No | No | N/A | Yes | Yes | Full |
| Token Metadata write | No | No | N/A | Yes | Yes | Full |
| Editions | No | No | N/A | Yes | Yes | Full |
| Collections | No | No | N/A | Yes | Yes | Full |
| MPL Core | No | No | N/A | Partial | Yes | Full |
| Compressed NFTs | No | No | N/A | Partial | `artemis-cnft` | Full |
| Candy Machine v3 | No | No | N/A | No | `artemis-candy-machine` | Full |

## Developer Tooling

| Capability | solana-kmp | Sol4k | Solana Mobile SDK | Metaplex KMM | Artemis | Artemis Status |
|------------|-----------|-------|-------------------|--------------|---------|---------------|
| Transaction simulation | No | No | N/A | N/A | `artemis-simulation` | Full |
| Compute estimation | No | No | N/A | N/A | `artemis-compute` | Full |
| Priority fee helpers | No | No | N/A | N/A | Yes | Full |
| Error decoding | Minimal | No | N/A | N/A | `artemis-errors` | Full |
| Transaction engine (pipeline) | No | No | N/A | N/A | `TxEngine` | Full |

## Artemis-only capabilities

These are features no other Kotlin Solana SDK provides:

| Capability | Module | Description |
|------------|--------|-------------|
| Anchor IDL client | `artemis-anchor` | Parse Anchor IDL, Borsh serialization, type-safe instruction building |
| Jupiter DEX integration | `artemis-jupiter` | Quotes, swaps, route optimization |
| Solana Actions / Blinks | `artemis-actions` | Fetch and execute Solana Actions |
| Privacy toolkit | `artemis-privacy` | Stealth addresses, encrypted memos, confidential transfers |
| Zero-copy streaming | `artemis-streaming` | Memory-efficient real-time account updates |
| Transaction batching | `artemis-batch` | Automatic transaction grouping and dispatch |
| Offline queue | `artemis-offline` | Durable offline transaction preparation |
| Portfolio tracking | `artemis-portfolio` | Live WebSocket portfolio sync |
| Gaming primitives | `artemis-gaming` | Session keys, verifiable randomness, state proofs |
| DePIN attestation | `artemis-depin` | Device identity and attestation |
| NLP transactions | `artemis-nlp` | Natural language to transaction intent |
| Intent decoding | `artemis-intent` | Human-readable transaction analysis |
| Universal program client | `artemis-universal` | Interact with programs without an IDL |
