# Ecosystem support

Artemis includes native support for selected Metaplex, DAS, cNFT, Jupiter, and Solana Pay flows. Do not describe it as a full Metaplex replacement.

| Area | Module | Status | Supported |
|---|---|---|---|
| Token Metadata reads | `artemis-metaplex`, `artemis-nft-compat` | Verified/Partial | Metadata, MasterEdition, collection authority records, selected PDA helpers. |
| Token Metadata writes | `artemis-metaplex`, `artemis-nft-compat` | Partial | create/update/verify selected builders. Burn and every pNFT edge are not claimed. |
| DAS asset lookup | `artemis-cnft` | Verified | asset, assets by owner, collection flows through Helius/RPC-compatible clients. |
| DAS fallback | `artemis-cnft` | Verified | primary/fallback router with cooldown plus RPC fallback. |
| Compressed NFTs | `artemis-cnft` | Verified | proof resolution and transfer-oriented marketplace flows. |
| Candy Machine v3 | `artemis-candy-machine`, `artemis-metaplex-android-compat` | Partial | mint_v2 instruction builder and Metaplex Android compat bridge. Unsupported lookup helpers return typed `UnsupportedMetaplexFeature` sentinels. Full lifecycle mutations remain partial. |
| MPL Core | `artemis-mplcore` | Partial | selected asset/plugin flows. Full upstream coverage is not claimed. |
| Auction House | none claimed | Unsupported | Do not claim support. Android compat query placeholders return typed `UnsupportedMetaplexFeature` sentinels; action placeholders return typed not-implemented results. |
| Jupiter | `artemis-jupiter` | Partial | quote/swap client surface; broader live route matrix pending. |
| Solana Pay | `artemis-solana-pay` | Verified for shipped flows | transfer and transaction request URL parse/build/validation flows. |
| Solana Actions / Blinks | `artemis-actions` | In Progress | GET/POST, parameters, callbacks, actions.json delegation, QR/deep links. Interactive replay coverage remains open. |

Unsupported or unimplemented ecosystem features should fail clearly with typed feature flags or explicit unsupported exceptions. Silent success stubs are not acceptable for public compatibility claims.