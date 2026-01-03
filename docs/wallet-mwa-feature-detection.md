# MWA feature detection and fallback routing

Artemis' native MWA adapter calls `get_capabilities` and routes automatically:

- If wallet supports `sign_and_send_transactions`, Artemis uses it and returns signatures.
- Otherwise Artemis falls back to `sign_transactions`, and you can broadcast with Artemis RPC.

## Recommended flow for most apps

Sign-only gives you full control over RPC routing and resend logic:

```kotlin
val signed = adapter.signMessages(listOf(txBytes))
val sig = rpc.sendAndConfirmRawTransaction(signed.first())
```

## One-liner helper

If you want "wallet broadcast when available, otherwise RPC":

```kotlin
val sigs = adapter.signThenSendViaRpc(
  rpcSend = { bytes -> rpc.sendRawTransaction(bytes) },
  transactions = listOf(txBytes)
)
```
