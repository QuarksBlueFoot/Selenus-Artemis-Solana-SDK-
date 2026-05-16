# Claim audit

Use this table when writing README copy, release notes, marketing copy, or Maven descriptions. Claims must stay tied to evidence.

| Claim | Status | Evidence | Allowed wording | Avoid |
|---|---|---|---|---|
| Artemis replaces Sol4k for common app code | Mostly yes | `artemis-sol4k-compat` tests and API snapshot | "Sol4k migration-compatible" | "perfect Sol4k clone" |
| Artemis replaces SolanaKT / solana-kmp app-side primitives | Mostly yes | `artemis-solana-kmp-compat` API snapshot and tests | "solana-kmp-style migration path" | "full upstream fork replacement" |
| Artemis replaces Solana Mobile Stack | No | SMS includes platform protocols, wallet approval UX, and Seed Vault custody | "complements Solana Mobile Stack" | "replaces SMS" |
| Artemis supports Mobile Wallet Adapter | Yes | native MWA modules, compat modules, MWA tests, API snapshots | "integrates with MWA 2.1.0 and provides source-compatible client shims" | "fixes every wallet bug" |
| Artemis replaces Seed Vault | No | Seed Vault is a platform custody service | "uses Seed Vault through native and compat APIs" | "bypasses Seed Vault" |
| Artemis supports Token-2022 | Yes for shipped extensions | Token-2022 tests and TLV/zero-copy views | "supports Token-2022 flows including transfer hooks and TLV extension views" | "complete confidential transfer replacement" |
| Artemis supports Metaplex | Selected flows | NFT/DAS/Candy Machine bridge tests | "selected Metaplex, DAS, cNFT, and Candy Machine flows" | "full Metaplex replacement" |
| Artemis replaces `multimult` Base58 imports | Yes for Base58 | `artemis-multimult-compat` tests and API snapshot | "source-compatible Base58 shim for multimult" | "complete multimult multibase implementation" |
| Artemis is Maven-ready | Local staging yes, remote credentials gated | root publish config, `artemis-bom`, local staging tasks | "publishes to local staging and Central when credentials/signing are present" | "published to Central from this machine" |

## Required wording

Lead with: "Artemis is a mobile-first Kotlin Solana SDK that consolidates app-side Solana client dependencies."

Always include: "Artemis uses Mobile Wallet Adapter and Seed Vault; it does not replace Solana Mobile platform primitives."