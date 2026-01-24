# Artemis SDK - Devnet Integration Test

Comprehensive integration test for all innovative Artemis SDK features added in January 2026.

## Features Tested

### 🔐 Privacy Module
- **Confidential Transfers** - Hide transaction amounts with Pedersen commitments
- **Ring Signatures** - Anonymous group signatures for mixing
- **Mixing Pool** - CoinJoin-style transaction privacy

### 🎮 Gaming Module
- **Verifiable Randomness** - VRF and commit-reveal for provably fair gaming
- **Game State Proofs** - Merkle state trees and fraud proofs
- **Reward Distribution** - Multiple payout strategies (Winner Takes All, Linear, Exponential, Poker-style)

### 🎨 Metaplex Module
- **Batch NFT Operations** - Mint multiple NFTs efficiently
- **Dynamic Metadata** - Time-based metadata updates
- **Collection Management** - Create and manage NFT collections

### 💰 Token-2022 Module
- **Interest-Bearing Tokens** - Automatic yield calculation
- **Non-Transferable (Soulbound)** - Tokens that can't be transferred
- **Permanent Delegate** - Always-delegated tokens
- **Transfer Hooks** - Custom transfer logic
- **Confidential Transfers** - Private token amounts

## Setup

### 1. Generate Devnet Wallet

```bash
# Generate new keypair
solana-keygen new --no-bip39-passphrase -o ~/.config/solana/devnet.json

# Display your public key
solana-keygen pubkey ~/.config/solana/devnet.json
```

### 2. Fund Your Wallet

```bash
# Request airdrop (2 SOL)
solana airdrop 2 $(solana-keygen pubkey ~/.config/solana/devnet.json) --url devnet

# Check balance
solana balance --url devnet
```

### 3. Export Wallet Seed

```bash
# Get the seed in base58 format
cat ~/.config/solana/devnet.json

# Export as environment variable
export DEVNET_WALLET_SEED="your-base58-seed-here"
```

## Running the Test

### Option 1: Gradle

```bash
cd /workspaces/Selenus-Artemis-Solana-SDK-
./gradlew :samples:artemis-features-devnet-test:run
```

### Option 2: Direct Kotlin Execution

```bash
cd samples/artemis-features-devnet-test
kotlinc -script ArtemisIntegrationTest.kt
```

## Expected Output

The test will output detailed results for each module:

```
═══════════════════════════════════════════════════════════════
   ARTEMIS SDK - Devnet Integration Test Suite
   Testing ALL innovative features (Privacy, Gaming, NFT, Token)
═══════════════════════════════════════════════════════════════

⚙️  Setting up test environment...

   Wallet: <your-pubkey>
   Network Health: ok
   Balance: 2.0 SOL

   ✅ Setup complete

🔐 PRIVACY MODULE TESTS
─────────────────────────────────────────────────────────────

1️⃣  Testing Confidential Transfers
   → Hiding transaction amounts with Pedersen commitments

   ✓ Generated confidential keys
   ✓ Encrypted amount: 1000000 lamports
     Commitment: ...
   ✓ Decrypted amount: 1000000 lamports
   ✅ Confidential transfer cryptography VERIFIED

[... continues for all 8 test sections ...]

═══════════════════════════════════════════════════════════════
   ✅ ALL TESTS COMPLETED SUCCESSFULLY
═══════════════════════════════════════════════════════════════
```

## What This Tests

- ✅ **Cryptographic Primitives** - All crypto operations work correctly
- ✅ **Data Structures** - Serialization and deserialization
- ✅ **Instruction Building** - Transaction construction
- ✅ **Integration** - Modules work together seamlessly
- ⚠️ **Note**: Does not submit to blockchain (saves devnet SOL)

## Real Blockchain Testing

To actually submit transactions to devnet, modify the test to:

1. Build complete transactions with blockhash
2. Sign with wallet keypair
3. Submit via `rpc.sendTransaction()`
4. Confirm via `rpc.confirmTransaction()`

Example:

```kotlin
val blockhash = rpc.getLatestBlockhash()
val tx = Transaction(
    feePayer = wallet.publicKey,
    instructions = yourInstructions,
    recentBlockhash = blockhash.blockhash
)
tx.sign(wallet)
val signature = rpc.sendTransaction(tx)
val confirmed = rpc.confirmTransaction(signature)
```

## Troubleshooting

### "DEVNET_WALLET_SEED not set"
- Export your wallet seed: `export DEVNET_WALLET_SEED="..."`

### "Low balance" warning
- Request more SOL: `solana airdrop 2 <pubkey> --url devnet`

### Network errors
- Devnet may be congested. Wait and retry.
- Try alternative RPC: `https://devnet.helius-rpc.com` or `https://api.devnet.solana.com`

### Airdrop failures
- Devnet faucet has rate limits
- Use https://faucet.solana.com for alternative airdrop

## Next Steps

- Modify test to submit real transactions
- Add error handling and retry logic
- Integrate with your app's wallet adapter
- Deploy programs and test with on-chain programs

## License

Apache 2.0 - See root LICENSE file
