# Migration Guide: Solana Mobile SDK → Artemis SDK

This guide helps Solana Mobile developers migrate from the current Solana Mobile stack to Artemis SDK, the most comprehensive Kotlin-first Solana SDK available.

> **Artemis provides complete API parity with `solana-kmp` and `mobile-wallet-adapter-clientlib-ktx`**, with innovative enhancements: coroutine-first design, WebSocket subscriptions, Token-2022, and unified dependency management.

## TL;DR: What Solana Mobile Uses & How to Replace It

**Solana Mobile SDK** (mobile-wallet-adapter, seed-vault-sdk) uses these Kotlin Solana libraries:

| Current Dependency | Package | Artemis Replacement |
|--------------------|---------|---------------------|
| `com.solana.publickey.SolanaPublicKey` | solana-kmp (Funkatronics) | `xyz.selenus:artemis-core` → `Pubkey` |
| `com.solana.rpc.SolanaRpcClient` | solana-kmp | `xyz.selenus:artemis-rpc` → `RpcClient` |
| `com.solana.networking.KtorNetworkDriver` | solana-kmp | Built into `artemis-rpc` |
| `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` | Solana Mobile | `xyz.selenus:artemis-wallet-mwa-android` |
| `com.solanamobile:seedvault-wallet-sdk` | Solana Mobile | `xyz.selenus:artemis-seed-vault` |

### One-Line Summary

Replace **5+ dependencies** with **2 Artemis dependencies** and get more features:

```kotlin
// BEFORE: Typical Solana Mobile app
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.7")
implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
implementation("foundation.metaplex:solana-kmp:0.3.0")  // For SolanaPublicKey, SolanaRpcClient
// Plus OkHttp/Ktor for networking...

// AFTER: Artemis unified SDK
implementation("xyz.selenus:artemis-core:1.4.0")              // Pubkey, Keypair, Base58
implementation("xyz.selenus:artemis-wallet-mwa-android:1.4.0") // MWA + Seed Vault + RPC
```

---

## Why Migrate to Artemis?

| Issue with Current SDKs | Solution in Artemis |
|-------------------------|---------------------|
| Multiple dependencies (MWA + RPC + utilities) | Unified SDK - one import |
| solana-kmp is Metaplex-focused | Full SDK with all Solana features |
| No WebSocket subscriptions | Full WebSocket support via `artemis-ws` |
| Callback-heavy APIs | Coroutine-first design with Flows |
| Missing Token-2022 | Complete Token-2022 extensions |
| No gaming/privacy modules | Gaming, Privacy, DePIN modules |
| React Native requires extra setup | Native React Native module included |

### Innovative, Original Implementation

Artemis is built from the ground up with modern Android development practices:

- ✅ **Coroutine-first architecture** — All APIs use `suspend fun` and `Flow` for reactive state
- ✅ **Mobile-first design** — Optimized for Android lifecycle, memory, and battery
- ✅ **Zero boilerplate** — Type-safe builders, extension functions, sealed result types
- ✅ **Modular architecture** — Include only the modules you need
- ✅ **Complete MWA 2.0 parity** — Full protocol implementation with enhancements

**Innovative enhancements over solana-kmp:**
- 🚀 **Built-in retry with exponential backoff** for RPC calls
- 🚀 **WebSocket subscriptions** (account, slot, signature) via `artemis-ws`
- 🚀 **Transaction simulation** before send with CU estimation
- 🚀 **Batch transaction processing** with configurable concurrency
- 🚀 **Session key management** for gasless/delegated transactions

---

## Quick Start

### Step 1: Update Dependencies

#### Before (Solana Mobile Stack)
```kotlin
// build.gradle.kts - Current Solana Mobile approach
dependencies {
    // MWA client library
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.7")
    
    // Seed Vault (for Saga/Seeker)
    implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
    
    // RPC + Primitives (solana-kmp from Funkatronics/Metaplex)
    // This is what Solana Mobile SDK actually uses!
    implementation("foundation.metaplex:solana-kmp:0.3.0")
}
```

#### After (Artemis - Unified)
```kotlin
// build.gradle.kts - Artemis unified SDK
dependencies {
    // Core primitives (PublicKey, Keypair, Base58)
    implementation("xyz.selenus:artemis-core:1.4.0")
    
    // RPC client (includes networking)
    implementation("xyz.selenus:artemis-rpc:1.4.0")
    
    // Mobile Wallet Adapter (MWA 2.0)
    implementation("xyz.selenus:artemis-wallet-mwa-android:1.4.0")
    
    // Seed Vault integration (optional - for Saga/Seeker)
    implementation("xyz.selenus:artemis-seed-vault:1.4.0")
    
    // WebSocket subscriptions (optional)
    implementation("xyz.selenus:artemis-ws:1.4.0")
}
```

### Step 2: Update Imports (Direct Mappings)

Here's exactly how to update your imports:

```kotlin
// ═══════════════════════════════════════════════════════════════
// PUBLIC KEY
// ═══════════════════════════════════════════════════════════════

// BEFORE (solana-kmp / Metaplex)
import com.solana.publickey.SolanaPublicKey
val pk = SolanaPublicKey.from("base58string")

// AFTER (Artemis)
import com.selenus.artemis.runtime.Pubkey
val pk = Pubkey.fromBase58("base58string")

// OR use type alias for zero code changes:
import com.selenus.artemis.compat.SolanaPublicKey
val pk = SolanaPublicKey.from("base58string")  // Works!

// ═══════════════════════════════════════════════════════════════
// RPC CLIENT
// ═══════════════════════════════════════════════════════════════

// BEFORE (solana-kmp)
import com.solana.rpc.SolanaRpcClient
import com.solana.networking.KtorNetworkDriver
val rpc = SolanaRpcClient("https://api.devnet.solana.com", KtorNetworkDriver())
val balance = rpc.getBalance(pubkey)

// AFTER (Artemis - simpler!)
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.JsonRpcClient
val client = JsonRpcClient("https://api.devnet.solana.com")  // No driver needed
val rpc = RpcApi(client)
val balance = rpc.getBalance(pubkey)

// ═══════════════════════════════════════════════════════════════
// KEYPAIR
// ═══════════════════════════════════════════════════════════════

// BEFORE (solana-kmp)
import com.solana.keypair.SolanaKeypair
val keypair = SolanaKeypair.generate()

// AFTER (Artemis)
import com.selenus.artemis.runtime.Keypair
val keypair = Keypair()  // Generates new

// ═══════════════════════════════════════════════════════════════
// MOBILE WALLET ADAPTER
// ═══════════════════════════════════════════════════════════════

// BEFORE (Solana Mobile)
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
val mwa = MobileWalletAdapter(connectionIdentity)

// AFTER (Artemis)
import com.selenus.artemis.wallet.mwa.MobileWalletAdapter
import com.selenus.artemis.wallet.mwa.ConnectionIdentity
val mwa = MobileWalletAdapter(connectionIdentity)  // Same API!
```

### Step 3: Zero Code Changes

Your existing code works unchanged:

```kotlin
// This code works identically in Artemis!
val account = Account()  // Generates new keypair
val publicKey = account.publicKey
val signature = account.sign(message)
val encoded = publicKey.toBase58()
```

---

## API Comparison

### Keypair Generation

#### solana-kmp
```kotlin
import com.solana.keypair.SolanaKeypair
val keypair = SolanaKeypair.generate()
val secretKey = keypair.secretKey
```

#### Artemis (drop-in compatible)
```kotlin
import com.selenus.artemis.runtime.Keypair

val keypair = Keypair()
val secretKey = keypair.secret
```

### Public Key Operations

#### solana-kmp
```kotlin
import com.solana.publickey.SolanaPublicKey
val pk = SolanaPublicKey.from("base58string")
val bytes = pk.bytes
val base58 = pk.toString()
```

#### Artemis (drop-in compatible)
```kotlin
import com.selenus.artemis.runtime.Pubkey

val pk = Pubkey.fromBase58("base58string")
val bytes = pk.bytes
val base58 = pk.toBase58()
```

### Base58 Encoding

#### solana-kmp
```kotlin
import com.solana.base58.Base58
val encoded = Base58.encode(bytes)
val decoded = Base58.decode(encoded)
```

#### Artemis (drop-in compatible)
```kotlin
import com.selenus.artemis.runtime.Base58

val encoded = Base58.encode(bytes)  // Identical API!
val decoded = Base58.decode(encoded)

// Artemis also provides:
val valid = Base58.isValid(encoded)
val validated = Base58.validateOrNull(encoded)
```

### PDA Derivation

#### solana-kmp
```kotlin
import com.solana.publickey.ProgramDerivedAddress
val pda = ProgramDerivedAddress.find(
    seeds = listOf(seed1, seed2),
    programId = programId
)
```

#### Artemis (drop-in compatible)
```kotlin
import com.selenus.artemis.runtime.Pda

val (pda, bump) = Pda.findProgramAddress(
    seeds = listOf(seed1, seed2),
    programId = programId
)

// Or using the Pubkey extension:
val (pda, bump) = programId.findProgramAddress(seed1, seed2)
```

### RPC Calls

#### solana-kmp
```kotlin
import com.solana.rpc.SolanaRpcClient
import com.solana.networking.KtorNetworkDriver

val rpc = SolanaRpcClient("https://api.mainnet-beta.solana.com", KtorNetworkDriver())
val balance = rpc.getBalance(publicKey)
```

#### Artemis (simpler, more features)
```kotlin
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.rpc.JsonRpcClient

val client = JsonRpcClient("https://api.mainnet-beta.solana.com")
val rpc = RpcApi(client)

// Suspend function - clean and simple!
val balance = rpc.getBalance(publicKey)

// Artemis also provides typed account responses:
val accountInfo = rpc.getAccountInfoParsed(publicKey)
println("Owner: ${accountInfo?.owner}")
println("Lamports: ${accountInfo?.lamports}")
```

---

## Mobile Wallet Adapter Migration

### Current Solana Mobile Stack

```kotlin
// Multiple dependencies needed
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.7")
implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
implementation("foundation.metaplex:solana-kmp:0.3.0")
```

### Artemis (unified)

```kotlin
// Single dependency for everything!
implementation("xyz.selenus:artemis-wallet-mwa-android:1.4.0")
```

### MWA 2.0 Features (Artemis-exclusive for Kotlin)

```kotlin
import com.selenus.artemis.wallet.mwa.*

// P-256 ECDH session establishment (MWA 2.0)
val session = MwaSession.establish(context)

// Sign and send in one call
val signatures = session.signAndSendTransactions(transactions)

// Sign In With Solana (SIWS)
val signInResult = session.signIn(SignInPayload(
    domain = "myapp.com",
    statement = "Sign in to MyApp"
))
```

---

## Seed Vault Migration

### Solana Mobile Seed Vault SDK

```kotlin
implementation("com.solanamobile:seedvault:1.0.0")
```

### Artemis

```kotlin
implementation("xyz.selenus:artemis-seed-vault:1.4.0")
```

### Usage Comparison

```kotlin
// Artemis provides the same API with Kotlin improvements

// Check availability
val available = SeedVault.isAvailable(context)

// Authorize
val authToken = SeedVault.authorizeSeed(activity, PURPOSE_SIGN_SOLANA_TRANSACTION)

// Sign transaction (now with coroutines!)
val signature = withContext(Dispatchers.IO) {
    seedVault.signTransaction(authToken, derivationPath, transaction)
}
```

---

## Innovative Features in Artemis

After migrating, you get access to features not available in solana-kmp or Solana Mobile SDK:

### 1. Privacy Module (`artemis-privacy`)
```kotlin
import com.selenus.artemis.privacy.*

// Confidential Transfers - Hide transaction amounts
val senderKeys = ConfidentialTransfer.generateKeys()
val recipientKeys = ConfidentialTransfer.generateKeys()

val amount = 1_000_000L  // 1 SOL
val commitment = ConfidentialTransfer.encryptAmount(
    amount = amount,
    recipientPubkey = recipientKeys.publicKey,
    senderKeys = senderKeys
)

// Verify without revealing amount
val isValid = ConfidentialTransfer.verifyCommitment(
    commitment = commitment,
    recipientPubkey = recipientKeys.publicKey
)

// Ring Signatures - Anonymous transactions
val yourKeypair = Keypair.generate()
val decoy1 = Keypair.generate().publicKey
val decoy2 = Keypair.generate().publicKey

val ring = listOf(
    yourKeypair.publicKey.bytes,
    decoy1.bytes,
    decoy2.bytes
)

val message = "transfer:100000:recipient".toByteArray()
val signature = RingSignature.sign(
    message = message,
    signerPrivateKey = yourKeypair.secretKey,
    ring = ring,
    signerIndex = 0
)

// Anyone can verify, but can't tell which member signed
val isValid = RingSignature.verify(message, signature, ring)

// Mixing Pools - CoinJoin-style privacy
val pool = MixingPool.createPool(
    poolId = "mix-round-42",
    denomination = 1_000_000L,
    minParticipants = 3,
    maxParticipants = 10,
    timeout = 300_000L
)

val commitment = MixingPool.commit(
    participantKeypair = yourKeypair,
    denomination = 1_000_000L,
    outputAddress = "your_output_address"
)
pool.addCommitment(commitment)
```

### 2. Gaming Module (`artemis-gaming`)
```kotlin
import com.selenus.artemis.gaming.*

// Verifiable Randomness - Provably fair RNG
val (commitment, secret) = VerifiableRandomness.commit()
// Submit commitment on-chain...
// Later reveal to prove fairness
val reveal = VerifiableRandomness.Reveal(
    value = secret.copyOfRange(0, 32),
    salt = secret.copyOfRange(32, 48),
    commitment = commitment
)
val isValid = reveal.verify()

// VRF (Verifiable Random Function)
val vrfOutput = VerifiableRandomness.vrfGenerate(
    privateKey = gameServerKey,
    message = "game-round-42".toByteArray()
)

// Reward Distribution - Flexible payout strategies
val totalPool = 10_000_000L  // 0.01 SOL

// Linear decay (50%, 33%, 17%)
val linear = RewardDistribution.PayoutStrategy.LinearDecay(topN = 3)
val payouts = linear.calculate(totalPool, 3)

// Exponential (heavier top-weighting)
val exp = RewardDistribution.PayoutStrategy.ExponentialPayout(
    topN = 3,
    decayFactor = 0.5
)

// Merkle claims for gas-efficient distribution
val claims = winners.zip(payouts).map { (pubkey, amount) ->
    RewardDistribution.RewardClaim(
        recipient = pubkey,
        amount = amount,
        metadata = mapOf("rank" to "top3")
    )
}

val rewardTree = RewardDistribution.RewardTree.build(claims)
val proof = rewardTree.generateProof(player1Pubkey)

// Game State Proofs - Cryptographic verification
val gameState = GameStateProofs.GameState(
    stateHash = ByteArray(32),
    nonce = 0,
    timestamp = System.currentTimeMillis(),
    players = listOf(player1, player2),
    data = mapOf(
        "player1_hp" to byteArrayOf(100),
        "player2_hp" to byteArrayOf(100)
    )
)

val newState = gameState.update("player1_hp", byteArrayOf(90))
val stateTree = GameStateProofs.buildStateTree(newState)
```

### 3. Token-2022 Complete Support (`artemis-token2022`)
```kotlin
import com.selenus.artemis.token2022.*

// All 8 extensions supported
val extensions = listOf(
    Token2022Extension.TransferFeeConfig(100, 10000),
    Token2022Extension.InterestBearingConfig(authority, 500),
    Token2022Extension.NonTransferable,
    Token2022Extension.PermanentDelegate(delegateAuthority),
    Token2022Extension.TransferHook(programId),
    Token2022Extension.MetadataPointer(metadataAddress)
)

val instructions = AdvancedToken2022Extensions.prepareMintWithExtensions(
    mint = mintKeypair.publicKey,
    mintAuthority = authority,
    decimals = 9,
    extensions = extensions,
    payer = payer
)
```

### 4. NFT Batch Operations (`artemis-metaplex`)
```kotlin
import com.selenus.artemis.metaplex.*

// Batch mint 4 NFTs per transaction
val metadata = listOf(
    NftMetadata("NFT #1", "https://arweave.net/1", 500),
    NftMetadata("NFT #2", "https://arweave.net/2", 500),
    NftMetadata("NFT #3", "https://arweave.net/3", 500),
    NftMetadata("NFT #4", "https://arweave.net/4", 500)
)

val result = AdvancedNftOperations.batchMintNfts(
    payer = payer,
    creator = creator,
    metadataList = metadata,
    rpcClient = rpc
)

// Dynamic metadata with state hashing
val dynamicMetadata = AdvancedNftOperations.DynamicMetadata(
    baseUri = "https://api.mygame.com/nft",
    stateHash = gameState.stateHash,
    updateAuthority = updateAuthority,
    mutable = true
)
```

### 5. WebSocket Subscriptions (`artemis-ws`)
```kotlin
import com.selenus.artemis.ws.*

val ws = SolanaWebSocket("wss://api.mainnet-beta.solana.com")

// Account change subscription
ws.accountSubscribe(pubkey).collect { account ->
    println("Account updated: ${account.lamports}")
}

// Signature confirmation
ws.signatureSubscribe(signature).collect { status ->
    if (status.confirmationStatus == "finalized") {
        println("Transaction confirmed!")
    }
}
```

### 6. Enhanced Wallet Integration (`artemis-wallet`)
```kotlin
import com.selenus.artemis.wallet.*

// SendTransactionOptions with commitment control
val options = SendTransactionOptions(
    commitment = CommitmentLevel.FINALIZED,
    preflightCommitment = CommitmentLevel.PROCESSED,
    skipPreflight = false,
    maxRetries = 3
)

val signature = wallet.signAndSendTransaction(
    transaction = tx,
    options = options
)

// Batch transactions with ordered execution
val results = wallet.signAndSendTransactions(
    transactions = listOf(tx1, tx2, tx3),
    waitForCommitmentToSendNextTransaction = true
)
```

### 7. Compute Budget Management (`artemis-compute`)
```kotlin
import com.selenus.artemis.compute.*

// Game-optimized presets
val instructions = ComputeBudgetPresets.preset(
    ComputeBudgetPresets.Tier.COMPETITIVE
)

// Custom priority fees
val ix = ComputeBudget.setComputeUnitPrice(microLamports = 1000)
val ix2 = ComputeBudget.setComputeUnitLimit(units = 200_000)
```

---

## React Native Migration

### Before: Multiple packages
```json
{
  "dependencies": {
    "@solana/web3.js": "^1.87.0",
    "@solana-mobile/mobile-wallet-adapter-protocol": "^2.0.0",
    "@solana-mobile/wallet-adapter-mobile": "^2.0.0"
  }
}
```

### After: Artemis unified
```json
{
  "dependencies": {
    "artemis-solana-sdk": "^1.4.0"
  }
}
```

### Usage
```typescript
import { Base58, Crypto, MWA } from 'artemis-solana-sdk';

// Generate keypair (native module)
const keypair = await Crypto.generateKeypair();

// Base58 encoding (native module)
const encoded = await Base58.encode(bytes);

// MWA integration
const result = await MWA.connect(appIdentity);
const signatures = await MWA.signTransactions(transactions);
```

---

## Troubleshooting

### "Cannot find symbol: SolanaPublicKey"

Make sure you're importing from Artemis:
```kotlin
import com.selenus.artemis.runtime.Pubkey    // ✅ Artemis
import com.solana.publickey.SolanaPublicKey  // ❌ Old solana-kmp
```

### "Unresolved reference: secretKey"

The `secretKey` property is an extension. Make sure you have the correct import:
```kotlin
import com.selenus.artemis.runtime.*  // Includes extensions
```

### "Unresolved reference: Token2022Tlv"

Make sure you're using the correct package:
```kotlin
import com.selenus.artemis.token2022.Token2022Tlv  // ✅ Correct
```

### "Unresolved reference: ComputeBudgetPresets"

Available in both compute and gaming modules:
```kotlin
import com.selenus.artemis.compute.ComputeBudgetPresets  // ✅ From compute module
import com.selenus.artemis.gaming.ComputeBudgetPresets   // ✅ From gaming module (with gaming presets)
```

### "Unresolved reference: VrfUtils"

The class is named `VerifiableRandomness`:
```kotlin
import com.selenus.artemis.gaming.VerifiableRandomness  // ✅ Correct name
// NOT: import com.selenus.artemis.gaming.VrfUtils      // ❌ Wrong name
```

### MWA not connecting

Ensure you have the Android MWA module:
```kotlin
implementation("xyz.selenus:artemis-wallet-mwa-android:1.5.0")
```

### Build errors with BouncyCastle

Artemis includes BouncyCastle. Exclude duplicates:
```kotlin
implementation("xyz.selenus:artemis-core:1.4.0") {
    exclude(group = "org.bouncycastle")
}
```

---

## Complete Example: Before & After

### Before (solana-kmp + Solana Mobile)
```kotlin
import com.solana.publickey.SolanaPublicKey
import com.solana.keypair.SolanaKeypair
import com.solana.rpc.SolanaRpcClient
import com.solana.networking.KtorNetworkDriver
import com.solanamobile.mobilewalletadapter.*

class WalletManager {
    private val keypair = SolanaKeypair.generate()
    private val rpc = SolanaRpcClient("https://api.mainnet-beta.solana.com", KtorNetworkDriver())
    
    fun getAddress(): String = keypair.publicKey.toString()
    
    suspend fun getBalance(): Long {
        return rpc.getBalance(keypair.publicKey)
    }
    
    suspend fun sendTransaction(to: String, amount: Long): String {
        val blockhash = rpc.getLatestBlockhash()
        // Build transaction manually...
        val tx = buildTransaction(to, amount, blockhash)
        return rpc.sendTransaction(tx)
    }
}
```

### After (Artemis)
```kotlin
import com.selenus.artemis.runtime.*
import com.selenus.artemis.rpc.RpcClient
import kotlinx.coroutines.*

class WalletManager {
    private val keypair = Keypair()
    private val rpc = RpcClient("https://api.mainnet-beta.solana.com")
    
    fun getAddress(): String = keypair.publicKey.toBase58()
    
    suspend fun getBalance(): Long = rpc.getBalance(keypair.publicKey)
    
    suspend fun sendTransaction(to: String, amount: Long): String {
        val recipient = Pubkey.fromBase58(to)
        val blockhash = rpc.getLatestBlockhash()
        
        val tx = Transaction.build {
            recentBlockhash = blockhash
            feePayer = keypair.publicKey
            addInstruction(SystemProgram.transfer(keypair.publicKey, recipient, amount))
        }.sign(keypair)
        
        return rpc.sendTransaction(tx)
    }
}
```

---

## Getting Help

- 📖 [Artemis Documentation](https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-)
- 💬 [GitHub Issues](https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-/issues)
- 📦 [Maven Central](https://central.sonatype.com/search?q=xyz.selenus)

---

## For Solana Mobile Team: Why Recommend Artemis?

### Current State

Your example apps in `mobile-wallet-adapter` use:
```kotlin
import com.solana.networking.KtorNetworkDriver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.SolanaRpcClient
```

This is from `solana-kmp` (Funkatronics/Metaplex). While it works, developers need **3+ separate dependencies** to build a complete app:

1. `mobile-wallet-adapter-clientlib-ktx` (MWA)
2. `seedvault-wallet-sdk` (Seed Vault)
3. `solana-kmp` (RPC + primitives)

### The Artemis Advantage

**One SDK that replaces everything:**

| What Developers Need | Current Approach | Artemis |
|----------------------|------------------|---------|
| MWA 2.0 | `clientlib-ktx` | ✅ `artemis-wallet-mwa-android` |
| Seed Vault | `seedvault-wallet-sdk` | ✅ `artemis-seed-vault` |
| PublicKey/Keypair | `solana-kmp` | ✅ `artemis-core` |
| RPC Client | `solana-kmp` | ✅ `artemis-rpc` |
| **WebSocket** | ❌ Not available | ✅ `artemis-ws` |
| **Token-2022** | ❌ Limited | ✅ `artemis-token2022` |
| **Gaming Utils** | ❌ None | ✅ `artemis-gaming` |

### Drop-In Compatibility

Artemis provides type aliases so existing code works without changes:

```kotlin
// These work in Artemis via compatibility layer:
typealias SolanaPublicKey = Pubkey
typealias Account = Keypair
```

### Example: Updating Your Example Apps

In `example-clientlib-ktx-app/SolanaRpcUseCase.kt`:

```kotlin
// BEFORE
import com.solana.networking.KtorNetworkDriver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.SolanaRpcClient

val rpc = SolanaRpcClient(url, KtorNetworkDriver())

// AFTER
import xyz.selenus.artemis.rpc.RpcClient
import xyz.selenus.artemis.core.Pubkey

val rpc = RpcClient(url)  // No driver needed, simpler!
```

### Next Steps

1. **Evaluate Artemis** for your internal projects
2. **Update example apps** to show Artemis as the recommended SDK
3. **Link to Artemis** in Solana Mobile docs as the "batteries-included" option

**Contact:** [GitHub Issues](https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-/issues)

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.4.0 | 2025 | Module rename (artemis-runtime → artemis-core), Solana Mobile parity |
| 1.3.0 | 2025 | Token-2022, Gaming utilities |
| 1.2.0 | 2024 | MWA 2.0, WebSocket support |
| 1.0.0 | 2024 | Initial release |
