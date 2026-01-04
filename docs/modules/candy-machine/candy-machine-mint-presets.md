# Candy Machine Mint Presets (v60)

This module provides a **one-call mint preset** for Candy Machine v3 on mobile.

It composes three layers:

- v58: Candy Guard introspection + planning (accounts + args)
- v58: safe mint builder (human errors, invariant checks)
- v59: transaction composer preset (ATA creation + priority + resend/confirm)

No indexer. No paid services. No vendor lock-in.

## Install

Add the optional module:

- `:artemis-candy-machine-presets`

It depends on `:artemis-candy-machine` and `:artemis-tx-presets`.

## Mint in 15 lines

```kotlin
import com.selenus.artemis.candymachine.guards.GuardArgs
import com.selenus.artemis.candymachine.presets.CandyMachineMintPresets

// Recommended: mobile-first mint creation via createAccountWithSeed
val res = CandyMachineMintPresets.mintNewWithSeed(
  rpc = rpc,
  adapter = walletAdapter,
  candyGuard = candyGuardPubkey,
  candyMachine = candyMachinePubkey,
  seed = "my-app-mint-0001", // <= 32 bytes
  guardArgs = GuardArgs(
    // Provide allowlist proof, mintLimit id, etc when required by the machine.
  ),
  forcePnft = false
)

println("mint signature = ${res.signature}")
println("minted mint = ${res.mintedMint}")
```

## What it does

- Reads Candy Guard to detect requirements and fail early on unsupported guards
- Builds a `mint_v2` instruction using the v58 safe builder
- Ensures ATAs exist only when needed:
  - pNFT mint: NFT ATA
  - token payment: payment token ATA
- Sends with compute budget advice, priority fees, and resend/confirm for mobile networks

## Notes

- If a Candy Guard uses a guard that Artemis does not support yet, this preset fails early with a readable error.
- If your machine requires allowlist proofs or gatekeeper tokens, provide them via `GuardArgs`.
