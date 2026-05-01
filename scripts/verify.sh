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

echo "==> compat parity gate (ktx + non-ktx)"
./gradlew --no-daemon \
    :artemis-mwa-clientlib-compat:testDebugUnitTest \
    :artemis-mwa-compat:testDebugUnitTest

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
