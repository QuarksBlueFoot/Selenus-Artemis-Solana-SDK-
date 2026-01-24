# artemis-privacy

**Production-ready privacy features for Solana transactions**

> ⭐ **New in v1.5.0**: Confidential Transfers, Ring Signatures, and Mixing Pools

Artemis Privacy brings privacy-preserving cryptography to Solana mobile applications:
- 🔒 **Confidential Transfers**: Hide transaction amounts using Pedersen commitments
- 👥 **Ring Signatures**: Anonymous transactions with unlinkable signatures
- 🌊 **Mixing Pools**: CoinJoin-style transaction mixing for privacy

## Features

### 🔒 Confidential Transfers

Hide transaction amounts while proving validity using homomorphic encryption.

```kotlin
import com.selenus.artemis.privacy.ConfidentialTransfer

// Generate keys for confidential transfers
val senderKeys = ConfidentialTransfer.generateKeys()
val recipientKeys = ConfidentialTransfer.generateKeys()

// Encrypt amount with Pedersen commitment
val amount = 1_000_000L  // 1 SOL
val commitment = ConfidentialTransfer.encryptAmount(
    amount = amount,
    recipientPubkey = recipientKeys.publicKey,
    senderKeys = senderKeys
)

// Verify commitment without revealing amount
val isValid = ConfidentialTransfer.verifyCommitment(
    commitment = commitment,
    recipientPubkey = recipientKeys.publicKey
)

// Decrypt amount as recipient
val decryptedAmount = ConfidentialTransfer.decryptAmount(
    commitment = commitment,
    recipientKeys = recipientKeys
)
// decryptedAmount == 1_000_000L

// Range proof (prove amount is within valid range without revealing it)
val rangeProof = ConfidentialTransfer.generateRangeProof(
    amount = amount,
    commitment = commitment,
    blinding = commitment.blinding,
    minValue = 0,
    maxValue = 1_000_000_000L  // 1000 SOL max
)

val proofValid = ConfidentialTransfer.verifyRangeProof(
    proof = rangeProof,
    commitment = commitment,
    minValue = 0,
    maxValue = 1_000_000_000L
)

// Auditor support (designated third party can decrypt)
val auditorKeys = ConfidentialTransfer.generateKeys()
val auditableCommitment = ConfidentialTransfer.encryptWithAuditor(
    amount = amount,
    recipientPubkey = recipientKeys.publicKey,
    auditorPubkey = auditorKeys.publicKey,
    senderKeys = senderKeys
)

val auditedAmount = ConfidentialTransfer.auditDecrypt(
    commitment = auditableCommitment,
    auditorKeys = auditorKeys
)
```

**Use cases**: Private payments, salary payments, confidential auctions, private DeFi

### 👥 Ring Signatures

Sign transactions anonymously within a group (ring) of possible signers.

```kotlin
import com.selenus.artemis.privacy.RingSignature

// Create a ring of public keys (your key + decoys)
val yourKeypair = Keypair.generate()
val decoy1 = Keypair.generate().publicKey
val decoy2 = Keypair.generate().publicKey
val decoy3 = Keypair.generate().publicKey

val ring = listOf(
    yourKeypair.publicKey.bytes,
    decoy1.bytes,
    decoy2.bytes,
    decoy3.bytes
)

val message = "transfer:100000:recipient_address".toByteArray()

// Sign anonymously (no one knows which key in the ring signed)
val signature = RingSignature.sign(
    message = message,
    signerPrivateKey = yourKeypair.secretKey,
    ring = ring,
    signerIndex = 0  // Your position in ring
)

// Anyone can verify the signature came from someone in the ring
val isValid = RingSignature.verify(
    message = message,
    signature = signature,
    ring = ring
)
// true, but verifier doesn't know which member signed

// Linkable ring signatures (prevent double-spending)
val linkableSignature = RingSignature.signLinkable(
    message = message,
    signerPrivateKey = yourKeypair.secretKey,
    ring = ring,
    signerIndex = 0
)

// If same signer signs twice, signatures can be linked
val signature2 = RingSignature.signLinkable(
    message = "transfer:200000:another_recipient".toByteArray(),
    signerPrivateKey = yourKeypair.secretKey,
    ring = ring,
    signerIndex = 0
)

val areLinked = RingSignature.areLinked(linkableSignature, signature2)
// true - same key image proves same signer

// Support for large rings (up to 128 members)
val largeRing = (1..64).map { Keypair.generate().publicKey.bytes }
val efficientSignature = RingSignature.sign(
    message = message,
    signerPrivateKey = yourKeypair.secretKey,
    ring = largeRing,
    signerIndex = 32
)
```

**Use cases**: Private voting, anonymous donations, whistleblower protection, privacy-preserving DeFi

### 🌊 Mixing Pools

CoinJoin-style transaction mixing for breaking on-chain traceability.

```kotlin
import com.selenus.artemis.privacy.MixingPool

// Create a mixing pool session
val pool = MixingPool.createPool(
    poolId = "mix-round-42",
    denomination = 1_000_000L,  // All amounts must be 1 SOL
    minParticipants = 3,
    maxParticipants = 10,
    timeout = 300_000L  // 5 minutes
)

// Participant 1: Commit to participation
val participant1Keys = Keypair.generate()
val commitment1 = MixingPool.commit(
    participantKeypair = participant1Keys,
    denomination = 1_000_000L,
    outputAddress = "output_address_1"
)

// Add to pool
pool.addCommitment(commitment1)

// ... more participants join ...

// Check if pool is ready
val isReady = pool.isReady()  // true when minParticipants reached

// Reveal phase (after all commitments)
val reveal1 = MixingPool.reveal(
    commitment = commitment1,
    participantKeypair = participant1Keys
)

pool.addReveal(reveal1)

// Once all reveals submitted, compute shuffled outputs
val mixedOutputs = pool.shuffle()
// mixedOutputs is randomized, breaking input→output links

// Verify the mix was fair
val verificationProof = pool.generateProof()
val proofValid = MixingPool.verifyProof(verificationProof)

// Execute mixed transaction (all inputs → all outputs in one tx)
val mixTx = pool.buildMixTransaction(
    recentBlockhash = recentBlockhash,
    feePayer = coordinator.publicKey
)
```

**Use cases**: Privacy-enhanced wallets, anonymous transfers, unlinkable payments

## Security Notes

### Cryptographic Primitives

All implementations use **BouncyCastle** (already in Artemis Core dependencies):
- **Pedersen Commitments**: Elliptic curve operations on Ed25519
- **Range Proofs**: Bulletproofs-style compact proofs
- **Ring Signatures**: SAG (Spontaneous Anonymous Group) signatures
- **Mixing**: Commit-reveal with cryptographic shuffling

### Mobile Optimization

- Lazy computation: expensive operations deferred until needed
- Batch verification: verify multiple proofs/signatures together
- Memory efficient: streaming processing for large rings
- Android-friendly: no JNI or native dependencies

### Standards Compliance

✅ Compatible with Solana Foundation privacy proposals  
✅ Follows Zcash Sapling range proof specification  
✅ Ring signatures based on CryptoNote/Monero SAG  
✅ Mixing pools follow CoinJoin best practices  

## Architecture

All features follow modern 2026 Android patterns:
- ✅ Kotlin Coroutines for crypto operations
- ✅ Flow for pool coordination
- ✅ StateFlow for real-time pool status
- ✅ Zero additional dependencies (uses BouncyCastle)
- ✅ Production-ready for mobile wallets

## License

Apache-2.0 - See root LICENSE file
