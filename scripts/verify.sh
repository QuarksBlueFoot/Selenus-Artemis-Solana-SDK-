#!/usr/bin/env bash
# Local pre-publish verification. Mirrors the CI gates so a green run here
# implies a green run in Actions.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> build + assembleDebug"
./gradlew --no-daemon clean assembleDebug

echo "==> unit tests (foundation + wallet + MWA + Seed Vault)"
./gradlew --no-daemon \
    :artemis-tx:jvmTest \
    :artemis-vtx:jvmTest \
    :artemis-programs:jvmTest \
    :artemis-rpc:jvmTest \
    :artemis-ws:jvmTest \
    :artemis-wallet:jvmTest \
    :artemis-wallet-mwa-android:testDebugUnitTest \
    :artemis-seed-vault:testDebugUnitTest

echo "==> MWA walletlib 2.0 behavior gate"
./gradlew --no-daemon :artemis-wallet-mwa-android:testDebugUnitTest --tests '*BehaviorTest*'

echo "==> client SDK compat parity gate"
./gradlew --no-daemon \
    :artemis-mwa-common-compat:testDebugUnitTest \
    :artemis-mwa-clientlib-compat:testDebugUnitTest \
    :artemis-mwa-walletlib-compat:testDebugUnitTest \
    :artemis-mwa-compat:testDebugUnitTest \
    :artemis-seedvault-compat:testDebugUnitTest \
    :artemis-web3-solana-compat:jvmTest \
    :artemis-rpc-core-compat:jvmTest \
    :artemis-sol4k-compat:jvmTest \
    :artemis-solana-kmp-compat:jvmTest \
    :artemis-metaplex-android-compat:jvmTest

echo "==> dependency ring enforcement"
./gradlew --no-daemon checkDependencyRings

echo "==> compat API surface diff"
./gradlew --no-daemon dumpApi
if ! git diff --exit-code -- 'interop/*/api/*.api'; then
    echo "ERROR: Compat API surface drifted. Review the diff and, if intentional, commit the updated snapshots."
    git --no-pager diff -- 'interop/*/api/*.api'
    exit 1
fi

echo "==> OK"
