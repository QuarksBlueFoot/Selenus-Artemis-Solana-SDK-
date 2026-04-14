# Migration Guide: Solana Mobile Stack → Artemis

## Mobile Wallet Adapter (MWA)

### Before (Solana Mobile SDK)
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
implementation("com.selenus.artemis:artemis-wallet-mwa-android:2.x.x")

// One-line setup
val artemis = ArtemisMobile.create(
    activity     = this,
    identityUri  = Uri.parse("https://myapp.example.com"),
    iconPath     = "https://myapp.example.com/favicon.ico",  // must be absolute HTTPS URI
    identityName = "MyApp",
    rpcUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    wsUrl        = "wss://atlas-mainnet.helius-rpc.com/?api-key=YOUR_KEY",
    dasUrl       = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"  // null = no DAS support
)

// Connect
artemis.wallet.connect()

// Send SOL (signs + sends + confirms automatically)
val result = artemis.session.sendSol(recipient, 1_000_000_000L)
```

---

## RPC Client

### Before (sol4k / solana-kt)
```kotlin
val connection = Connection(RpcUrl.MAINNET)
val blockhash = connection.getLatestBlockhash()
val signature = connection.sendTransaction(signedTx)
```

### After (Artemis)
```kotlin
val rpc = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))
val blockhash = rpc.getLatestBlockhash()
val signature = rpc.sendTransaction(signedTxBase64)
```

---

## Transaction Pipeline

### Before (manual)
```kotlin
val blockhash = connection.getLatestBlockhash()
tx.setRecentBlockhash(blockhash)
val signed = wallet.signTransaction(tx)
val sig = connection.sendTransaction(signed)
// manual retry, manual confirmation polling...
```

### After (Artemis TxEngine)
```kotlin
val engine = TxEngine(rpc)
val result = engine.execute(
    instructions = listOf(transferIx),
    signer = keypair,
    config = TxConfig(retries = 3, awaitConfirmation = true)
)
// Blockhash, signing, sending, confirmation, retries — all handled.
```

---

## Seed Vault

### Before (Solana Mobile Seed Vault SDK)
```kotlin
implementation("com.solanamobile:seedvault:0.4.0")

Wallet.authorizeSeed(activity, requestCode, purpose)
Wallet.signTransaction(activity, requestCode, authToken, signingRequests)
```

### After (Artemis)
```kotlin
implementation("com.selenus.artemis:artemis-seed-vault:2.x.x")

val manager = SeedVaultManager(context)
manager.connect()
val auth = manager.resolveAuthorization(tokenResult)
val keyStore = SeedVaultKeyStore(manager, auth.authToken)
val signature = keyStore.signTransaction(txBytes)
```

---

## SPL Token Operations

### Before (manual instruction building)
```kotlin
val transferIx = TokenInstruction.transfer(source, dest, owner, amount)
val createAtaIx = AssociatedTokenProgram.createAssociatedTokenAccount(payer, owner, mint)
// manually compose, sign, send...
```

### After (Artemis Presets)
```kotlin
val ixs = Artemis.transferToken(from, to, mint, amount)  // handles ATA creation
val result = session.sendBatch(ixs)
```

---

---

## Real-time account subscriptions

`RealtimeEngine` (in `artemis-ws`) wraps the raw WebSocket layer and delivers typed callbacks.

```kotlin
artemis.realtime.connect()

// Account changes
val handle = artemis.realtime.subscribeAccount(
    pubkey = wallet.publicKey.toBase58(),
    commitment = "confirmed"
) { info ->
    println("lamports: ${info.lamports}, slot: ${info.slot}")
}

// Signature confirmation
artemis.realtime.subscribeSignature(txSignature) { confirmed ->
    println("confirmed: $confirmed")
}

// Unsubscribe
handle.close()
artemis.realtime.close()
```

---

## NFT queries via DAS

`HeliusDas` implements the `ArtemisDas` interface against the Helius DAS API. Pass `dasUrl` to `ArtemisMobile.create()` to enable it.

```kotlin
// All NFTs owned by a wallet
val nfts: List<DigitalAsset> = artemis.das?.assetsByOwner(walletPubkey) ?: emptyList()

// Single asset
val asset: DigitalAsset? = artemis.das?.asset("GdR7...")

// Collection
val collection = artemis.das?.assetsByCollection("CollMintAddress...")
```

---

## Transfer a compressed NFT

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

---

## Module Mapping

| Solana Mobile SDK | Artemis Module |
|---|---|
| `mobile-wallet-adapter-clientlib-ktx` | `artemis-wallet-mwa-android` |
| `seedvault` | `artemis-seed-vault` |
| sol4k `Connection` | `artemis-rpc` (`RpcApi`) |
| Manual tx building | `artemis-vtx` (`TxEngine`) |
| No equivalent | `artemis-tx-presets` (one-line operations) |
| No equivalent | `artemis-wallet` (unified `WalletSession`) |
| No equivalent | `artemis-cnft` (`ArtemisDas`, `HeliusDas`, `MarketplaceEngine`) |
