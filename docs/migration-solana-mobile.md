# Migration guide: Solana Mobile Stack to Artemis

API-level migration notes for apps moving off the official Solana Mobile Kotlin SDKs (`mobile-wallet-adapter-clientlib-ktx`, `seedvault`, sol4k, solana-kt) to Artemis. For the dependency mapping table and end-to-end migration walkthrough, see [REPLACE_SOLANA_MOBILE_STACK.md](REPLACE_SOLANA_MOBILE_STACK.md).

## Mobile Wallet Adapter

### Before (Solana Mobile clientlib)

```kotlin
// Gradle
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")

// Connect
val result = transact(sender) { authResult ->
    val publicKey = authResult.accounts.first().publicKey
}

// Sign
transact(sender) { authResult ->
    signTransactions(arrayOf(serializedTx))
}
```

### After (Artemis)

```kotlin
// Gradle
implementation("xyz.selenus:artemis-wallet-mwa-android:2.3.0")

// One-line setup
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico", // absolute HTTPS URI
    identityName = "MyApp",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

// Connect once. The session manager caches the auth token and reconnects as needed.
artemis.wallet.connect()

// Send SOL. Signs, sends, and confirms automatically.
val result = artemis.session.sendSol(recipient, 1_000_000_000L)
```

The `ArtemisMobile.create()` source is at [mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt](../mobile/artemis-wallet-mwa-android/src/main/kotlin/com/selenus/artemis/wallet/mwa/ArtemisMobile.kt).

## RPC client

### Before (sol4k or solana-kt)

```kotlin
val connection = Connection(RpcUrl.MAINNET)
val blockhash  = connection.getLatestBlockhash()
val signature  = connection.sendTransaction(signedTx)
```

### After (Artemis)

```kotlin
val rpc       = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))
val blockhash = rpc.getLatestBlockhash()
val signature = rpc.sendTransaction(signedTxBase64)
```

`RpcApi` exposes 80+ methods, typed result wrappers, a batch DSL via `BatchRequestBuilder`, and a `BlockhashCache`. Source: [foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcApi.kt](../foundation/artemis-rpc/src/commonMain/kotlin/com/selenus/artemis/rpc/RpcApi.kt).

## Transaction pipeline

### Before (manual)

```kotlin
val blockhash = connection.getLatestBlockhash()
tx.setRecentBlockhash(blockhash)
val signed = wallet.signTransaction(tx)
val sig    = connection.sendTransaction(signed)
// manual retry, manual confirmation polling, manual blockhash refresh
```

### After (Artemis TxEngine)

```kotlin
val engine = TxEngine(rpc)
val result = engine.execute(
    instructions = listOf(transferIx),
    signer       = keypair,
    config       = TxConfig(retries = 3, awaitConfirmation = true)
)
// Blockhash, signing, sending, confirmation, and retry: all handled.
```

The pipeline runs `prepare -> simulate -> sign -> send -> confirm`, refreshes blockhash on retry, and emits `ArtemisEvent.Tx.Sent / Confirmed / Failed / Retrying` to the framework event bus on the way through. Source: [foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt](../foundation/artemis-vtx/src/commonMain/kotlin/com/selenus/artemis/vtx/TxEngine.kt).

## Seed Vault

### Before (Solana Mobile Seed Vault SDK)

```kotlin
implementation("com.solanamobile:seedvault:0.4.0")

Wallet.authorizeSeed(activity, requestCode, purpose)
Wallet.signTransaction(activity, requestCode, authToken, signingRequests)
```

### After (Artemis)

```kotlin
implementation("xyz.selenus:artemis-seed-vault:2.3.0")

val manager = SeedVaultManager(context)
manager.connect()
val auth      = manager.resolveAuthorization(tokenResult)
val keyStore  = SeedVaultKeyStore(manager, auth.authToken)
val signature = keyStore.signTransaction(txBytes)
```

## SPL token operations

### Before (manual instruction building)

```kotlin
val transferIx  = TokenInstruction.transfer(source, dest, owner, amount)
val createAtaIx = AssociatedTokenProgram.createAssociatedTokenAccount(payer, owner, mint)
// manually compose, sign, send
```

### After (Artemis presets)

```kotlin
val ixs    = Artemis.transferToken(from, to, mint, 100_000L, decimals = 6)
val result = session.sendBatch(ixs)
```

The `Artemis.transferToken` preset lives at [compatibility/artemis-tx-presets/src/main/kotlin/com/selenus/artemis/txpresets/Artemis.kt](../compatibility/artemis-tx-presets/src/main/kotlin/com/selenus/artemis/txpresets/Artemis.kt).

For destination ATA handling without the preset, use `AtaEnsurer` from `artemis-cnft`:

```kotlin
val ensurer    = AtaEnsurer(rpc)
val resolution = ensurer.resolve(payer = wallet.publicKey, owner = recipient, mint = usdcMint)

val instructions = buildList {
    resolution.createIx?.let(::add)
    add(tokenTransfer(source, resolution.ata, amount))
}
```

Source: [ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/AtaEnsurer.kt).

## Real-time account subscriptions

`RealtimeEngine` (in `artemis-ws`) wraps the websocket layer and delivers typed callbacks. Subscriptions survive reconnects.

```kotlin
artemis.realtime.connect()

val handle = artemis.realtime.subscribeAccount(
    pubkey     = wallet.publicKey.toBase58(),
    commitment = "confirmed"
) { info ->
    println("lamports: ${info.lamports}, slot: ${info.slot}")
}

artemis.realtime.subscribeSignature(txSignature) { confirmed ->
    println("confirmed: $confirmed")
}

handle.close()
artemis.realtime.close()
```

Observe transport state directly:

```kotlin
artemis.realtime.state
    .onEach { state ->
        when (state) {
            is ConnectionState.Connected    -> hideOfflineBanner()
            is ConnectionState.Reconnecting -> showBanner("reconnecting")
            is ConnectionState.Closed       -> showBanner("offline")
            else -> Unit
        }
    }
    .launchIn(scope)
```

`ConnectionState` is at [foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt](../foundation/artemis-ws/src/commonMain/kotlin/com/selenus/artemis/ws/ConnectionState.kt).

## NFT queries via DAS

`HeliusDas` implements `ArtemisDas` against the Helius DAS API. Pass `dasUrl` to `ArtemisMobile.create()` to enable it. For production resilience wrap it in `CompositeDas`, which falls back to `RpcFallbackDas` on Helius failure.

```kotlin
val nfts: List<DigitalAsset> = artemis.das?.assetsByOwner(walletPubkey) ?: emptyList()
val asset: DigitalAsset?     = artemis.das?.asset("GdR7...")
val collection               = artemis.das?.assetsByCollection("CollMintAddress...")
```

Manual setup with fallback:

```kotlin
val das: ArtemisDas = CompositeDas(
    primary  = HeliusDas(rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$KEY"),
    fallback = RpcFallbackDas(rpc)
)
```

Sources: [HeliusDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/HeliusDas.kt), [RpcFallbackDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/RpcFallbackDas.kt), [CompositeDas.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/das/CompositeDas.kt).

## Compressed NFT transfer

```kotlin
val result = artemis.marketplace.transferCnft(
    wallet     = artemis.wallet,
    dasClient  = myDasClient,
    assetId    = "GdR7...",
    merkleTree = Pubkey.fromBase58("tree..."),
    newOwner   = recipientPubkey
)
println("signature: ${result.signature}, confirmed: ${result.confirmed}")
```

Preflight runs by default and validates ownership, frozen state, DAS record, and (for standard NFTs) destination ATA presence. Source: [MarketplaceEngine.kt](../ecosystem/artemis-cnft/src/commonMain/kotlin/com/selenus/artemis/cnft/MarketplaceEngine.kt).

## Module mapping

| Solana Mobile SDK | Artemis module |
| --- | --- |
| `mobile-wallet-adapter-clientlib-ktx` | `artemis-wallet-mwa-android` |
| `seedvault` | `artemis-seed-vault` |
| sol4k `Connection` | `artemis-rpc` (`RpcApi`) |
| Manual tx building | `artemis-vtx` (`TxEngine`) |
| No equivalent | `artemis-tx-presets` (`Artemis.transferToken`, etc.) |
| No equivalent | `artemis-wallet` (unified `WalletSession`) |
| No equivalent | `artemis-cnft` (`ArtemisDas`, `HeliusDas`, `RpcFallbackDas`, `CompositeDas`, `MarketplaceEngine`, `AtaEnsurer`) |
| No equivalent | `artemis-core` (`ArtemisEvent`, `ArtemisEventBus`) |
