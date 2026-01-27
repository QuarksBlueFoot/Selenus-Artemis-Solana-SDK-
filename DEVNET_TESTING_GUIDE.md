# Devnet Testing Guide - Artemis SDK v2.0.0

## Overview

This guide explains how to run comprehensive devnet tests for all revolutionary features in Artemis SDK v2.0.0.

## Prerequisites

### 1. Install Solana CLI

```bash
sh -c "$(curl -sSfL https://release.solana.com/stable/install)"
```

After installation, restart your terminal and verify:
```bash
solana --version
# Expected: solana-cli 1.18.x or later
```

### 2. Create Devnet Wallet

```bash
# Generate new keypair
solana-keygen new --outfile ~/.config/solana/id.json

# Get your public key
solana-keygen pubkey ~/.config/solana/id.json
```

### 3. Airdrop Devnet SOL

```bash
# Set devnet as default
solana config set --url devnet

# Airdrop 2 SOL (can do multiple times)
solana airdrop 2

# Check balance
solana balance
```

## Running Tests

### Quick Start (Automated)

```bash
# Run all devnet tests with automatic setup
./run-devnet-tests.sh
```

This script will:
- ✅ Check for Solana CLI
- ✅ Verify or create devnet wallet
- ✅ Request airdrop if balance is low
- ✅ Build the SDK
- ✅ Run comprehensive devnet tests
- ✅ Display results

### Manual Testing

```bash
# 1. Build the SDK
./gradlew clean build -x test

# 2. Run devnet tests
./gradlew :artemis-devnet-tests:test

# 3. View detailed results
cat artemis-devnet-tests/build/reports/tests/test/index.html
```

### Custom Keypair Location

```bash
# Use a specific keypair
export DEVNET_KEYPAIR_PATH=/path/to/your/keypair.json
./run-devnet-tests.sh
```

## Test Coverage

The devnet test suite validates all 6 revolutionary features:

### Test 1: Wallet Balance Check ✅
- Verifies devnet wallet has sufficient SOL
- Automatically requests airdrop if needed
- Confirms balance > 0.1 SOL for tests

### Test 2: Transaction Builder with Priority Fees ✅
- Creates real transaction on devnet
- Adds priority fee instructions
- Signs and submits to network
- Confirms transaction on-chain

**What it tests:**
- `artemisTransaction {}` DSL
- Priority fee calculation
- Transaction signing
- RPC submission
- Confirmation polling

### Test 3: Universal Program Client (IDL-less) ✅
- Discovers System Program capabilities
- Analyzes instruction patterns
- Infers account structures
- Works without needing IDL

**What it tests:**
- Program capability discovery
- Instruction pattern matching
- Account role detection
- Transaction analysis

### Test 4: Natural Language Transactions ✅
- Parses natural language intents
- Extracts entities (amounts, addresses)
- Builds transactions from text
- Validates intent confidence

**Test cases:**
- "Send 0.001 SOL to [address]"
- "Transfer 1000000 lamports"
- "Check my balance"

**What it tests:**
- Intent parsing accuracy
- Entity extraction
- Transaction generation
- Confidence scoring

### Test 5: Zero-Copy Account Streaming ✅
- Connects to devnet WebSocket
- Subscribes to account updates
- Receives real-time changes
- Uses memory-mapped buffers

**What it tests:**
- WebSocket connection
- Account subscriptions
- Delta compression
- Memory efficiency
- Real-time updates

### Test 6: Jupiter DEX Integration ✅
- Fetches swap quotes
- Calculates price impact
- Builds swap transactions
- Tests routing algorithm

**Note:** Jupiter devnet support is limited. Test validates:
- Client initialization
- API communication
- Quote parsing
- Transaction building

### Test 7: Solana Actions/Blinks ✅
- Parses action URLs
- Extracts metadata
- Validates parameters
- Builds action transactions

**Test cases:**
- `solana-action:https://...`
- Blink metadata extraction
- Action parameter validation

**What it tests:**
- URL parsing
- Metadata extraction
- Parameter handling
- Transaction generation

### Test 8: Anchor Program Client ✅
- Parses Anchor IDL JSON
- Creates type-safe client
- Builds instructions from IDL
- Derives PDAs from seeds

**What it tests:**
- IDL deserialization
- Type inference
- Account resolution
- PDA derivation
- Borsh serialization

### Test 9: Comprehensive Integration ✅
- Runs all modules together
- Tests interoperability
- Validates success rate
- Generates summary report

**Success criteria:** ≥80% pass rate

## Expected Output

```
🚀 Starting Artemis SDK v2.0.0 Devnet Tests
============================================================
✅ RPC Client: https://api.devnet.solana.com
✅ Wallet: 7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU
============================================================

📊 Test 1: Wallet Balance Check
------------------------------------------------------------
💰 Current Balance: 2.5 SOL
✅ Balance sufficient for tests

🔧 Test 2: Enhanced Transaction Builder
------------------------------------------------------------
📤 Creating transfer: 0.001 SOL to 9xQ...
💎 Priority Fee: 5000 microlamports
✅ Transaction sent: 4kJ8...
🔗 View: https://explorer.solana.com/tx/4kJ8...?cluster=devnet
✅ Transaction confirmed

🔍 Test 3: Universal Program Client (IDL-less)
------------------------------------------------------------
🎯 Analyzing System Program (11111...)
📋 Discovered Capabilities:
   • TRANSFER
   • CREATE_ACCOUNT
   • ALLOCATE
✅ Found 10 recent transactions

... (tests 4-9) ...

============================================================
📊 Test Summary:
   Passed: 9/9
   ✅ TransactionBuilder
   ✅ RpcClient
   ✅ Keypair
   ✅ UniversalClient
   ✅ NLPBuilder
   ✅ PriorityFees
   ✅ JupiterClient
   ✅ ActionClient
   ✅ AnchorClient

🎯 Success Rate: 100%
============================================================
🏁 Devnet Tests Completed
============================================================
```

## Troubleshooting

### "Keypair not found"
**Solution:**
```bash
solana-keygen new --outfile ~/.config/solana/id.json
```

### "Insufficient funds"
**Solution:**
```bash
solana airdrop 2 --url devnet
# If rate limited, wait 60 seconds and try again
```

### "Connection timeout"
**Cause:** Devnet may be slow or congested  
**Solution:** 
- Wait and retry
- Check devnet status: https://status.solana.com
- Use different RPC: Set `DEVNET_RPC_URL` environment variable

### "Transaction failed"
**Possible causes:**
1. Insufficient SOL for fees
2. Network congestion
3. Invalid instruction data
4. Rate limiting

**Solution:**
- Check wallet balance
- Retry with higher priority fees
- Wait between transactions

### "WebSocket connection failed"
**Cause:** Firewall or network restrictions  
**Solution:**
- Check firewall rules for wss:// connections
- Try alternative endpoint
- Run from different network

## CI/CD Integration

### GitHub Actions

```yaml
name: Devnet Tests

on: [push, pull_request]

jobs:
  devnet-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
      
      - name: Install Solana CLI
        run: |
          sh -c "$(curl -sSfL https://release.solana.com/stable/install)"
          echo "$HOME/.local/share/solana/install/active_release/bin" >> $GITHUB_PATH
      
      - name: Generate Devnet Wallet
        run: |
          solana-keygen new --no-bip39-passphrase --outfile ~/.config/solana/id.json
          solana config set --url devnet
      
      - name: Airdrop SOL
        run: |
          for i in {1..3}; do
            solana airdrop 2 && break
            sleep 5
          done
      
      - name: Run Devnet Tests
        run: ./run-devnet-tests.sh
```

## Performance Benchmarks

Expected test execution times:

| Test | Duration | Notes |
|------|----------|-------|
| Wallet Balance | ~2s | RPC call |
| Transaction Builder | ~5s | Includes confirmation |
| Universal Client | ~3s | Analysis + discovery |
| NLP Builder | ~1s | Local parsing |
| Zero-Copy Streaming | ~15s | WebSocket connection |
| Jupiter Integration | ~2s | API call |
| Actions Parser | ~1s | Local validation |
| Anchor Client | ~1s | IDL parsing |
| Integration Test | ~5s | All modules |

**Total:** ~35 seconds

## Next Steps After Tests Pass

1. **Review Results**
   ```bash
   cat artemis-devnet-tests/build/reports/tests/test/index.html
   ```

2. **Publish to Maven**
   ```bash
   ./publish.sh
   ```

3. **Publish to NPM**
   ```bash
   cd artemis-react-native
   npm publish --access public
   ```

4. **Create GitHub Release**
   ```bash
   git tag v2.0.0
   git push origin v2.0.0
   ```

5. **Announce Release**
   - Twitter/X: Share revolutionary features
   - Discord: Post in #announcements
   - Reddit: r/solana, r/solanaDev

---

**Ready to validate Artemis SDK v2.0.0 on devnet! 🚀**
