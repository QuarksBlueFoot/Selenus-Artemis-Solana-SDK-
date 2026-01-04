# Artemis React Native SDK - Troubleshooting & MWA Fixes

This document outlines common issues with the Mobile Wallet Adapter (MWA) on Android and how the Artemis SDK addresses them.

## 1. Concurrency Issues (Fixed)

**Issue:** The official MWA client library can throw exceptions if multiple requests (e.g., `connect` followed immediately by `signTransaction`) are made simultaneously or if a previous session hasn't fully closed.

**Fix:** The Artemis SDK implements a `Mutex` (Mutual Exclusion) lock in the native Kotlin layer (`ArtemisModule.kt`). This ensures that all MWA operations are serialized. If you call `connect()` and then `signTransaction()`, the second call will wait until the first one completes or fails.

## 2. Transaction vs. Message Signing (Fixed)

**Issue:** Some wallets treat `signTransaction` and `signMessage` interchangeably, or the SDK fails to distinguish them correctly, leading to "Invalid Transaction" errors when signing arbitrary messages.

**Fix:** The Artemis SDK explicitly separates these paths:
- `signTransaction`: Decodes the input as a Solana Transaction and uses the MWA `signTransactions` endpoint.
- `signMessage`: Uses the MWA `signMessages` endpoint for arbitrary data.

## 3. Build Errors: "SDK location not found"

**Issue:** When building the Android project, Gradle may fail if it cannot find the Android SDK.

**Fix:**
1. Ensure the Android SDK is installed.
2. Create a `local.properties` file in the root of your project (or `android/` folder) with:
   ```properties
   sdk.dir=/path/to/your/android-sdk
   ```
   (e.g., `/Users/username/Library/Android/sdk` on macOS or `/home/username/Android/Sdk` on Linux).

## 4. React Native "Plugin" Usage

To use the Artemis SDK as a standard Wallet Adapter plugin:

```typescript
import { MobileWalletAdapter } from 'artemis-solana-sdk/MobileWalletAdapter';

const wallet = new MobileWalletAdapter({
    identityUri: 'https://myapp.com',
    iconPath: 'relative/path/to/icon.png',
    identityName: 'My App',
    chain: 'solana:mainnet'
});

await wallet.connect();
const signedTx = await wallet.signTransaction(tx);
```

## 5. Missing Dependencies

**Issue:** Runtime crashes due to missing native libraries.

**Fix:** The `artemis-react-native` module automatically includes all necessary Artemis sub-modules (`artemis-core`, `artemis-rpc`, `artemis-depin`, etc.). Ensure your `android/build.gradle` includes the Artemis Maven repository if you are consuming it as a library.
