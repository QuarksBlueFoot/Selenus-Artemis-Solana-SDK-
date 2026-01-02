# Transaction composer presets

This document describes **artemis-tx-presets**, an optional module that helps mobile apps and games
compose and send transactions safely.

Goals:

- reduce client glue code
- keep everything RPC-only (no indexer)
- handle flaky mobile networks with resend

## What you get

`TxComposerPresets.sendWithAtaAndPriority(...)` composes a transaction that can:

- create missing ATAs before your program instruction
- insert compute budget instructions using Artemis `SendPipeline`
- send and confirm, with a small resend loop

No vendor services. No magic.

## Mint style preset in 15 lines

```kotlin
val res = TxComposerPresets.sendWithAtaAndPriority(
  rpc = rpc,
  adapter = wallet,
  instructions = listOf(mintIx),
  ataIntents = listOf(
    TxComposerPresets.AtaIntent(owner = wallet.publicKey, mint = paymentMint)
  ),
  sendConfig = SendPipeline.Config(desiredPriority0to100 = 45),
  resendConfig = TxComposerPresets.ResendConfig(maxResends = 2)
)

println("mint signature: ${res.signature}")
```

## Design notes

- ATA creation is added only when the ATA account is missing.
- Priority fees and compute unit limit are injected by `SendPipeline`.
- Resend uses the same signed transaction bytes and re-broadcasts if confirmation polling times out.

## When not to use this

- If your wallet can sign-and-send with its own routing, you may prefer that.
- If you require v0 + ALTs, use `artemis-vtx` tools and compile a v0 message.
