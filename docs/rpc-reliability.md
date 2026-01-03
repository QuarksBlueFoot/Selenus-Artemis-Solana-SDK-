# RPC reliability

Artemis RPC includes retry and backoff knobs for mobile networks.

## Config

```kotlin
val client = JsonRpcClient(
  endpoint = rpcUrl,
  config = RpcClientConfig(
    maxAttempts = 4,
    baseBackoffMs = 150,
    maxBackoffMs = 2500
  )
)
val api = RpcApi(client)
```

## Backoff

Default is exponential backoff with jitter. You can supply your own BackoffStrategy.
