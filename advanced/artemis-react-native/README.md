# Artemis Solana SDK for React Native

A modular, mobile-first Solana SDK for React Native. Powered by the Artemis Kotlin SDK on Android and native Swift on iOS.

Parity with the Solana Mobile Stack where the platform allows: MWA 2.0, Seed Vault on Android, plus native Base58 and Ed25519 on both platforms.

## Why Artemis for React Native?

| Feature | @solana/web3.js | Solana Mobile RN | Artemis |
| --- | --- | --- | --- |
| Base58 (native) | JS polyfill | partial | native Kotlin and Swift |
| Ed25519 (native) | JS polyfill | partial | native |
| MWA 2.0 | no | yes | parity |
| Seed Vault | no | yes | parity |
| iOS support | yes | no | yes |
| DePIN proofs | no | no | yes |
| Solana Pay | partial | no | native |

## Features

- **Cross-platform Base58**: native Base58 encoding and decoding on both iOS and Android.
- **Mobile Wallet Adapter**: direct integration with the Solana Mobile Stack on Android.
- **Sign-In With Solana**: authentication through the MWA 2.0 SIWS flow.
- **Seed Vault access**: for wallet apps, direct management of keys in the system Seed Vault on Saga.
- **Ed25519 crypto**: native key generation and signing on both platforms.
- **High performance**: heavy lifting runs in native Kotlin and Swift.
- **Modular**: pay only for what you import.

## Platform support

| Feature | iOS | Android |
| --- | --- | --- |
| Base58 encode and decode | yes | yes |
| Base58Check | yes | yes |
| Ed25519 keypair | yes | yes |
| Ed25519 signing | yes | yes |
| SHA-256 | yes | yes |
| Mobile Wallet Adapter | no | yes |
| Seed Vault | no | yes |
| DePIN proofs | yes | yes |
| Solana Pay | yes | yes |

Mobile Wallet Adapter is Android only because it is part of the Solana Mobile Stack. For iOS wallet connections, use WalletConnect or another iOS-compatible protocol.

## Installation

\`\`\`bash
npm install @selenus/artemis-solana-sdk @solana/web3.js @solana/wallet-adapter-base buffer
\`\`\`

### Android Setup

1. Open \`android/build.gradle\` and ensure you have \`mavenCentral()\` in your repositories.

2. In \`android/app/build.gradle\`, add the Artemis dependencies:

\`\`\`gradle
dependencies {
    implementation project(':artemis-react-native')
}
\`\`\`

### iOS Setup

1. Add to your \`Podfile\`:

\`\`\`ruby
pod 'artemis-solana-sdk', :path => '../node_modules/artemis-solana-sdk/ios'
\`\`\`

2. Run \`pod install\` in your \`ios\` directory.

## Usage

### Cross-Platform Base58 Utilities

\`\`\`typescript
import { Base58, Crypto } from 'artemis-solana-sdk';

// Encode bytes to Base58
const encoded = await Base58.encode(myBytes);

// Decode Base58 to bytes
const decoded = await Base58.decode('abc123...');

// Validate Solana addresses
const isValidPubkey = await Base58.isValidPubkey('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA');
const isValidSig = await Base58.isValidSignature(signatureString);

// Convert between encodings
const base64 = await Base58.toBase64(base58String);
const base58 = await Base58.fromBase64(base64String);

// Generate keypair (Ed25519)
const { publicKey, secretKey } = await Crypto.generateKeypair();
\`\`\`

### Mobile Wallet Adapter with SIWS (Android)

\`\`\`typescript
import Artemis from 'artemis-solana-sdk';

const result = await Artemis.connectWithSignIn({
    domain: 'myapp.com',
    uri: 'https://myapp.com/login',
    statement: 'Login to My App',
    chainId: 'solana:mainnet'
});

console.log('User Address:', result.address);
console.log('Signature:', result.signature);
\`\`\`

### Platform Detection

\`\`\`typescript
import { ArtemisPlatform } from 'artemis-solana-sdk';

if (ArtemisPlatform.hasMWA) {
  // Use Mobile Wallet Adapter (Android)
} else {
  // Use WalletConnect or other iOS-compatible solution
}
\`\`\`

## Troubleshooting

See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common issues.
