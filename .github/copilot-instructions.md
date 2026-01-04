# Selenus Artemis Solana SDK - AI Coding Instructions

You are working on **Selenus Artemis**, a modular, mobile-first Kotlin SDK for Solana.
This project prioritizes reliability, v0 transactions, and modern Solana standards (Token-2022, cNFTs, MPL Core).

## 1. Architecture & Modules

The project is a multi-module Gradle build. Understand the boundaries:

- **`artemis-core`**: Shared domain models, constants, and interfaces.
- **`artemis-rpc`**: Typed JSON-RPC client. Uses `OkHttp` and `kotlinx.serialization`.
- **`artemis-tx` / `artemis-vtx`**: Transaction building. **Always prefer v0 transactions** and Address Lookup Tables (ALT).
- **`artemis-token2022`**: Full Token-2022 support (TLV decoding, extensions).
- **`artemis-cnft`**: Compressed NFTs (Bubblegum/DAS).
- **`artemis-mplcore`**: Metaplex Core (v2).
- **`artemis-wallet-mwa-android`**: Android Wallet Adapter bindings.

**Key Principle:** Modules are optional and drop-in. Do not introduce circular dependencies.

## 2. Coding Conventions

### Kotlin & Serialization
- Use **`kotlinx.serialization`** for all JSON handling.
- Use the `buildJsonObject` DSL for constructing JSON payloads manually.
- **Do not** use Gson or Jackson.

```kotlin
// Example: Constructing a JSON-RPC request
val payload = buildJsonObject {
  put("jsonrpc", "2.0")
  put("method", "getLatestBlockhash")
  put("params", buildJsonArray { ... })
}
```

### Networking & RPC
- The `JsonRpcClient` is designed for **mobile reliability**.
- It includes built-in retry and backoff strategies (`ExponentialJitterBackoff`).
- Do not assume a stable connection. Handle `RpcException`.

### Transactions
- **Default to Versioned Transactions (v0)**. Legacy transactions are discouraged.
- Use `artemis-tx` builders for constructing instructions.

## 3. Build & Test Workflows

### Building
- The project includes Android modules.
- Standard build:
  ```bash
  ./gradlew build
  ```

### Android Sample App
- The sample app is **excluded** from the default build to save time.
- To build the sample:
  ```bash
  ./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
  ```

### Testing
- Unit tests are in `src/test/kotlin`.
- Run tests via `./gradlew test`.

## 4. Project Rules
- **No Paid Assumptions:** The SDK must work with standard RPCs. Do not bake in dependencies on paid APIs (like Helius specific endpoints) unless they are optional add-ons.
- **Mobile-First:** Assume flaky networks.
- **Pure Kotlin:** Avoid Java-isms where possible.

## 5. File Structure
- Source code: `src/main/kotlin/com/selenus/artemis/<module>/`
- Tests: `src/test/kotlin/com/selenus/artemis/<module>/`
