# artemis-ws

Wallet grade Solana WebSocket subscriptions for Kotlin.

## Features

- Auto reconnect with jittered backoff
- Deterministic resubscribe ordering
- De-dupe subscriptions by logical key
- Subscription bundling to reduce request spam during startup
- Kotlin Flow event stream
- Intent presets for common app patterns
- Optional HTTP fallback polling while disconnected

## Usage

```kotlin
val ws = SolanaWsClient("wss://your-rpc")
ws.connect()

val handle = ws.accountSubscribe(pubkey)

scope.launch {
  ws.events.collect { ev ->
    if (ev is WsEvent.Notification) {
      println(ev.key)
      println(ev.result)
    }
  }
}

handle.close()
ws.close()
```

## HTTP fallback

Fallback is optional. It lets you keep a live UI while WS reconnects.
You provide a poller that emits synthetic events.

```kotlin
val ws = SolanaWsClient(
  url = "wss://your-rpc",
  fallback = object : SolanaWsClient.WsFallback {
    override suspend fun poll(activeKeys: List<String>, emit: suspend (WsEvent) -> Unit) {
      // Poll what you care about and emit WsEvent.Notification
    }
  }
)
```


## Backpressure and sampling

Game clients often subscribe to noisy streams. When notifications come in faster than your UI can render,
this module can sample non critical subscriptions to the latest value within a time window.

Critical keys are emitted best-effort by default:
- sig:
- acct:

You can tune the policy at runtime:

```kotlin
ws.setNotificationPolicy(
  NotificationPolicy(
    sampleWindowMs = 200,
    criticalKeyPrefixes = setOf("sig:", "acct:", "logs:")
  )
)
```

Sampled notifications set `isSampled = true`.
Backpressure events are emitted when drops are detected.
