# Android: Mobile Wallet Adapter wiring

This guide shows how to connect Artemis to MWA wallets on Solana Mobile devices.

## 1. Add dependency

Add the Artemis MWA adapter module to your app:

```kotlin
dependencies {
  implementation(project(":artemis-wallet-mwa-android"))
}
```

No extra wallet adapter client dependency is required. Artemis includes a native, zero-dependency MWA 2.x protocol implementation (no OkHttp, no heavy libraries).

## 2. Create the adapter

```kotlin
val adapter = MwaWalletAdapter(
  activity = this,
  identityUri = Uri.parse("https://yourdapp.com"),
  iconPath = "favicon.ico",
  identityName = "Your dApp",
  authStore = /* DataStore or SharedPreferences backed */
)
```

## 3. Connect

```kotlin
val pubkey = adapter.connect()
```

Persisting authToken is recommended so users do not have to approve every time.

## 4. Sign and send

Use Artemis SendPipeline. If your target wallets support `sign_transactions`, Artemis can request signed transaction bytes and broadcast via your own RPC pipeline. If you prefer wallet-broadcast, use `signAndSendTransactions`.

```kotlin
val sig = sendPipeline.sendSingle(
  wallet = adapter,
  compile = { compiledBytes },
  sendSigned = { signedTxBytes -> rpc.sendRawTransaction(signedTxBytes) }
)
```

## 5. Session Management

Artemis supports full session lifecycle management:

```kotlin
// Re-verify the current session (e.g. on app resume)
adapter.reauthorize()

// Disconnect and clear session
adapter.deauthorize()
```

## 6. Off-chain Signing

Sign arbitrary messages (authentication challenges) using `signOffChainMessages`. This maps to the MWA `sign_messages` method.

```kotlin
val message = "Sign this to authenticate".toByteArray(Charsets.UTF_8)
val signatures = adapter.signOffChainMessages(listOf(message))
```
