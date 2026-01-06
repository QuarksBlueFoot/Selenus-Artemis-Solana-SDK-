# Artemis Mobile Wallet Adapter (MWA)

The `artemis-wallet-mwa-android` module implements the [Mobile Wallet Adapter](https://github.com/solana-mobile/mobile-wallet-adapter) specification natively. It allows Android wallets to expose their signing capabilities to dApps via local IPC.

## Features

- **Pure Kotlin:** A rewrite from first principles ensuring clean architecture.
- **Standards Compliant:** Fully implements the 2.0 specs.
- **Optimized for Artemis:** Integrates directly with `artemis-core` data structures.

## Installation

```kotlin
dependencies {
    implementation("com.selenus.artemis:artemis-wallet-mwa-android:1.0.4")
}
```

## Wallet Implementation

To make your wallet discoverable by dApps:

### 1. Define Capability

Ensure your wallet Activity can handle the `solana-wallet://` scheme.

### 2. Handle Connection

When a dApp connects, the `MobileWalletAdapter` service (from this library) handles the handshake and session encryption.

```kotlin
// Example usage in a Service or ViewModel
class MyWalletService : Service() {
    private val mwa = MobileWalletAdapter()

    override fun onBind(intent: Intent): IBinder {
        return mwa.binder
    }
}
```
