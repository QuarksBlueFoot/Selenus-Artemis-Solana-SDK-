# Artemis Mobile Wallet Adapter (MWA)

`artemis-wallet-mwa-android` is a from-scratch dapp-side implementation of the [Solana Mobile Wallet Adapter 2.0 protocol](https://github.com/solana-mobile/mobile-wallet-adapter) in pure Kotlin. No OkHttp, no upstream clientlib transitive deps. It speaks the spec end-to-end: association URI, P-256 ECDH handshake, HKDF-SHA256 key derivation, AES-128-GCM session cipher, JSON-RPC over a local WebSocket, full MWA 2.0 method set.

## What you actually get

- `MwaWalletAdapter` connects, authorizes, signs, and sends through a real wallet on the same device.
- `ArtemisMobile.create(...)` is the one-call entry point that bundles this with RPC, transaction engine, realtime subscriptions, DAS, and session management.
- `AuthTokenStore.default(context)` returns a Keystore-encrypted store (AES-256-GCM, non-exportable key). Plaintext DataStore is still available via `DataStoreAuthTokenStore` for non-production builds.
- Wire-format components verified against RFC vectors in unit tests: HKDF-SHA256 ([HkdfVectorsTest](../../../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/protocol/HkdfVectorsTest.kt)), AES-128-GCM round-trip + layout + tamper rejection ([Aes128GcmTest](../../../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/protocol/Aes128GcmTest.kt)), EcP256 ECDH + ECDSA P1363 ([EcP256Test](../../../mobile/artemis-wallet-mwa-android/src/test/kotlin/com/selenus/artemis/wallet/mwa/protocol/EcP256Test.kt)).

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.2.0")
    implementation("xyz.selenus:artemis-wallet:2.2.0")
    implementation("xyz.selenus:artemis-vtx:2.2.0")
    implementation("xyz.selenus:artemis-rpc:2.2.0")
}
```

The published version is `2.2.0`. The source of truth is `version` in [gradle.properties](../../../gradle.properties).

## One-call setup with ArtemisMobile

```kotlin
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico",
    identityName = "My App",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

// Lazy connect + session reuse. Handles reauthorize and session expiry.
val sig = artemis.sessionManager.withWallet { session ->
    session.sendSol(recipient, 1_000_000_000L)
}
```

`ArtemisMobile.create(...)` lives at [ArtemisMobile.kt:85](../../../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt#L85).

## Direct adapter use

If you want to wire everything yourself:

```kotlin
val adapter = MwaWalletAdapter(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico",
    identityName = "My App",
    chain        = "solana:mainnet",
    authStore    = AuthTokenStore.default(applicationContext)
)

val publicKey = adapter.connect()
val signed: List<ByteArray> = adapter.signMessages(
    listOf(txBytes),
    SignTxRequest(purpose = "signTransactions")
)
adapter.disconnect()
```

The class lives at [MwaWalletAdapter.kt](../../../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/MwaWalletAdapter.kt).

## Drop-in replacement for upstream clientlib-ktx

If your dapp already imports `com.solana.mobilewalletadapter.clientlib.*`, the [artemis-mwa-compat](../../../interop/artemis-mwa-compat/) + [artemis-mwa-clientlib-compat](../../../interop/artemis-mwa-clientlib-compat/) + [artemis-mwa-common-compat](../../../interop/artemis-mwa-common-compat/) shims re-expose the upstream types at the same fully qualified names, backed by Artemis internals. `MobileWalletAdapter(connectionIdentity)`, `transact { ... }`, `AdapterOperations`, `LocalAssociationScenario`, `AuthorizationResult`, `SignInResult`, `SignPayloadsResult`, `SignMessagesResult`, and `SignAndSendTransactionsResult` all route through the live Artemis session via `MwaSessionBridge`. See the compat module sources for the mapping details.

## License

Apache License 2.0.
