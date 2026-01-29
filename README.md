# Artemis Solana SDK

A comprehensive Kotlin/Android SDK for building Solana mobile applications with privacy features, transaction building, and ecosystem integrations.

[![Maven Central](https://img.shields.io/maven-central/v/xyz.selenus/artemis-core?style=flat-square)](https://central.sonatype.com/search?q=xyz.selenus)
[![NPM](https://img.shields.io/npm/v/@selenus/artemis-solana-sdk?style=flat-square)](https://www.npmjs.com/package/@selenus/artemis-solana-sdk)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)

## Introduction

Artemis provides enabling libraries for Android wallet apps and dApps, allowing developers to build mobile experiences on Solana. Whether you're building a native Kotlin app, a React Native application, or integrating wallet functionality, these libraries handle the complexity of on-chain interaction, transaction construction, and privacy features.

**Maintained by [Bluefoot Labs](https://bluefootlabs.xyz)**

## Documentation

- [Quickstart Guide](QUICKSTART_v2.0.0.md)
- [Technical Architecture](docs/TECHNICAL_ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

## What's Included

### Core Libraries

| Module | Description |
|--------|-------------|
| **artemis-core** | Cryptographic primitives, Base58 encoding, public key handling |
| **artemis-rpc** | JSON-RPC client for Solana nodes with retry and failover |
| **artemis-tx** | Transaction construction and serialization |
| **artemis-vtx** | Versioned transaction support with address lookup tables |
| **artemis-ws** | WebSocket client for real-time subscriptions |

### Mobile Wallet Integration

| Module | Description |
|--------|-------------|
| **artemis-wallet** | Wallet abstraction layer with signing interfaces |
| **artemis-wallet-mwa-android** | Mobile Wallet Adapter 2.0 implementation for Android |
| **artemis-seed-vault** | Seed Vault integration for secure key custody |
| **artemis-solana-pay** | Solana Pay protocol implementation |

### Privacy

| Module | Description |
|--------|-------------|
| **artemis-privacy** | Privacy toolkit with stealth addresses, encrypted memos, and confidential transfers |

Features:
- **Stealth Addresses** – Receive funds at one-time addresses for receiver privacy
- **Encrypted Memos** – End-to-end encrypted on-chain messaging
- **Confidential Transfers** – Hidden transfer amounts with homomorphic commitments
- **Session Key Management** – Ephemeral keys with automatic rotation and cleanup

### Program Interaction

| Module | Description |
|--------|-------------|
| **artemis-anchor** | Type-safe Anchor program client with IDL parsing |
| **artemis-universal** | Program interaction without IDL via runtime discovery |
| **artemis-programs** | Common program instructions (System, Token, Associated Token) |

### Tokens & NFTs

| Module | Description |
|--------|-------------|
| **artemis-token2022** | Token-2022 extensions (transfer fees, interest, metadata) |
| **artemis-metaplex** | Token Metadata program integration |
| **artemis-mplcore** | MPL Core (Asset) program support |
| **artemis-cnft** | Compressed NFT (Bubblegum) operations |
| **artemis-candy-machine** | Candy Machine minting support |

### DeFi & Ecosystem

| Module | Description |
|--------|-------------|
| **artemis-jupiter** | Jupiter DEX aggregator integration |
| **artemis-actions** | Solana Actions and Blinks support |
| **artemis-nlp** | Natural language transaction parsing |
| **artemis-streaming** | Zero-copy account streaming for real-time updates |

### Gaming & DePIN

| Module | Description |
|--------|-------------|
| **artemis-gaming** | Session keys, verifiable randomness, state proofs |
| **artemis-depin** | DePIN device attestation utilities |

### Developer Tools

| Module | Description |
|--------|-------------|
| **artemis-compute** | Compute unit estimation and priority fee optimization |
| **artemis-simulation** | Transaction simulation before submission |
| **artemis-batch** | Batch transaction processing |
| **artemis-errors** | Structured error handling and decoding |

---

## Installation

### Gradle (Kotlin/Android)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core
    implementation("xyz.selenus:artemis-core:2.1.0")
    implementation("xyz.selenus:artemis-rpc:2.1.0")
    implementation("xyz.selenus:artemis-tx:2.1.0")
    
    // Mobile Wallet
    implementation("xyz.selenus:artemis-wallet-mwa-android:2.1.0")
    implementation("xyz.selenus:artemis-seed-vault:2.1.0")
    
    // Privacy
    implementation("xyz.selenus:artemis-privacy:2.1.0")
    
    // Add additional modules as needed
}
```

### NPM (React Native)

```bash
npm install @selenus/artemis-solana-sdk
```

---

## Usage Examples

### Wallet Connection (Mobile Wallet Adapter)

```kotlin
val adapter = MwaWalletAdapter.create(context)

// Connect to wallet
val result = adapter.connect()
if (result is WalletResult.Success) {
    val publicKey = result.publicKey
    println("Connected: ${publicKey.toBase58()}")
}

// Sign and send transaction
val signature = adapter.signAndSendTransaction(transaction)
```

### Transaction Building

```kotlin
val rpc = RpcClient.create(Cluster.MAINNET_BETA)

// Build a transfer instruction
val transferIx = SystemProgram.transfer(
    from = wallet.publicKey,
    to = recipient,
    lamports = 1_000_000_000 // 1 SOL
)

// Construct transaction
val tx = Transaction.new {
    feePayer(wallet.publicKey)
    recentBlockhash(rpc.getLatestBlockhash())
    instruction(transferIx)
}

// Sign and send
val signature = wallet.signAndSend(tx)
```

### Privacy: Stealth Addresses

```kotlin
// Receiver generates stealth keys
val stealthKeys = StealthAddress.generateKeys()
val metaAddress = stealthKeys.toMetaAddress()

// Publish meta-address (e.g., in profile, QR code)
println("st:${metaAddress.toCompact()}")

// Sender derives one-time address
val (stealthPubkey, ephemeralPubkey) = StealthAddress.deriveAddress(metaAddress)

// Sender transfers to stealth address
val tx = SystemProgram.transfer(sender, stealthPubkey, amount)

// Receiver scans for payments and derives spending key
val spendingKey = StealthAddress.deriveSpendingKey(stealthKeys, ephemeralPubkey)
```

### Privacy: Encrypted Memos

```kotlin
// Encrypt a memo for a recipient
val encrypted = EncryptedMemo.encrypt(
    message = "Payment for invoice #123",
    recipientPubkey = recipientPublicKey,
    type = MemoType.PAYMENT_NOTE
)

// Attach to transaction
val tx = Transaction.new {
    instruction(transferIx)
    instruction(MemoProgram.memo(encrypted.serialize()))
}

// Recipient decrypts
val decrypted = EncryptedMemo.decrypt(memoData, recipientPrivateKey)
println("Message: ${decrypted.message}")
```

### Jupiter DEX Integration

```kotlin
val jupiter = JupiterClient.create()

// Get quote
val quote = jupiter.quote {
    inputMint(USDC_MINT)
    outputMint(SOL_MINT)
    amount(1_000_000) // 1 USDC
    slippageBps(50)   // 0.5%
}

println("Output: ${quote.outAmount} lamports")
println("Price impact: ${quote.priceImpactPct}%")

// Build swap transaction
val swap = jupiter.swap {
    quote(quote)
    userPublicKey(wallet.publicKey)
}

val signature = wallet.signAndSend(swap.transaction)
```

### Anchor Program Interaction

```kotlin
val idl = AnchorIdl.parse(idlJson)
val program = AnchorProgram(idl, programId, rpc)

// Build instruction
val ix = program.methods
    .instruction("initialize")
    .args(mapOf("name" to "MyToken", "decimals" to 9))
    .accounts {
        account("mint", mintPubkey)
        signer("authority", wallet.publicKey)
        program("systemProgram", SystemProgram.ID)
    }
    .build()

// Fetch account data
val state = program.account.type("TokenState").fetch(stateAddress)
println("Name: ${state.get<String>("name")}")
```

### Natural Language Transactions

```kotlin
val nlp = NaturalLanguageBuilder.create(resolver)

val result = nlp.parse("send 1 SOL to alice.sol")

when (result) {
    is ParseResult.Success -> {
        println("Intent: ${result.intent.summary}")
        val tx = result.buildTransaction()
        wallet.signAndSend(tx)
    }
    is ParseResult.NeedsInfo -> {
        println("Missing: ${result.missing}")
    }
}
```

Supported patterns:
- `"send 1 SOL to alice.sol"` – Transfer
- `"swap 100 USDC for SOL"` – Jupiter swap
- `"stake 10 SOL with Marinade"` – Staking
- `"create token with 1M supply"` – Token creation

### Universal Program Client

```kotlin
val universal = UniversalProgramClient.create(rpc)

// Discover program structure from on-chain data
val program = universal.discover(programId)

// View discovered instructions
program.instructions.forEach { instr ->
    println("${instr.name}: ${instr.discriminator.hex}")
}

// Build instruction using discovered pattern
val ix = program.instruction("transfer") {
    account("source", myWallet)
    account("destination", recipient)
    u64("amount", 1_000_000)
}
```

### Real-Time Account Streaming

```kotlin
val stream = ZeroCopyAccountStream.create(wsClient)

// Subscribe with zero-copy field access
stream.subscribe(tokenAccount, TokenAccountSchema) { accessor ->
    val balance = accessor.getU64("amount")
    val owner = accessor.getPubkey("owner")
    updateUI(balance)
}

// Or use Kotlin Flow
stream.accountFlow(tokenAccount, TokenAccountSchema)
    .map { it.getU64("amount") }
    .distinctUntilChanged()
    .collect { balance -> updateBalance(balance) }
```

### Solana Actions/Blinks

```kotlin
val actions = ActionsClient.create()

// Fetch action metadata
val action = actions.getAction("https://dial.to/donate/example")

println("Title: ${action.title}")
println("Description: ${action.description}")

// Execute the action
val response = actions.executeAction(action) {
    account(wallet.publicKey)
    input("amount", "1.5")
}

val signature = wallet.signAndSend(response.transaction)
```

### Gaming: Verifiable Randomness

```kotlin
// Create commitment
val (commitment, secret) = VerifiableRandomness.commit()

// Submit commitment on-chain, wait for reveal phase

// Reveal and generate verifiable random
val proof = VerifiableRandomness.reveal(secret, commitment)
val randomValue = proof.output

// Verify (anyone can verify)
val valid = VerifiableRandomness.verify(commitment, proof)
```

---

## Target Audience

This SDK is intended for:

- **Mobile dApp developers** building Android applications that interact with Solana
- **Wallet developers** implementing transaction signing services
- **Game developers** requiring session keys and verifiable randomness
- **DeFi developers** integrating swaps and real-time data
- **Privacy-focused applications** requiring stealth payments or encrypted messaging

---

## How to Build

```bash
./gradlew build
```

Run tests:
```bash
./gradlew test
```

Run devnet integration tests:
```bash
./run-devnet-tests.sh
```

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

Apache License 2.0 – See [LICENSE](LICENSE)

---

**Built by [Bluefoot Labs](https://bluefootlabs.xyz)**

