# Replacing Solana Mobile Stack with Artemis

Step-by-step migration from the standard Solana Mobile Kotlin dependencies to Artemis equivalents.

## Dependency mapping

| Old dependency | Artemis replacement | Notes |
|----------------|-------------------|-------|
| `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` | `xyz.selenus:artemis-wallet-mwa-android` | Full MWA 2.0 client with session management |
| `com.solanamobile:seedvault-wallet-sdk` | `xyz.selenus:artemis-seed-vault` | Seed Vault integration for Saga |
| `com.solanamobile:rpc-core` (or solana-kmp for RPC) | `xyz.selenus:artemis-rpc` | JSON-RPC with failover, batching, circuit breaker |
| solana-kmp `PublicKey`, `Transaction` | `xyz.selenus:artemis-core`, `xyz.selenus:artemis-tx` | Core types and transaction model |
| solana-kmp `VersionedTransaction` | `xyz.selenus:artemis-vtx` | Versioned transaction (v0) with address lookup tables |
| sol4k primitives | `xyz.selenus:artemis-core` | PublicKey, Keypair, Base58, Ed25519 |

## Gradle migration

### Before

```kotlin
dependencies {
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")
    implementation("com.solanamobile:seedvault-wallet-sdk:0.2.6")
    implementation("com.metaplex:solana:0.3.0") // solana-kmp
    // or
    implementation("org.sol4k:sol4k:0.5.4")
}
```

### After

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
    implementation("xyz.selenus:artemis-seed-vault:2.1.1") // if using Saga
}
```

## Concept mapping

### Wallet connection

**Before (MWA clientlib)**
```kotlin
val sender = ActivityResultSender(activity)

transact(sender) { authResult ->
    val publicKey = authResult.publicKey
    // use authResult inside this block
}
```

**After (Artemis)**
```kotlin
val adapter = MwaWalletAdapter(
    activity = this,
    identityUri = Uri.parse("https://myapp.example.com"),
    iconPath = "favicon.ico",
    identityName = "My App"
)
adapter.connect()

val wallet = WalletSession.fromAdapter(adapter, txEngine)
// wallet is reusable across the session
```

### Signing and sending

**Before (MWA clientlib)**
```kotlin
transact(sender) { authResult ->
    val signedTx = signTransactions(listOf(serializedTx))
    // manually submit via RPC
}
```

**After (Artemis)**
```kotlin
// Single instruction
val result = wallet.send(
    SystemProgram.transfer(wallet.publicKey, recipient, lamports)
)

// Or send SOL directly
val result = wallet.sendSol(to = recipient, lamports = 1_000_000_000L)
```

### RPC access

**Before (solana-kmp or sol4k)**
```kotlin
// solana-kmp
val connection = Connection(URL("https://api.mainnet-beta.solana.com"))
val balance = connection.getBalance(publicKey)

// sol4k
val connection = Connection(RpcUrl.MAINNET)
val balance = connection.getBalance(publicKey)
```

**After (Artemis)**
```kotlin
val client = ArtemisClient {
    rpc = "https://api.mainnet-beta.solana.com"
    commitment = Commitment.CONFIRMED
}
val rpc = client.rpc()
val balance = rpc.getBalance(publicKey)
```

### Transaction building

**Before (solana-kmp)**
```kotlin
val tx = Transaction()
tx.add(transferInstruction)
tx.setRecentBlockHash(blockhash)
tx.sign(listOf(keypair))
val serialized = tx.serialize()
```

**After (Artemis)**
```kotlin
val tx = artemisTransaction {
    feePayer(wallet.publicKey)
    instruction(SystemProgram.transfer(from, to, lamports))
}

// Or use the engine pipeline (handles blockhash, signing, retry)
val result = engine.sendTransaction(ix) {
    simulate = true
    retryCount = 3
}
```

## What is complete

| Capability | Status |
|------------|--------|
| MWA 2.0 client (authorize, sign, sign_and_send) | Complete |
| MWA session lifecycle (reauthorize, deauthorize) | Complete |
| Seed Vault signing | Complete |
| PublicKey / Keypair / Base58 | Complete |
| Ed25519 signing (local keypair) | Complete |
| Legacy transaction construction | Complete |
| Versioned transaction (v0) | Complete |
| JSON-RPC client with retry + failover | Complete |
| WebSocket subscriptions | Complete |
| System / Token / ATA / Compute Budget programs | Complete |

## What differs

| Area | Old stack | Artemis |
|------|-----------|---------|
| Wallet auth | Per-call `transact {}` block | Persistent `WalletSession` with stored auth |
| Transaction pipeline | Manual build/sign/send | `TxEngine` pipeline with simulation, retry, priority fees |
| RPC | Basic HTTP | Endpoint pool, circuit breaker, batch DSL, blockhash cache |
| MWA constructor | `ActivityResultSender` | `MwaWalletAdapter(activity, identityUri, iconPath, identityName)` |
| Android lifecycle | Manual `whenResumed {}` handling | Session-based (lifecycle integration is partial) |

## Migration steps

1. **Replace dependencies** in `build.gradle.kts` per the table above
2. **Replace imports**: `com.solana.*` / `org.sol4k.*` become `com.selenus.artemis.*`
3. **Replace `transact {}` blocks** with `MwaWalletAdapter` + `WalletSession`
4. **Replace manual RPC calls** with `ArtemisClient` + `client.rpc()`
5. **Replace manual tx build/sign/send** with `wallet.send(ix)` or `TxEngine`
6. **Test thoroughly** - API semantics are similar but not identical
