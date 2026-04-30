# Contributing to Artemis

Thanks for considering a contribution. Artemis is a Kotlin Multiplatform Solana SDK with a strict ring architecture and an explicit reliability-first design. The rules below keep the surface stable for the apps that ship on top of it.

## Repo layout

```text
foundation/    Ring 1   Core primitives, RPC, websockets, transactions, programs
mobile/        Ring 2   Wallet abstraction, MWA Android client, Seed Vault
ecosystem/     Ring 3   Token-2022, Metaplex, MPL Core, cNFT, Candy Machine, Jupiter, Anchor, Actions, Solana Pay
advanced/      Ring 4   Opt-in helpers and labs modules
compatibility/ Ring 5   Source-compatible shims for migration off other SDKs
interop/                Solana Mobile client-library compatibility shims
testing/                Integration and devnet test modules
samples/                Sample apps (excluded from default build)
docs/                   All documentation
```

The ring map and dependency rules are documented at [docs/ARCHITECTURE_OVERVIEW.md](docs/ARCHITECTURE_OVERVIEW.md) and [docs/DEPENDENCY_RULES.md](docs/DEPENDENCY_RULES.md). The build enforces them via `./gradlew checkDependencyRings`.

## Build prerequisites

- JDK 17 (the KMP toolchain is pinned to 17 in the Foundation modules)
- Gradle wrapper bundled with the repo (`./gradlew` / `gradlew.bat`)
- Android SDK if you want to build the Android-specific modules. Set `ANDROID_HOME` or add `sdk.dir` to `local.properties`. Pure JVM modules build without it.

Copy `local.properties.example` to `local.properties` and point it at your Android SDK if you have one.

## Build

A full build:

```bash
./gradlew --no-daemon clean build
```

A targeted JVM-only build for a single module:

```bash
./gradlew :artemis-vtx:compileKotlinJvm
./gradlew :artemis-vtx:jvmTest
```

A full test run:

```bash
./gradlew test
```

## Sample apps

Sample Android apps are intentionally excluded from the default build so the foundation and ecosystem rings build without an Android SDK. Enable them explicitly:

```bash
./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
```

## Devnet integration tests

[run-devnet-tests.sh](run-devnet-tests.sh) provisions a devnet keypair (or reuses one at `~/.config/solana/id.json`) and runs the integration test module at [testing/artemis-devnet-tests/](testing/artemis-devnet-tests/) against real devnet RPC. The Solana CLI is optional: you can supply an existing keypair via the `DEVNET_KEYPAIR_PATH` environment variable.

## The rules

These exist because real apps depend on Artemis and small surface drifts cost them work. Please follow them.

1. **No paid-service assumptions.** Every required code path must work with a vanilla Solana RPC endpoint. Helius, QuickNode, Triton, and other DAS-providing endpoints are supported and recommended where available, but never required by Foundation, Mobile, or Ecosystem modules. The `RpcFallbackDas` and `CompositeDas` design at [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/](ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/) is the reference pattern.
2. **Modules stay drop-in and optional.** A Foundation module never depends on a Mobile or Ecosystem module. An Ecosystem module never depends on Advanced. Adding a new dependency to a module requires checking that you are not creating a cycle. The build will catch obvious cycles, but check the ring map first.
3. **Public APIs are documented and stable.** Every public class, function, and field in `commonMain` source sets needs a KDoc that explains what it does, when to use it, and a short usage example if it is non-trivial. New parameters on existing functions ship with default values so existing call sites compile unchanged.
4. **Reliability beats features.** A small feature with a retry path, a state machine, and a fallback is more useful than a big feature without them. The pattern reference is [WalletSessionManager](mobile/artemis-wallet/src/commonMain/kotlin/com/selenus/artemis/wallet/WalletSessionManager.kt), [BlockhashCache](foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/BlockhashCache.kt), [RetryPipeline](foundation/artemis-tx/src/commonMain/kotlin/com/selenus/artemis/tx/RetryPipeline.kt), and the websocket reconnect loop in [SolanaWsClient](foundation/artemis-ws/src/jvmMain/kotlin/com/selenus/artemis/ws/SolanaWsClient.kt).
5. **Tests are required for new logic.** A new public class needs a unit test that exercises its happy path and at least one error path. Multiplatform tests live in `commonTest`; JVM-only tests live in `jvmTest`. The reference test layout is at [foundation/artemis-ws/src/jvmTest/kotlin/com/selenus/artemis/ws/](foundation/artemis-ws/src/jvmTest/kotlin/com/selenus/artemis/ws/).
6. **No mocking the database, the RPC, or the wallet.** Tests that reach the network or hit a wallet adapter must use the real surface (devnet, a real adapter, the in-repo MwaWebSocketServer mock from the protocol package) rather than ad-hoc mocks.

## Pull request checklist

Before opening a PR:

- [ ] `./gradlew :<module>:compileKotlinJvm` passes for every module you touched.
- [ ] `./gradlew :<module>:jvmTest` passes for every module you touched.
- [ ] New public APIs have KDoc.
- [ ] New public APIs have at least one test.
- [ ] CHANGELOG.md has an `## Unreleased` entry that describes the change with a file-path link to the relevant source.
- [ ] No em-dashes (`U+2014`), no en-dashes (`U+2013`), no decorative emoji in docs.
- [ ] No promotional language that is not backed by code in the same PR.

## Filing issues

Please include:

1. The module you are working with.
2. A minimal Kotlin snippet that reproduces the problem.
3. Solana cluster (mainnet, devnet, custom) and RPC endpoint type (vanilla, Helius, QuickNode, etc.).
4. The exception or unexpected output. Stack traces are gold.
5. The Artemis version you are on (the `version` field in [gradle.properties](gradle.properties)).

## License

By contributing you agree that your contribution is licensed under the Apache License 2.0, the same license that covers the rest of the repository. See [LICENSE](LICENSE).
