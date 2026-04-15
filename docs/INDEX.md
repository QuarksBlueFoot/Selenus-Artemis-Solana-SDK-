# Artemis SDK documentation index

A flat index of every document in the repo. The structured navigation lives at [docs/README.md](README.md).

## Top-level

| Document | Covers |
| --- | --- |
| [../README.md](../README.md) | Project overview, install, quick start |
| [../CHANGELOG.md](../CHANGELOG.md) | Release history |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | How to contribute |
| [../LICENSE](../LICENSE) | Apache License 2.0 |
| [../NOTICE](../NOTICE) | Required notice file |

## Architecture

| Document | Audience |
| --- | --- |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | Anyone wiring Artemis into an app |
| [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) | Contributors and reviewers |
| [DEPENDENCY_RULES.md](DEPENDENCY_RULES.md) | Contributors |
| [MODULE_MAP.md](MODULE_MAP.md) | All developers |
| [ADOPTION_BUNDLES.md](ADOPTION_BUNDLES.md) | Mobile app teams |
| [PARITY_MATRIX.md](PARITY_MATRIX.md) | SDK evaluators |

## Migration

| Document | Audience |
| --- | --- |
| [REPLACE_SOLANA_MOBILE_STACK.md](REPLACE_SOLANA_MOBILE_STACK.md) | Apps moving off the Solana Mobile Stack |
| [migration-solana-mobile.md](migration-solana-mobile.md) | API-level migration notes |

## Mobile integration

| Document | Audience |
| --- | --- |
| [MOBILE_APP_GUIDE.md](MOBILE_APP_GUIDE.md) | Android app developers |

## Feature guides

| Guide | Module |
| --- | --- |
| [guides/ANCHOR_GUIDE.md](guides/ANCHOR_GUIDE.md) | `artemis-anchor` |
| [guides/JUPITER_GUIDE.md](guides/JUPITER_GUIDE.md) | `artemis-jupiter` |
| [guides/ACTIONS_GUIDE.md](guides/ACTIONS_GUIDE.md) | `artemis-actions` |
| [guides/UNIVERSAL_GUIDE.md](guides/UNIVERSAL_GUIDE.md) | `artemis-universal` |
| [guides/NLP_GUIDE.md](guides/NLP_GUIDE.md) | `artemis-nlp` |
| [guides/STREAMING_GUIDE.md](guides/STREAMING_GUIDE.md) | `artemis-streaming` |

## Overview pages

| Document | Audience |
| --- | --- |
| [overview/ARTEMIS_OVERVIEW.md](overview/ARTEMIS_OVERVIEW.md) | Anyone evaluating the project |
| [overview/packages.md](overview/packages.md) | All developers |
| [overview/public-api.md](overview/public-api.md) | Contributors |
| [overview/ATTRIBUTION.md](overview/ATTRIBUTION.md) | Anyone |

## Setup

| Document | Audience |
| --- | --- |
| [setup/GPG_SETUP.md](setup/GPG_SETUP.md) | Release maintainers |

## Quick install snippet

The current published version is `2.2.0`. The source of truth for the version number is the `version` field in [../gradle.properties](../gradle.properties).

```kotlin
dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.2.0")
    implementation("xyz.selenus:artemis-rpc:2.2.0")
    implementation("xyz.selenus:artemis-ws:2.2.0")
    implementation("xyz.selenus:artemis-tx:2.2.0")
    implementation("xyz.selenus:artemis-vtx:2.2.0")
    implementation("xyz.selenus:artemis-programs:2.2.0")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.2.0")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.2.0")
    implementation("xyz.selenus:artemis-seed-vault:2.2.0")

    // NFT, DAS, marketplace
    implementation("xyz.selenus:artemis-cnft:2.2.0")

    // Optional
    implementation("xyz.selenus:artemis-token2022:2.2.0")
    implementation("xyz.selenus:artemis-jupiter:2.2.0")
    implementation("xyz.selenus:artemis-actions:2.2.0")
    implementation("xyz.selenus:artemis-anchor:2.2.0")
}
```

## License

Apache License 2.0. See [../LICENSE](../LICENSE).
