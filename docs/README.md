# Artemis SDK documentation

Top-level index for everything under `docs/`. Start at the root [README.md](../README.md) for installation and quick start.

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
| [REPLACE_SOLANA_MOBILE_STACK.md](REPLACE_SOLANA_MOBILE_STACK.md) | Replacing the official Solana Mobile Stack with Artemis |
| [migration-solana-mobile.md](migration-solana-mobile.md) | API-level migration notes for SMS users |

## Mobile integration

| Document | Covers |
| --- | --- |
| [MOBILE_APP_GUIDE.md](MOBILE_APP_GUIDE.md) | End-to-end Android integration walkthrough using `ArtemisMobile.create()` |

## Module guides

These are deep dives for individual ecosystem and advanced modules. Each guide includes a working code example and links back to the source files in the repo.

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
