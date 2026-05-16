# MWA compat migration sample

This opt-in Android sample compiles three migration styles in one app:

- Upstream-import compatibility through `com.solana.mobilewalletadapter.clientlib.*` backed by `artemis-mwa-compat`.
- Native Artemis MWA through `com.selenus.artemis.wallet.mwa.MwaWalletAdapter`.
- Mixed transaction construction through Artemis `Pubkey` plus `web3-solana` package-compatible builders.

Build it with:

```bash
./gradlew -PenableAndroidSamples=true :samples:mwa-compat-migration:assembleDebug
```

Run it on an Android device or emulator with an MWA wallet installed. Live wallet behavior remains wallet/device-gated; the build gate proves source migration and app wiring compile against Artemis.