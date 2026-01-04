# Solana Mobile migration: replace clientlib with Artemis (15 minutes)

Goal: SolanaMobile (or any Solana Mobile Android app) can remove the Solana Mobile Kotlin client library dependency and use Artemis' native MWA implementation.

## Step 1: Remove Solana Mobile clientlib dependency

Remove:

```kotlin
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")
```

## Step 2: Add Artemis module

Add:

```kotlin
dependencies {
  implementation(project(":artemis-wallet-mwa-android"))
}
```

## Step 3: Replace MWA client usage with MwaWalletAdapter

Before (clientlib direct usage):
- connect
- authorize
- sign or signAndSend

After (Artemis):

```kotlin
val adapter = MwaWalletAdapter(
  activity = activity,
  identityUri = Uri.parse("https://yourapp.com"),
  iconPath = "favicon.ico",
  identityName = "Your App",
  authStore = DataStoreAuthTokenStore.from(activity)
)

val pubkey = adapter.connect()
```

## Step 4: Signing

Sign transactions (sign-only):

```kotlin
val signed = adapter.signMessages(listOf(txBytes))
```

Sign off-chain messages (authentication):

```kotlin
val signatures = adapter.signOffChainMessages(listOf(messageBytes))
```

Sign and send when available:

```kotlin
val sigs = adapter.signAndSendTransactions(listOf(txBytes))
```

## Step 5: One-call routing (recommended)

Wallet broadcast when supported, otherwise sign and broadcast with your RPC:

```kotlin
val sigs = adapter.signThenSendViaRpc(
  rpcSend = { bytes -> rpc.sendRawTransaction(bytes) },
  transactions = listOf(txBytes)
)
```

## Step 6: Batching

Artemis reads `get_capabilities` and automatically batches requests to match wallet limits. No app-side batching needed.
