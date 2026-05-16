# Solana Mobile integration

Artemis is the app-side Kotlin SDK layer above Solana Mobile primitives. It consolidates RPC, transactions, realtime, Token-2022, selected NFT/DAS tooling, wallet sessions, MWA client code, and source-compatible migration shims.

## What Artemis replaces

| App-side dependency | Artemis replacement |
|---|---|
| Sol4k | `artemis-sol4k-compat` or native Foundation modules |
| SolanaKT / solana-kmp | `artemis-solana-kmp-compat` or native Foundation modules |
| `rpc-core` | `artemis-rpc-core-compat` or `artemis-rpc` |
| `web3-solana` | `artemis-web3-solana-compat` plus native transaction/program modules |
| `mobile-wallet-adapter-clientlib(-ktx)` | `artemis-mwa-compat`, `artemis-mwa-clientlib-compat`, native MWA modules |
| `mobile-wallet-adapter-walletlib` | `artemis-mwa-walletlib-compat`, `artemis-wallet-mwa-walletlib-android` |
| `multimult` Base58 usage | `artemis-multimult-compat` or `com.selenus.artemis.runtime.Base58` |

## What Artemis does not replace

- Wallet apps and their approval UX.
- Mobile Wallet Adapter as the wallet/dapp protocol.
- Seed Vault secure custody and platform service behavior.
- Android platform lifecycle and permission boundaries.

## Gradle setup

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(platform("xyz.selenus:artemis-bom:2.3.2"))
    implementation("xyz.selenus:artemis-core")
    implementation("xyz.selenus:artemis-rpc")
    implementation("xyz.selenus:artemis-wallet")
    implementation("xyz.selenus:artemis-wallet-mwa-android")
    implementation("xyz.selenus:artemis-seed-vault")
    implementation("xyz.selenus:artemis-token2022")
}
```

For source-compatible migration:

```kotlin
dependencies {
    implementation(platform("xyz.selenus:artemis-bom:2.3.2"))
    implementation("xyz.selenus:artemis-mwa-compat")
    implementation("xyz.selenus:artemis-mwa-walletlib-compat")
    implementation("xyz.selenus:artemis-seedvault-compat")
    implementation("xyz.selenus:artemis-sol4k-compat")
    implementation("xyz.selenus:artemis-rpc-core-compat")
    implementation("xyz.selenus:artemis-web3-solana-compat")
    implementation("xyz.selenus:artemis-multimult-compat")
}
```

## MWA notes

- The compat modules are pinned to MWA `2.1.0` metadata.
- The high-level transact signature remains `transact(sender, signInPayload, block)`.
- `iconUri` should be an absolute HTTPS URI so wallet apps can load it.
- Run high-level wallet flows from a ViewModel coroutine scope and pass `ActivityResultSender` in from the Activity.
- Phantom's known `RESULT_CANCELED` pattern is wallet-side behavior of the high-level activity-result flow. The low-level local-association workaround belongs in app code that deliberately opts into it.

## Seed Vault notes

Seed Vault remains secure custody. Artemis wraps availability checks, auth token parsing, provider trust checks, and transaction signing calls, but full signing behavior must be validated on a device or supported simulator.

## Compatibility table

| Platform | Supported by Artemis modules |
|---|---|
| Android | MWA, Seed Vault, Android library publications, compat shims |
| JVM | Foundation, RPC, transaction, program, compat tests, BOM |
| KMP common | Foundation, RPC models, transactions, programs, many compat shims |

Current release metadata uses Java 17 toolchains. Android modules use the repository `compileSdk` and `minSdk` from the version catalog.