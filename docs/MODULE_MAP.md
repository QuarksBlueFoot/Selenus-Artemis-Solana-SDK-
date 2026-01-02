# Module Map

Every module in Artemis, what ring it belongs to, what it does, and when you need it.

## Foundation (Ring 1)

| Module | Purpose | You need this when... |
|--------|---------|----------------------|
| `artemis-core` | PublicKey, Keypair, PDA derivation, Base58, cryptographic primitives | You are doing anything with Solana |
| `artemis-rpc` | JSON-RPC client, broad method coverage, typed wrappers, batch DSL, endpoint pool, circuit breaker | You need to talk to a Solana node |
| `artemis-ws` | WebSocket subscriptions, auto-reconnect, polling fallback | You need real-time account or slot updates |
| `artemis-tx` | Legacy transaction construction, serialization, signing, durable nonce | You are building or signing transactions |
| `artemis-vtx` | Versioned transactions (v0), address lookup tables | You need v0 transactions or ALTs |
| `artemis-programs` | System, Token, Associated Token, Compute Budget, Stake instructions | You are sending SOL, creating accounts, or using SPL Token |
| `artemis-errors` | Structured error types, on-chain error decoding | You want meaningful error messages instead of raw codes |
| `artemis-logging` | Lightweight structured logging | You want SDK-level log output |
| `artemis-compute` | Compute unit estimation, priority fee optimization | You are optimizing transaction landing rates |

## Mobile (Ring 2)

| Module | Purpose | You need this when... |
|--------|---------|----------------------|
| `artemis-wallet` | Wallet abstraction: Local, Adapter, Raw signing strategies | You are signing transactions in any context |
| `artemis-wallet-mwa-android` | Mobile Wallet Adapter 2.0 client | You are building an Android dApp that connects to mobile wallets |
| `artemis-seed-vault` | Seed Vault integration | You are building for Saga or devices with Seed Vault |

## Ecosystem (Ring 3)

| Module | Purpose | You need this when... |
|--------|---------|----------------------|
| `artemis-token2022` | Token-2022 extensions (transfer fees, interest, metadata, confidential transfers, CPI guard) | Your app works with Token-2022 mints |
| `artemis-metaplex` | Token Metadata: metadata, editions, collections | You are reading or writing NFT metadata |
| `artemis-mplcore` | MPL Core (Asset) program | You are working with MPL Core assets |
| `artemis-cnft` | Compressed NFTs via Bubblegum | You are minting or transferring cNFTs |
| `artemis-candy-machine` | Candy Machine v3 minting | You are integrating candy machine mints |
| `artemis-solana-pay` | Solana Pay protocol | You are building payment flows |
| `artemis-anchor` | Anchor IDL parsing, Borsh serialization, type-safe program client | You are interacting with Anchor programs |
| `artemis-jupiter` | Jupiter DEX aggregator: quotes, swaps, routing | You need token swaps |
| `artemis-actions` | Solana Actions / Blinks | You are consuming or serving Solana Actions |

## Advanced (Ring 4)

| Module | Purpose | You need this when... |
|--------|---------|----------------------|
| `artemis-privacy` | Stealth addresses, encrypted memos, confidential transfers | You are building privacy-preserving payment flows |
| `artemis-streaming` | Zero-copy account streaming via WebSocket | You need high-throughput low-allocation account updates |
| `artemis-universal` | IDL-less program discovery and interaction | You need to interact with programs you don't have an IDL for |
| `artemis-simulation` | Transaction simulation and analysis | You want to dry-run transactions before submitting |
| `artemis-batch` | Automatic transaction batching | You need to send many transactions efficiently |
| `artemis-scheduler` | Network-aware transaction scheduling | You want the SDK to pick optimal send times |
| `artemis-offline` | Offline transaction queue with automatic retry | Your app needs to handle intermittent connectivity |
| `artemis-portfolio` | Real-time portfolio tracking via WebSocket | You are building a portfolio view |
| `artemis-replay` | Transaction replay and analysis | You need to inspect or replay past transactions |
| `artemis-gaming` | Session keys, verifiable randomness, state proofs | You are building on-chain games |
| `artemis-depin` | DePIN device attestation | You are building DePIN infrastructure |
| `artemis-nlp` | Natural language transaction parsing | You want users to describe transactions in plain English |
| `artemis-intent` | Human-readable transaction intent decoding with risk analysis | You want to show users what a transaction will do |
| `artemis-preview` | Transaction preview CLI: simulates and renders transaction effects | You want to inspect transaction behavior before submission |

## Compatibility (Ring 5)

| Module | Purpose | You need this when... |
|--------|---------|----------------------|
| `artemis-discriminators` | Program discriminator utilities | You are decoding account data and need discriminator matching |
| `artemis-nft-compat` | Unified NFT layer across Metaplex, MPL Core, cNFT | You want one API for all NFT standards |
| `artemis-tx-presets` | Pre-composed tx patterns (ATA + priority fees + resend) | You want ready-made transaction templates |
| `artemis-candy-machine-presets` | Candy Machine mint presets | You want a one-call candy machine mint |
| `artemis-presets` | Preset registry, lightweight interfaces for composing modules | You want to bundle multiple modules together |
