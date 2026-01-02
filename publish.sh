#!/bin/bash
set -euo pipefail

# =============================================================================
# Artemis SDK — Maven Central Publisher (Central Portal API)
# =============================================================================
# Publishes all modules to Maven Central via the Central Portal REST API.
# https://central.sonatype.com/api/v1/publisher/upload
#
# Prerequisites:
#   CENTRAL_USERNAME / CENTRAL_PASSWORD  — Central Portal token (generate at
#       https://central.sonatype.com/account -> Generate Token)
#   SIGNING_KEY    — Armored GPG private key (or supply secret.asc file)
#   SIGNING_PASSWORD — GPG key passphrase (empty string if no passphrase)
# =============================================================================

# ---- Load credentials from local.properties if available --------------------
if [ -f "local.properties" ]; then
    echo "Loading properties from local.properties..."
    while IFS='=' read -r key value; do
        key=$(echo "$key" | xargs)  # trim whitespace
        case "$key" in
            CENTRAL_USERNAME|CENTRAL_PASSWORD|SIGNING_PASSWORD)
                export "$key"="$value"
                ;;
        esac
    done < local.properties
fi

# ---- Load signing key -------------------------------------------------------
if [ -z "${SIGNING_KEY:-}" ] && [ -f "secret.asc" ]; then
    echo "Loading SIGNING_KEY from secret.asc..."
    export SIGNING_KEY
    SIGNING_KEY="$(cat secret.asc)"
fi

# ---- Validate ----------------------------------------------------------------
fail=0
for var in CENTRAL_USERNAME CENTRAL_PASSWORD; do
    if [ -z "${!var:-}" ]; then
        echo "Error: $var is not set." >&2
        fail=1
    fi
done
if [ -z "${SIGNING_KEY:-}" ]; then
    echo "Warning: SIGNING_KEY is not set — artifacts will NOT be signed."
    echo "         Maven Central requires signed artifacts. Continuing for staging only."
fi
[ "$fail" -ne 0 ] && exit 1

# ---- Read version -----------------------------------------------------------
VERSION=$(grep '^version=' gradle.properties | cut -d'=' -f2)
echo ""
echo "======================================================"
echo "  Publishing Artemis SDK v${VERSION} to Maven Central"
echo "======================================================"
echo ""

# ---- Step 1: Clean & build to local staging directory ------------------------
echo "1/4  Cleaning previous staging artifacts..."
rm -rf build/staging-deploy build/artemis-sdk.zip

echo "2/4  Building & publishing to local staging repository..."
GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.parallel=true"
export GRADLE_OPTS
./gradlew clean publishAllPublicationsToStagingRepository \
    -Pversion="$VERSION" \
    --no-daemon \
    -x javaDocReleaseGeneration \
    -x test \
    --stacktrace || {
        echo "ERROR: Gradle publish failed." >&2
        exit 1
    }

echo ""
echo "   Staged artifacts:"
find build/staging-deploy -name "*.pom" | while read -r pom; do
    dir=$(dirname "$pom")
    echo "   - $(basename "$dir")"
done

# ---- Step 2: Create bundle zip -----------------------------------------------
echo ""
echo "3/4  Creating upload bundle..."
(cd build/staging-deploy && zip -qr ../artemis-sdk.zip .)
SIZE=$(du -h build/artemis-sdk.zip | cut -f1)
echo "   Bundle: build/artemis-sdk.zip (${SIZE})"

# ---- Step 3: Upload to Central Portal API ------------------------------------
echo ""
echo "4/4  Uploading to Central Portal API..."

# Base64 auth
AUTH=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')

HTTP_CODE=$(curl --silent --output /tmp/central-response.json --write-out "%{http_code}" \
    --request POST \
    --url 'https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC' \
    --header "Authorization: Bearer ${AUTH}" \
    --form "bundle=@build/artemis-sdk.zip")

echo ""
if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
    DEPLOYMENT_ID=$(cat /tmp/central-response.json)
    echo "SUCCESS — Upload accepted by Central Portal."
    echo "   HTTP Status : ${HTTP_CODE}"
    echo "   Deployment  : ${DEPLOYMENT_ID}"
    echo ""
    echo "   Track status: https://central.sonatype.com/publishing/deployments"
    echo "   Direct link : https://central.sonatype.com/api/v1/publisher/status?id=${DEPLOYMENT_ID}"
    echo ""
    echo "   Central Portal will validate, sign-check, and publish automatically."
    echo "   Artifacts will appear on Maven Central within ~30 minutes."
else
    echo "FAILED — Central Portal returned HTTP ${HTTP_CODE}" >&2
    echo "   Response:" >&2
    cat /tmp/central-response.json >&2
    echo "" >&2
    exit 1
fi

echo ""
echo "Done."
