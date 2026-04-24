#!/bin/bash

# Artemis SDK 2.3.0: devnet test runner
# Sets up a devnet keypair (or reuses one from ~/.config/solana/id.json)
# and runs the integration test module at testing/artemis-devnet-tests/.

set -e

echo "Artemis SDK 2.3.0 devnet test suite"
echo "==================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default keypair location
KEYPAIR_PATH="${HOME}/.config/solana/id.json"
DEVNET_KEYPAIR_PATH="${DEVNET_KEYPAIR_PATH:-$KEYPAIR_PATH}"

# Check if Solana CLI is installed
if ! command -v solana &> /dev/null; then
    echo -e "${YELLOW}[warn] Solana CLI not found${NC}"
    echo ""
    echo "Install it with:"
    echo "  sh -c \"\$(curl -sSfL https://release.solana.com/stable/install)\""
    echo ""
    echo "Docs: https://docs.solana.com/cli/install-solana-cli-tools"
    echo ""
    echo -e "${BLUE}[info] You can also provide an existing keypair via DEVNET_KEYPAIR_PATH${NC}"
    read -p "Continue without Solana CLI? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}[ok] Solana CLI found${NC}"
    solana --version
fi

echo ""
echo "Checking for devnet wallet..."

# Check if keypair exists
if [ ! -f "$DEVNET_KEYPAIR_PATH" ]; then
    echo -e "${YELLOW}[warn] Keypair not found at: $DEVNET_KEYPAIR_PATH${NC}"
    echo ""

    if command -v solana &> /dev/null; then
        read -p "Generate a new devnet keypair? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            mkdir -p "$(dirname "$DEVNET_KEYPAIR_PATH")"
            solana-keygen new --outfile "$DEVNET_KEYPAIR_PATH" --no-bip39-passphrase
            echo -e "${GREEN}[ok] Keypair generated${NC}"
        else
            echo -e "${RED}[error] Cannot run tests without a keypair${NC}"
            exit 1
        fi
    else
        echo -e "${RED}[error] Cannot generate a keypair without Solana CLI${NC}"
        echo "Install Solana CLI or provide DEVNET_KEYPAIR_PATH"
        exit 1
    fi
else
    echo -e "${GREEN}[ok] Keypair found: $DEVNET_KEYPAIR_PATH${NC}"
fi

# Get wallet address
if command -v solana &> /dev/null; then
    WALLET_ADDRESS=$(solana-keygen pubkey "$DEVNET_KEYPAIR_PATH")
    echo -e "${BLUE}[info] Wallet: $WALLET_ADDRESS${NC}"

    # Check balance
    echo ""
    echo "Checking devnet balance..."
    solana config set --url devnet > /dev/null 2>&1
    BALANCE=$(solana balance "$WALLET_ADDRESS" 2>/dev/null || echo "0 SOL")
    echo "   Balance: $BALANCE"

    # Airdrop if needed
    if [[ "$BALANCE" =~ ^0 ]] || [[ "$BALANCE" =~ ^0\.0 ]]; then
        echo -e "${YELLOW}[warn] Low balance, requesting airdrop...${NC}"

        for i in {1..3}; do
            if solana airdrop 2 "$WALLET_ADDRESS" 2>/dev/null; then
                echo -e "${GREEN}[ok] Airdrop successful${NC}"
                sleep 2
                BALANCE=$(solana balance "$WALLET_ADDRESS")
                echo "   New balance: $BALANCE"
                break
            else
                if [ $i -eq 3 ]; then
                    echo -e "${YELLOW}[warn] Airdrop failed. Tests may fail due to insufficient funds.${NC}"
                    echo "   Try a manual airdrop: solana airdrop 2 $WALLET_ADDRESS --url devnet"
                else
                    echo "   Retrying airdrop ($i/3)..."
                    sleep 2
                fi
            fi
        done
    else
        echo -e "${GREEN}[ok] Balance sufficient${NC}"
    fi
fi

echo ""
echo "Building Artemis SDK..."
echo ""

# Build the SDK (skip unit tests; they run as part of the devnet task below)
./gradlew clean build -x test --no-daemon

if [ $? -ne 0 ]; then
    echo -e "${RED}[error] Build failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}[ok] Build successful${NC}"
echo ""
echo "Running devnet tests..."
echo "======================="
echo ""

# Export keypair path for tests
export DEVNET_KEYPAIR_PATH="$DEVNET_KEYPAIR_PATH"

# Run devnet tests
./gradlew :artemis-devnet-tests:test --no-daemon --info

TEST_RESULT=$?

echo ""
echo "======================="

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}[ok] All devnet tests passed${NC}"
    echo ""
    echo "The test suite exercises:"
    echo "   - Core types (Pubkey, Keypair)"
    echo "   - RPC client initialization"
    echo "   - Transaction builder and pipeline"
    echo "   - Wallet signing paths"
    echo ""
    echo "Next steps:"
    echo "   1. Publish to Maven Central: ./publish.sh"
    echo "   2. Tag the release:          git tag v2.3.0 && git push origin v2.3.0"
    exit 0
else
    echo -e "${RED}[error] Some tests failed${NC}"
    echo ""
    echo "Check test output above for details."
    echo ""
    echo "Common issues:"
    echo "   - Insufficient SOL balance (request airdrop)"
    echo "   - Network connectivity (check devnet status)"
    echo "   - Rate limiting (wait and retry)"
    exit 1
fi
