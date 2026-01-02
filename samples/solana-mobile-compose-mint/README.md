# Solana Mobile + Artemis Candy Machine mint (Compose walkthrough)

This sample shows how to use **Solana Mobile Wallet Adapter (MWA)** with Artemis to mint from a
Candy Machine v3 with Candy Guard, using the **v60 mint preset**.

It is intentionally small and focuses on the integration points:

1. Connect to a wallet using MWA (Android)
2. Read Candy Machine state to drive UI
3. Call a single safe mint preset

## Why this sample exists

Mobile teams hit the same two failure modes:

- Candy Guard requires remaining accounts/args that are easy to miss
- Transactions need priority fees + resend/confirm to feel reliable on mobile

Artemis solves both with:

- `CandyGuardMintV2Safe.buildSafe(...)` (v58)
- `TxComposerPresets.sendWithAtaAndPriority(...)` (v59)
- `CandyMachineMintPresets.mintWithPriorityAndResend(...)` (v60)

## Minimal Kotlin flow (pseudo UI)

```kotlin
val rpc = RpcApi("https://api.mainnet-beta.solana.com")

// 1) Get an adapter from MWA.
// Your app already has this from the MWA connection flow.
val adapter: WalletAdapter = mwaAdapter

// 2) Read state for UI.
val state = CandyMachineStateReader.read(
  rpc = rpc,
  candyMachine = candyMachinePubkey
)

if (state.isSoldOut) {
  showSoldOut()
  return
}

// 3) Mint safely.
val result = CandyMachineMintPresets.mintNewWithSeed(
  rpc = rpc,
  adapter = adapter,
  candyGuard = candyGuardPubkey,
  candyMachine = candyMachinePubkey,
  seed = "mint-${System.currentTimeMillis()}",
  sendConfig = SendPipeline.Config(),
  resendConfig = TxComposerPresets.ResendConfig(
    maxResends = 3,
    confirmTimeoutMs = 25_000
  )
)

toast("Minted: ${result.signature}")
```

## Notes

- This repo includes `artemis-wallet-mwa-android` which can serve as the reference implementation
  of a `WalletAdapter` backed by Solana Mobile Wallet Adapter.
- If Candy Guard enables a guard Artemis does not recognize, Artemis fails early with a
  human-readable error.
