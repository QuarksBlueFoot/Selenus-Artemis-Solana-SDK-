# Artemis SDK documentation

top-level index for everything under `docs/`. if you just want to install and run code, start at the root [README.md](../README.md).

## Architecture and design

| Document | Covers |
| --- | --- |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | Ring structure, layering, dependency rules |
| [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) | Internal design of ecosystem and advanced modules |
| [DEPENDENCY_RULES.md](DEPENDENCY_RULES.md) | What can depend on what, and why the ring rules matter |
| [MODULE_MAP.md](MODULE_MAP.md) | Every module with purpose and adoption context |
| [ADOPTION_BUNDLES.md](ADOPTION_BUNDLES.md) | Recommended dependency sets per use case |
| [PARITY_MATRIX.md](PARITY_MATRIX.md) | Side-by-side feature comparison vs solana-kmp, sol4k, Solana Mobile SDK, Metaplex KMM |

## Migration

| Document | Covers |
| --- | --- |
| [ADOPTING_ARTEMIS_WITH_SOLANA_MOBILE.md](ADOPTING_ARTEMIS_WITH_SOLANA_MOBILE.md) | Adopting Artemis as the client SDK layer above MWA and Seed Vault |
| [migration-solana-mobile.md](migration-solana-mobile.md) | API-level migration notes for SMS users |

## Mobile integration

| Document | Covers |
| --- | --- |
| [MOBILE_APP_GUIDE.md](MOBILE_APP_GUIDE.md) | End-to-end Android integration walkthrough using `ArtemisMobile.create()` |
| [WALLET_COMPATIBILITY_TESTING.md](WALLET_COMPATIBILITY_TESTING.md) | Real wallet test matrix and MWA edge-case coverage |
| [TRANSACTION_CORRECTNESS.md](TRANSACTION_CORRECTNESS.md) | Byte-level transaction correctness evidence policy |
| [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md) | Reproducible benchmark methodology before performance claims |

## Module guides

deep dives for individual ecosystem and advanced modules. each guide ships a working code example and links straight to source.

| Guide | Module |
| --- | --- |
| [guides/ANCHOR_GUIDE.md](guides/ANCHOR_GUIDE.md) | `artemis-anchor` |
| [guides/JUPITER_GUIDE.md](guides/JUPITER_GUIDE.md) | `artemis-jupiter` |
| [guides/ACTIONS_GUIDE.md](guides/ACTIONS_GUIDE.md) | `artemis-actions` |
| [guides/UNIVERSAL_GUIDE.md](guides/UNIVERSAL_GUIDE.md) | `artemis-universal` |
| [guides/NLP_GUIDE.md](guides/NLP_GUIDE.md) | `artemis-nlp` |
| [guides/STREAMING_GUIDE.md](guides/STREAMING_GUIDE.md) | `artemis-streaming` |

## Overview pages

| Document | Covers |
| --- | --- |
| [overview/ARTEMIS_OVERVIEW.md](overview/ARTEMIS_OVERVIEW.md) | Project narrative and roadmap |
| [overview/packages.md](overview/packages.md) | Maven coordinates for every published artifact |
| [overview/public-api.md](overview/public-api.md) | Public API surface map |
| [overview/ATTRIBUTION.md](overview/ATTRIBUTION.md) | Acknowledgements |

## Maintenance

| Document | Covers |
| --- | --- |
| [setup/GPG_SETUP.md](setup/GPG_SETUP.md) | GPG key setup for signing Maven Central releases |

## Project files

| Document | Covers |
| --- | --- |
| [../README.md](../README.md) | Project README |
| [../CHANGELOG.md](../CHANGELOG.md) | Version history |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Contribution rules |
| [../LICENSE](../LICENSE) | Apache License 2.0 |
