# @selenus/artemis-solana-sdk (React Native bindings)

React Native bindings for the Artemis Solana SDK. Android-first Mobile
Wallet Adapter 2.0 + Seed Vault, realtime WebSocket subscriptions,
expanded JSON-RPC, compressed-NFT DAS queries, compute-budget and
PDA/ATA helpers, and cross-platform Base58 + Ed25519 + SHA-256
primitives that work on both Android and iOS.

## Platform support

| Capability | Android | iOS |
| --- | --- | --- |
| Mobile Wallet Adapter 2.0 (connect, SIWS, sign + send, batch) | yes | not available on iOS (platform restriction) |
| Seed Vault (Saga / Seeker wallet apps) | yes | not applicable |
| Realtime WebSocket (`subscribeAccount`, `subscribeSignature`, state events) | yes | via `@solana/web3.js` Connection |
| JSON-RPC helpers (`getAccountInfo`, `getTokenAccountsByOwner`, `simulateTransaction`, ...) | yes | via `@solana/web3.js` |
| DAS queries (`dasAssetsByOwner`, `dasAsset`, `dasAssetsByCollection`) | yes | via REST |
| Compute budget instructions (`SetUnitLimit`, `SetUnitPrice`) | yes | via `@solana/web3.js` |
| PDA (`findProgramAddress`) / ATA (`getAssociatedTokenAddress`) | yes | via `@solana/web3.js` |
| Base58 / Base58Check utilities | yes | yes |
| Ed25519 keygen / sign / verify | yes | yes |
| SHA-256 | yes | yes |
| Solana Pay URI parse and build | yes (native) | via JS |

Mobile Wallet Adapter is an Android-only contract defined by Solana
Mobile. On iOS, `MobileWalletAdapter.readyState` is
`WalletReadyState.Unsupported` so wallet-adapter UIs hide the entry
point; pair it with WalletConnect or an iOS-compatible alternative.

## Installation

```bash
npm install @selenus/artemis-solana-sdk @solana/web3.js @solana/wallet-adapter-base buffer
```

### Dev build requirement

This package links against native Kotlin/Swift code. It will not load
in Expo Go. Your app needs a custom dev build. Pick the path that
matches your setup:

- Bare React Native: `npx react-native run-android` / `npx react-native run-ios`.
- Expo with config plugins: `npx expo prebuild` followed by
  `npx expo run:android` or `npx expo run:ios`.
- EAS Build: add this package to `package.json`, then run
  `eas build --profile development --platform android` so the native
  module is bundled into your dev client.

There is no pure-JS fallback: Mobile Wallet Adapter, Seed Vault, and
the native Base58 / Ed25519 primitives all reach through the native
bridge by design. The JS-only surface you can use without a native
build is limited to type declarations and pure helpers from
`@solana/web3.js`.

### Android

Autolinking detects the module in `node_modules/@selenus/artemis-solana-sdk/android`
and pulls in the Android artifacts it depends on. Gradle dependencies
are pinned to the same version as the npm package (one source of
truth). If you build against a local SDK checkout, set
`-PartemisVersion=<version>`.

### iOS

The iOS side exposes the crypto utility layer only. Add to your Podfile:

```ruby
pod 'ArtemisSolanaSDK', :path => '../node_modules/@selenus/artemis-solana-sdk/ios'
```

Then run `pod install` in your `ios` directory.

## Usage

### Mobile Wallet Adapter 2.0 (Android)

```typescript
import {
  MobileWalletAdapter,
  MWA_FEATURES,
  transact,
} from '@selenus/artemis-solana-sdk';

const wallet = new MobileWalletAdapter({
  identityUri: 'https://myapp.example.com',
  iconPath: 'https://myapp.example.com/favicon.ico',
  identityName: 'My App',
  chain: 'solana:mainnet',
});

await wallet.connect();
console.log(wallet.publicKey?.toBase58());
console.log(wallet.accounts);
console.log(wallet.capabilities);
```

### `transact` block (drop-in parity with `@solana-mobile/mobile-wallet-adapter-protocol-mobile`)

```typescript
const signature = await transact(wallet, async (w) => {
  const result = await w.signAndSendTransaction(tx, { commitment: 'confirmed' });
  if (result.isSuccess) return result.signature;
  throw new Error(result.error ?? 'unknown wallet error');
});
```

`transact` handles session open + teardown around your block. The
wallet is automatically deauthorized when the block resolves or
throws.

### Sign-In With Solana

```typescript
const auth = await wallet.connectWithSignIn({
  domain: 'myapp.example.com',
  uri: 'https://myapp.example.com/login',
  statement: 'Sign in to My App',
  chainId: 'solana:mainnet',
});
console.log(auth.signInResult?.address, auth.signInResult?.signature);
```

### Sign and send a batch (per-slot results)

```typescript
const batch = await wallet.signAndSendTransactions(
  [tx1, tx2, tx3],
  { commitment: 'confirmed', waitForCommitmentToSendNextTransaction: true },
);
batch.results.forEach((slot) => {
  if (slot.isSuccess) console.log(`slot ${slot.index}: ${slot.signature}`);
  else if (slot.isFailure) console.warn(`slot ${slot.index} failed: ${slot.error}`);
  else if (slot.isSignedButNotBroadcast) {
    // Wallet signed but did not broadcast. Submit slot.signedRaw via
    // your own RPC to obtain a signature.
  }
});
```

### Cross-platform Base58 / Ed25519 utilities

```typescript
import { Base58, Crypto } from '@selenus/artemis-solana-sdk';

const encoded = await Base58.encode(myBytes);
const decoded = await Base58.decode('abc123...');
const isValid = await Base58.isValidPubkey('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA');

const { publicKey, secretKey } = await Crypto.generateKeypair();
const signature = await Crypto.sign(message, secretKey);
const ok = await Crypto.verify(signature, message, publicKey);
```

### Seed Vault (Android wallet apps)

```typescript
import Artemis from '@selenus/artemis-solana-sdk';

const auth = await Artemis.seedVaultAuthorize('sign_transaction');
const accounts = await Artemis.seedVaultGetAccounts(auth.authToken);
const signatures = await Artemis.seedVaultSignTransactions(
  auth.authToken,
  [base64Tx1, base64Tx2],
);
```

Seed Vault is available only on devices that ship the system service
(Saga, Seeker, and some emulator images). Calls on devices without
Seed Vault fail fast with a typed error.

### Realtime WebSocket subscriptions

```typescript
import Artemis, { Realtime } from '@selenus/artemis-solana-sdk';

Artemis.setWsUrl('wss://mainnet.helius-rpc.com/?api-key=YOUR_KEY');
await Realtime.connect();

const stateSub = Realtime.onState((event) => {
  // event.kind is 'Idle' | 'Connecting' | 'Connected' | 'Reconnecting' | 'Closed'
  console.log(event.kind, 'epoch=', event.epoch, 'endpoint=', event.endpoint);
});

const acctSub = await Realtime.onAccountChanged(
  'Vote111111111111111111111111111111111111111',
  (n) => console.log('lamports=', n.lamports, 'slot=', n.slot),
  'confirmed',
);

const sigSub = await Realtime.onSignatureConfirmed(txSignature, (n) =>
  console.log('confirmed=', n.confirmed),
);

// ...later
sigSub.remove();
acctSub.remove();
stateSub.remove();
await Realtime.close();
```

Every state transition carries a monotonic `epoch` counter so
collectors can distinguish a fresh connect from a reconnect that
landed back on `Connected`.

### Expanded JSON-RPC

```typescript
const accountInfo = await Artemis.getAccountInfo(pubkey, 'confirmed', 'base64');
const multi       = await Artemis.getMultipleAccounts([pk1, pk2, pk3], 'confirmed');
const tokens      = await Artemis.getTokenAccountsByOwner(owner, null, null, 'confirmed');
const sim         = await Artemis.simulateTransaction(base64Tx, false, true, 'processed');
const statuses    = await Artemis.getSignatureStatuses([sig1, sig2], true);
const slot        = await Artemis.getSlot('confirmed');
const rent        = await Artemis.getMinimumBalanceForRentExemption(165, 'confirmed');
```

Every method delegates to `artemis-rpc` on the native side; the JS
layer gets a plain object back, no JSON string to reparse.

### DAS queries for compressed NFTs

```typescript
Artemis.setDasUrl('https://mainnet.helius-rpc.com/?api-key=YOUR_KEY');

const page  = await Artemis.dasAssetsByOwner(ownerPubkey, 1, 100);
const one   = await Artemis.dasAsset('GdR7...');
const coll  = await Artemis.dasAssetsByCollection(collectionMint);
```

Backed by `CompositeDas` → `HeliusDas` with `RpcFallbackDas`. When the
DAS URL is not set, the fallback synthesizes the same `DigitalAsset`
view from `getTokenAccountsByOwner` + Metaplex metadata.

### Compute budget, PDA, and ATA helpers

```typescript
const limitIx = await Artemis.computeBudgetSetUnitLimit(200_000);
const priceIx = await Artemis.computeBudgetSetUnitPrice(5_000n);

const { address, bump } = await Artemis.findProgramAddress(
  [Buffer.from('vault').toString('base64'), Buffer.from(owner).toString('base64')],
  programId,
);

const ata = await Artemis.getAssociatedTokenAddress(owner, mint);
```

Instruction shape is `{ programId, accounts: [{ pubkey, isSigner, isWritable }], data }`
with `data` as base64. Fold into a `@solana/web3.js`
`TransactionInstruction` by mapping `accounts` to `keys` and
base64-decoding `data` into a `Buffer`. ATA returns a base58 string
directly.

### Platform detection

```typescript
import { ArtemisPlatform } from '@selenus/artemis-solana-sdk';

if (ArtemisPlatform.hasMWA) {
  // Use Mobile Wallet Adapter on Android.
} else {
  // Fall back to WalletConnect or another iOS-compatible protocol.
}
```

## Bridge contract

Every method returns a structured JS object. No JSON stringification
across the native bridge. Shapes are documented in
[`index.d.ts`](./index.d.ts) and enforced by the TypeScript exports
in [`MobileWalletAdapter.ts`](./MobileWalletAdapter.ts).

Seed Vault auth tokens are opaque strings end to end: the native
bridge accepts strings, the TS types declare strings, and the upstream
Seed Vault contract treats the token as a string identifier.

## Troubleshooting

See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common issues.

## License

Apache License 2.0. See [LICENSE](../../LICENSE) in the repository root.
