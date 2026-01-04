# NFT compat module

Module: `:artemis-nft-compat`

This module provides Metaplex Token Metadata compatible helpers without forcing NFT opinions into Artemis core.

## What you get

### PDA helpers
- metadata PDA
- master edition PDA
- edition marker PDA
- token record PDA (programmable NFTs)
- collection authority record PDA

### Parsers
- Metadata (name, symbol, uri, seller fee, creators, collection, uses)
- MasterEditionV2 (supply, maxSupply)

### High-level client
- `fetchMetadata(mint)`
- `fetchMasterEdition(mint)`
- `listWalletNfts(owner)` (RPC-only, no indexer)

### Instruction builders (Token Metadata program)
- createMetadataAccountV3
- createMasterEditionV3
- updateMetadataAccountV2
- signMetadata
- verifyCollection
