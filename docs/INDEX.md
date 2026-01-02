# Artemis SDK - Documentation Index

## Mobile-first Kotlin Solana SDK

Master index for all Artemis documentation.

---

## Core Documentation

| Document | Description | Audience |
|----------|-------------|----------|
| [README.md](../README.md) | Project overview and quick start | All developers |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | Ring structure, dependencies, module hierarchy | All developers |
| [MODULE_MAP.md](MODULE_MAP.md) | Every module and when to use it | All developers |
| [PARITY_MATRIX.md](PARITY_MATRIX.md) | Feature comparison vs other Kotlin SDKs | Evaluators |
| [DEPENDENCY_RULES.md](DEPENDENCY_RULES.md) | What can depend on what | Contributors |
| [ADOPTION_BUNDLES.md](ADOPTION_BUNDLES.md) | Recommended dependency sets by use case | All developers |
| [REPLACE_SOLANA_MOBILE_STACK.md](REPLACE_SOLANA_MOBILE_STACK.md) | Migrating from Solana Mobile Stack to Artemis | Mobile developers |
| [CHANGELOG.md](../CHANGELOG.md) | Version history | All developers |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Contribution guidelines | Contributors |

## Technical References

| Document | Description |
|----------|-------------|
| [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) | Internal architecture of ecosystem and advanced modules |
| [MOBILE_APP_GUIDE.md](MOBILE_APP_GUIDE.md) | Complete mobile integration walkthrough |

## Feature Guides

| Guide | Module |
|-------|--------|
| [ANCHOR_GUIDE.md](guides/ANCHOR_GUIDE.md) | artemis-anchor |
| [JUPITER_GUIDE.md](guides/JUPITER_GUIDE.md) | artemis-jupiter |
| [ACTIONS_GUIDE.md](guides/ACTIONS_GUIDE.md) | artemis-actions |
| [UNIVERSAL_GUIDE.md](guides/UNIVERSAL_GUIDE.md) | artemis-universal |
| [NLP_GUIDE.md](guides/NLP_GUIDE.md) | artemis-nlp |
| [STREAMING_GUIDE.md](guides/STREAMING_GUIDE.md) | artemis-streaming |

## Setup

| Document | Description |
|----------|-------------|
| [GPG_SETUP.md](setup/GPG_SETUP.md) | GPG key setup for signing |

---

## Quick Setup

```kotlin
dependencies {
    // Foundation
    implementation("xyz.selenus:artemis-core:2.1.1")
    implementation("xyz.selenus:artemis-rpc:2.1.1")
    implementation("xyz.selenus:artemis-tx:2.1.1")
    implementation("xyz.selenus:artemis-programs:2.1.1")

    // Mobile
    implementation("xyz.selenus:artemis-wallet:2.1.1")
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.1.1")

    // Ecosystem (add as needed)
    implementation("xyz.selenus:artemis-anchor:2.1.1")
    implementation("xyz.selenus:artemis-jupiter:2.1.1")
    implementation("xyz.selenus:artemis-actions:2.1.1")
}
```

---

## 🌐 Resources

- **Maven Repository:** `https://repo.maven.apache.org/maven2/xyz/selenus/`
- **NPM Package:** `@selenus/artemis-solana-sdk`
- **GitHub:** `https://github.com/selenus/artemis-solana-sdk`
- **Documentation:** `https://docs.selenus.xyz`

---

## 📞 Support

- **Issues:** GitHub Issues
- **Discord:** Coming soon
- **Email:** support@selenus.xyz

---

## 📄 License

Apache License 2.0 - See [LICENSE](../LICENSE)

---

*Artemis SDK v2.0.0 - The most advanced Solana SDK for mobile development*

*Built with ❤️ by Selenus Technologies*
