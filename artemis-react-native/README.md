# Artemis Solana SDK for React Native

A modular, mobile-first Solana SDK for React Native, powered by the Artemis Kotlin SDK on Android.

## Features

- **Mobile Wallet Adapter (MWA)**: Seamless integration with Solana Mobile Stack (SMS) on Android.
- **SignIn With Solana (SIWS)**: Authenticaton using the MWA 2.0 standardized flow.
- **Seed Vault Access**: (For Wallet Apps) Direct management of keys in the secure system Seed Vault.
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

### Mobile Wallet Adapter with SIWS (Connect + Sign)

```typescript
import Artemis from 'artemis-solana-sdk';

// One-step Connect and Authenticate
const result = await Artemis.connectWithSignIn({
    domain: 'myapp.com',
    uri: 'https://myapp.com/login',
    statement: 'Login to My App',
    chainId: 'solana:mainnet'
});

console.log('User Address:', result.address);
console.log('Signature:', result.signature);
```

### Mobile Wallet Adapter (Standard)

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

### Building a Wallet App? (Seed Vault)

Artemis allows you to build a full Wallet App in React Native that manages keys securely using the system Seed Vault.

```typescript
import Artemis from 'artemis-solana-sdk';

// 1. Authorize usage
const auth = await Artemis.seedVaultAuthorize('sign_transaction');
const token = auth.authToken;

// 2. Import a seed
await Artemis.seedVaultImportSeed('sign_transaction');

// 3. Get Accounts
const accounts = await Artemis.seedVaultGetAccounts(token);

// 4. Sign Transaction
const signatures = await Artemis.seedVaultSignMessages(token, [base64Tx]);
```

## Troubleshooting

See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common issues.
