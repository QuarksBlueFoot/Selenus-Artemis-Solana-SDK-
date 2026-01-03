# Contributing to Selenus Artemis

We keep Artemis **modular, optional, and mobile-first**. No bloat.

## Repo build

This repo includes Android library modules (for Solana Mobile / MWA), so CI installs the Android SDK.

Run a full build:

```bash
./gradlew --no-daemon clean build
```

## Android sample app (v64)

The sample app is intentionally **excluded** from the default build. Enable it explicitly:

```bash
./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
```

## The Rules (Don't Break These)

 - No paid-service assumptions
 - Modules remain drop-in/optional
 - Public APIs are documented and stable
