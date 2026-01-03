# SDK audit and parity targets

This doc tracks parity goals versus common Kotlin/KMM Solana stacks and networking choices.

## Targets

- sol4k
- SolanaKT
- Metaplex KMM
- Ktor (networking parity and integration)
- Solana Mobile (Saga and Seeker)

## Current strengths

- v0 transactions and ALT planning utilities
- Token-2022 TLV decode and helpers
- cNFT and Bubblegum operations
- MPL Core create flows and plugin helpers
- websocket module with backpressure
- compute budget helpers (v42)
- wallet capability layer and send pipeline (v43)
- optional game pipeline stack (Arcane, Conduit)

## Parity checklist

### Common RPC methods
- getBalance
- getLatestBlockhash
- getAccountInfo
- getMultipleAccounts
- simulateTransaction
- sendRawTransaction
- getSignatureStatuses
- getTokenAccountsByOwner
- getProgramAccounts
- getBlockHeight / getSlot
- getTransaction (jsonParsed + base64)

### Token and NFT
- SPL Token basic (mint, ATA, transfers)
- Token-2022 extensions decode (TLV)
- Metaplex legacy metadata read
- cNFT mint, transfer, burn, decompress
- MPL Core create + plugin flows

### Wallet and Mobile
- wallet adapter contract (MWA friendly)
- re-sign and blockhash refresh flow
- batch sign fallback
- fee payer swap request hint
- partial signing contract
- robust error mapping for mobile UX

### Networking
- OkHttp default
- Ktor parity via HttpTransport bridge
- timeouts and retry knobs
- request signing hooks (for paid endpoints or auth)

## Remaining work to fully eclipse

- expanded RPC surface parity (cover every method used by SolanaKT)
- full transaction pipeline facade across legacy and v0 paths
- richer wallet adapter implementations (MWA + wallet-standard examples)
- stable error taxonomy (mobile friendly)
- more end-to-end samples: Saga, Seeker, game loops

## Solana RPC full coverage
- RpcApi includes most common methods and exposes callRaw for any RPC method.
