# Replacement matrix

This is the dependency-by-dependency source of truth for migration claims. `Verified` means there is committed code plus a test, API snapshot, or build gate. `Partial` means the library is usable for the listed scope, but not a full upstream replacement.

| Dependency | Current upstream baseline | Artemis module(s) | Replacement type | Status | Notes |
|---|---|---|---|---|---|
| Sol4k | 0.7.0 | `artemis-sol4k-compat`, Foundation modules | Source/API compat plus native replacement | Verified | Covers Connection, PublicKey, Keypair, transactions, token helpers, constants, RPC models, and exception shapes. |
| SolanaKT / solana-kmp | pinned solana-kmp source surface | `artemis-solana-kmp-compat`, Foundation modules | Source/API compat plus native replacement | Verified | Covers PublicKey, Base58, Amount, clusters, RPC config, transaction/message types, System/Memo programs, Keypair, EDDSA, and DAS read interfaces. |
| MWA clientlib | 2.1.0 | `artemis-mwa-clientlib-compat`, `artemis-mwa-compat`, `artemis-mwa-common-compat` | Source compat backed by Artemis MWA | Verified for API/protocol unit coverage; device wallet matrix partial | High-level `transact(sender, signInPayload, block)` shape retained. Phantom RESULT_CANCELED remains a wallet-side limitation of high-level activity-result flows. |
| MWA clientlib-ktx | 2.1.0 | `artemis-mwa-compat` | Source compat | Verified for API/protocol unit coverage | Re-exports common/clientlib compat types transitively. |
| MWA walletlib | 2.1.0 | `artemis-mwa-walletlib-compat`, `artemis-wallet-mwa-walletlib-android` | Source compat backed by wallet-side Artemis MWA | Verified for local scenarios; device wallet matrix partial | Includes AssociationUri, LocalAssociationScenario, JsonRpc20Server, server exceptions, auth repository shape, and wallet icon helpers. |
| MWA common | 2.1.0 | `artemis-mwa-common-compat` | Source compat | Verified | Protocol and SIWS helpers exposed under upstream package names. |
| Seed Vault SDK | platform SDK | `artemis-seed-vault`, `artemis-seedvault-compat` | Compat wrapper over platform service | Partial | Static/API surface and provider trust checks are covered; hardware-backed signing is device-gated. |
| `rpc-core` | pinned upstream snapshot | `artemis-rpc-core-compat`, `artemis-rpc` | Source compat plus native transport | Verified | JSON-RPC envelopes, drivers, request serialization, response/error decoding, and client models covered. |
| `web3-solana` | main@2025-08 pinned snapshot | `artemis-web3-solana-compat`, `artemis-tx`, `artemis-vtx`, `artemis-programs` | Source compat plus native transaction/program builders | Verified for pinned snapshot | Refresh when upstream publishes a newer stable artifact/API. |
| `io.github.funkatronics:multimult` | 0.2.6 | `artemis-multimult-compat` | Source compat for Base58 APIs | Verified | Provides `com.funkatronics.encoders.Base58`, Encoder/Decoder, upstream error shapes, and group-style alias `io.github.funkatronics.multimult.Base58`. |
| SPL Token | Solana program | `artemis-programs` | Native replacement | Verified | Includes core instruction builders. |
| Token-2022 | Solana program | `artemis-token2022`, `artemis-web3-solana-compat` | Native replacement | Verified for shipped extensions; confidential transfer experimental | Transfer hook and zero-copy TLV/account extension views are covered. |
| Metaplex Android | selected Android SDK surface | `artemis-metaplex-android-compat`, `artemis-metaplex`, `artemis-candy-machine`, `artemis-cnft` | Selected source compat and native coverage | Partial | NFT/DAS and Candy Machine mint_v2 bridge are covered; unsupported Auction House and Candy Machine query placeholders return typed `UnsupportedMetaplexFeature` sentinels. Do not claim full Metaplex replacement. |
| Jupiter | v6 route/swap APIs | `artemis-jupiter` | Native integration | Partial | Quote/swap surface exists; broader live-route conformance remains gated. |
| DAS / cNFT | DAS APIs and Bubblegum flows | `artemis-cnft` | Native integration | Verified for shipped flows | Helius/RPC fallback and compressed NFT transfer/proof flows are covered. |

## Install with BOM

```kotlin
dependencies {
    implementation(platform("xyz.selenus:artemis-bom:2.3.2"))
    implementation("xyz.selenus:artemis-core")
    implementation("xyz.selenus:artemis-rpc")
    implementation("xyz.selenus:artemis-wallet-mwa-android")
    implementation("xyz.selenus:artemis-mwa-compat")
    implementation("xyz.selenus:artemis-sol4k-compat")
    implementation("xyz.selenus:artemis-multimult-compat")
}
```