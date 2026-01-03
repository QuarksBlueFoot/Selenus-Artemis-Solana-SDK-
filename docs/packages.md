# Package map

This file lists the actual Gradle modules in this repo and what each one is for.
All modules are designed to be **optional, drop-in**, and mobile-first.

## Core

- **artemis-core**: basic primitives, codecs, and shared utilities
- **artemis-runtime**: runtime adapters and small platform glue
- **artemis-rpc**: JSON-RPC client + typed helpers (base64 account reads, filters)
- **artemis-ws**: websocket subscriptions with retry/backpressure
- **artemis-tx**: transaction building primitives
- **artemis-compute**: compute budget + priority fee helpers
- **artemis-wallet**: wallet adapter interface + send pipeline
- **artemis-wallet-mwa-android**: Android Mobile Wallet Adapter implementation
- **artemis-errors**: stable error taxonomy + mappers
- **artemis-logging**: logging facade (optional bridges)

## Program toolkits

- **artemis-programs**: common program IDs + small instruction builders (System/Token/etc)
- **artemis-token2022**: Token-2022 TLV decode + utilities
- **artemis-vtx**: tx utilities (ALT helpers, budget advising)
- **artemis-discriminators**: instruction/data discriminators and decode helpers

## NFTs

- **artemis-metaplex**: Metaplex-friendly helpers where appropriate (no vendor lock)
- **artemis-nft-compat**: optional PDAs + metadata fetch/parse helpers
- **artemis-cnft**: compressed NFT helpers
- **artemis-mplcore**: MPL Core helpers
- **artemis-candy-machine**: Candy Machine v3 + Candy Guard tooling (including v58 intelligence layer)

## Presets

- **artemis-presets**: optional preset registry interfaces (v61)
- **artemis-tx-presets**: optional transaction composer presets (ATA + priority + resend) (v59)
- **artemis-candy-machine-presets**: optional one-call Candy Machine mint presets (v60)

## Samples

- **samples/solana-mobile-compose-mint**: MWA + Candy Machine mint preset walkthrough (docs-only)
- **samples/solana-mobile-compose-mint-app**: optional Android app module (MWA + Candy Machine mint) (v64)
