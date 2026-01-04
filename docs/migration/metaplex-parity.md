# Metaplex parity in Artemis (v65)

This doc is a factual checklist of Metaplex-related features covered by Artemis **without** requiring a separate Metaplex SDK.

Artemis intentionally focuses on **mobile-first** and **indexer-free** flows. Where Metaplex has broader surface area (e.g., Auction House), Artemis treats those as separate optional modules when/if needed.

## Covered today

### Token Metadata (mpl-token-metadata)
- PDA helpers: metadata + master edition PDAs.
- Account reads + decoders:
  - Metadata (name/symbol/uri/seller fee + creators + collection refs)
  - Master edition
  - Token record (pNFT)
  - Collection authority records
- Instruction Builders:
  - `createMetadataAccountV3`
  - `updateMetadataAccountV2`
  - `verifyCollection`
- Wallet NFT listing (heuristic: token accounts amount==1, then metadata batch fetch).

Metaplex-style queries (indexer-free, RPC-based):
- findByMint
- findAllByMintList
- findAllByOwner (heuristic)
- findAllByCreator (memcmp filter on metadata creator slots; may be heavy)
- findAllByCandyMachineV2 (best-effort creator-slot filter)

Implementation: `:artemis-nft-compat` (package `com.selenus.artemis.nft`).

### Candy Machine v3 + Candy Guard
- Candy Machine + Candy Guard account reads (no indexer).
- Guard manifest introspection + remaining accounts planning.
- Safe mint builder with human-readable errors.
- One-call mobile preset: `mintNewWithSeed` + priority fees + resend loop.

Implementation: `:artemis-candy-machine`, `:artemis-tx-presets`, `:artemis-candy-machine-presets`.

### Bubblegum (cNFT)
- Bubblegum instruction builders + PDAs.
- Marketplace-oriented helper flows (redeem/decompress, set tree delegate, etc.).

Implementation: `:artemis-cnft`.

### MPL Core
- Core program helpers (module present for apps that use Core assets).

Implementation: `:artemis-mplcore`.

## Not covered yet (by design)

These are not required for *minting + viewing* NFTs on mobile, and are intentionally kept out of the default surface area:

- **Auction House / Auctioneer** (marketplace listings + sales)
- **Gumdrop** / allowlist distribution tooling
- **Token Extras**

If your product needs these, they should land as **optional, separate modules** that follow the core Artemis tenets.

## One-stop Metaplex entry point

For convenience, Artemis provides a single facade:

- `com.selenus.artemis.metaplex.Metaplex`

This composes the supported features into one easy-to-use object while still allowing apps to depend on sub-modules directly.
