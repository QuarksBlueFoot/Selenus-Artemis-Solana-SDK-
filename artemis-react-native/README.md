# Artemis Solana SDK for React Native

A modular, mobile-first Solana SDK for React Native, powered by the Artemis Kotlin SDK on Android.

## Features

- **Mobile Wallet Adapter (MWA)**: Seamless integration with Solana Mobile Stack (SMS) on Android.
- **High Performance**: Uses native Kotlin modules for heavy lifting.
- **Modular**: Only pay for what you use.

## Installation

```bash
npm install artemis-solana-sdk @solana/web3.js @solana/wallet-adapter-base buffer
```

### Android Setup

1. Open `android/build.gradle` and ensure you have `mavenCentral()` in your repositories.

2. In `android/app/build.gradle`, add the Artemis dependencies you need (if not automatically linked):

```gradle
dependencies {
    implementation project(':artemis-react-native')
}
```

## Usage

### Mobile Wallet Adapter

```typescript
import { MobileWalletAdapter } from 'artemis-solana-sdk';
import { Connection, Transaction } from '@solana/web3.js';

const adapter = new MobileWalletAdapter({
    identityUri: 'https://myapp.com',
    iconPath: 'https://myapp.com/icon.png',
    identityName: 'My App',
});

// Connect
await adapter.connect();

// Sign and Send Transaction
const connection = new Connection('https://api.devnet.solana.com');
const tx = new Transaction().add(...);
const signature = await adapter.sendTransaction(tx, connection);
```

## Troubleshooting

See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common issues.
