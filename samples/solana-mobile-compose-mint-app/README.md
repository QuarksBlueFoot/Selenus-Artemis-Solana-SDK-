# Artemis: Solana Mobile Candy Machine Mint Sample (v64)

This is a tiny Android app that demonstrates a full **MWA → Candy Machine v3 mint** flow using Artemis:

 - `MwaWalletAdapter` (Artemis native MWA 2.x client)
 - `CandyMachineMintPresets.mintNewWithSeed` (plan + safe mint + priority fees + resend)

## Build

This sample is **not included** in the default repo build.

To include it, build with the property:

```bash
./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
```

## Run

1. Install an MWA-compatible wallet on the device.
2. Launch the sample app.
3. Enter:
   - RPC URL
   - Candy Machine pubkey
   - Candy Guard pubkey
   - optional guard group
   - seed (used to derive the mint pubkey via `createWithSeed`)
4. Tap **Connect Wallet**, then tap **Mint**.

The app displays the signature and derived mint address.

## Notes

This sample intentionally avoids any indexer or paid services. It relies on:

 - account reads (Candy Machine + Candy Guard)
 - Artemis planning + validation (v58)
 - Artemis transaction composing presets (v59)
