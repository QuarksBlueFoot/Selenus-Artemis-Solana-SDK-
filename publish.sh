#!/bin/bash

# Credentials for Central Portal
# NOTE: These must be generated from https://central.sonatype.com/account -> Generate Token
# export CENTRAL_USERNAME="<your_token_username>"
# export CENTRAL_PASSWORD="<your_token_password>"

# Load from local.properties if available
if [ -f "local.properties" ]; then
    echo "Loading properties from local.properties..."
    while IFS='=' read -r key value; do
        if [[ $key == "CENTRAL_USERNAME" || $key == "CENTRAL_PASSWORD" || $key == "SIGNING_PASSWORD" ]]; then
            export "$key"="$value"
        fi
    done < local.properties
fi

# Auto-load Signing Key
if [ -z "$SIGNING_KEY" ] && [ -f "secret.asc" ]; then
    echo "Loading SIGNING_KEY from secret.asc..."
    export SIGNING_KEY="$(cat secret.asc)"
fi

if [ -z "$CENTRAL_USERNAME" ]; then
  echo "Error: CENTRAL_USERNAME is not set."
  exit 1
fi

if [ -z "$CENTRAL_PASSWORD" ]; then
  echo "Error: CENTRAL_PASSWORD is not set."
  exit 1
fi

if [ -z "$SIGNING_KEY" ]; then
  echo "Error: SIGNING_KEY is not set."
  exit 1
fi

if [ -z "$SIGNING_PASSWORD" ]; then
  echo "Error: SIGNING_PASSWORD is not set."
  exit 1
fi

echo "1. Cleaning and Building Staging Repository..."
rm -rf build
./gradlew clean publishMavenPublicationToStagingRepository -Pversion=1.0.2 --no-daemon -x javaDocReleaseGeneration || exit 1

echo "2. Zipping Bundle..."
cd build/staging-deploy
zip -r ../artemis-sdk.zip .
cd ../..

echo "3. Uploading to Central Portal..."
# Construct Base64 Auth Header
AUTH_STRING=$(echo -n "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" | base64 | tr -d '\n')

curl --request POST \
  --url 'https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC' \
  --header "Authorization: Bearer ${AUTH_STRING}" \
  --form bundle=@build/artemis-sdk.zip

echo "Done."
