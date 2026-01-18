# Artemis Solana SDK for React Native

A modular, mobile-first Solana SDK for React Native, powered by the Artemis Kotlin SDK on Android and native Swift on iOS.

## Features

- **Cross-Platform Base58**: Native Base58 encoding/decoding on both iOS and Android.
- **Mobile Wallet Adapter (MWA)**: Seamless integration with Solana Mobile Stack (SMS) on Android.
- **SignIn With Solana (SIWS)**: Authentication using the MWA 2.0 standardized flow.
- **Seed Vault Access**: (For Wallet Apps) Direct management of keys in the secure system Seed Vault.
- **Ed25519 Crypto**: Native key generation and signing on both platforms.
- **High Performance**: Uses native modules (Kotlin/Swift) for heavy lifting.
- **Modular**: Only pay for what you use.

## Platform Support

| Feature | iOS | Android |
|---------|-----|---------|
| Base58 Encode/Decode | ✅ | ✅ |
| Base58Check | ✅ | ✅ |
| Ed25519 Keypair | ✅ | ✅ |
| Ed25519 Signing | ✅ | ✅ |
| SHA256 | ✅ | ✅ |
| Mobile Wallet Adapter | ❌ | ✅ |
| Seed Vault | ❌ | ✅ |
| DePIN Proofs | ✅ | ✅ |
| Solana Pay | ✅ | ✅ |

> **Note**: Mobile Wallet Adapter (MWA) is Android-only as it's part of the Solana Mobile Stack. 
> For iOS wallet connections, use WalletConnect or other iOS-compatible protocols.

## Installation

\`\`\`bash
npm install artemis-solana-sdk @solana/web3.js @solana/wallet-adapter-base buffer
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
