# artemis-streaming

Zero-copy field access over live account updates. Reads into pooled direct `ByteBuffer`s, surfaces a `ZeroCopyAccessor` per notification so callers can pull out the fields they care about without deserializing the whole account, and keeps a ring-buffer of snapshots for diff/history.

Source: [../../advanced/artemis-streaming/](../../advanced/artemis-streaming/). Public entry point is `ZeroCopyAccountStream`.

## Why this module

Mobile apps that follow many token balances, a whirlpool, a set of candy-machine accounts, etc. can't afford to deserialize the full account into Kotlin objects on every push. `ZeroCopyAccountStream` pools direct buffers, writes each update into one, and runs your callback with a lightweight accessor that reads fields straight out of the buffer by schema offset.

If you just want a single-account subscription with automatic reconnect and a typed `ConnectionState`, reach for [`RealtimeEngine`](../modules/rpc/rpc-reliability.md) in `artemis-ws`. Use this module when memory pressure or update throughput is the concern.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-streaming:2.3.0")
}
```

## Plug in a transport

The stream does not open sockets itself. You supply a `WebSocketClient` with two methods: `subscribeToAccount(account, onData)` and `unsubscribe(account)`. Wire it to `artemis-ws` (or any other transport) yourself:

```kotlin
import com.selenus.artemis.streaming.WebSocketClient
import com.selenus.artemis.ws.RealtimeEngine

class ArtemisWsBridge(private val realtime: RealtimeEngine) : WebSocketClient {

    private val handles = mutableMapOf<String, SubscriptionHandle>()

    override suspend fun subscribeToAccount(
        account: String,
        onData: (ByteArray) -> Unit
    ) {
        val handle = realtime.subscribeAccount(account) { notification ->
            // notification.data is base64; decode once and hand off the bytes
            notification.data?.let { onData(PlatformBase64.decode(it)) }
        }
        handles[account] = handle
    }

    override suspend fun unsubscribe(account: String) {
        handles.remove(account)?.close()
    }
}
```

The transport contract is deliberately small so you can also back it with Helius enhanced websockets, LaserStream, a file replay for tests, or anything else that yields raw account bytes.

## Create the stream

```kotlin
import com.selenus.artemis.streaming.ZeroCopyAccountStream
import com.selenus.artemis.streaming.StreamConfig

val stream = ZeroCopyAccountStream.create(
    wsClient = ArtemisWsBridge(artemis.realtime),
    config   = StreamConfig(
        poolSize       = 64,     // direct-buffer pool capacity
        bufferSize     = 4096,   // per-buffer size in bytes
        historySize    = 10,     // snapshots kept per account for diff/history
        flowBufferSize = 16      // back-pressure buffer for `accountFlow`
    )
)
```

Direct buffers are allocated lazily up to `poolSize`. If every buffer is checked out, new subscriptions block on `pool.receive()` rather than allocating unbounded memory.

## Define a schema

The stream reads fields by offset, not by parsing. Describe the account layout with `AccountSchema` and `FieldDef`:

```kotlin
import com.selenus.artemis.streaming.AccountSchema
import com.selenus.artemis.streaming.FieldDef
import com.selenus.artemis.streaming.FieldType

val myState = AccountSchema(
    name = "MyState",
    size = 96,
    fields = listOf(
        FieldDef("version",  FieldType.U8,     0),
        FieldDef("authority", FieldType.PUBKEY, 1),
        FieldDef("counter",  FieldType.U64,    33),
        FieldDef("enabled",  FieldType.BOOL,   41)
    ),
    // Only these fields trigger a change event; others are read-but-not-watched
    monitoredFields = listOf(
        FieldDef("counter",  FieldType.U64,    33)
    )
)
```

`FieldType` supports `U8`, `U16`, `U32`, `U64`, `I64`, `BOOL`, `PUBKEY`, and `BYTES`. `FieldDef.size` defaults to the type's natural size.

Common SPL account layouts are shipped preconfigured in `CommonSchemas`:

```kotlin
import com.selenus.artemis.streaming.CommonSchemas

CommonSchemas.TokenAccount   // 165 bytes, monitors `amount` and `delegatedAmount`
CommonSchemas.MintAccount    // 82 bytes,  monitors `supply`
CommonSchemas.StakeAccount   // partial; see source for exact offsets
```

## Subscribe with a callback

The callback runs every time a monitored field changes. The accessor is live only for that callback invocation; if you want to hold onto the bytes, copy them out.

```kotlin
val sub = stream.subscribe(
    account = tokenAccountPubkey.toBase58(),
    schema  = CommonSchemas.TokenAccount
) { accessor ->
    val amount = accessor.getU64("amount")
    val owner  = accessor.getPubkey("owner")
    updateUi(owner, amount)
}

// Later, when you're done
sub.unsubscribe()
```

`sub` is a `Subscription` with `unsubscribe()` and `isActive: Boolean`.

## Subscribe as a Flow

```kotlin
stream.accountFlow(tokenAccountPubkey.toBase58(), CommonSchemas.TokenAccount)
    .map { it.getU64("amount") }
    .distinctUntilChanged()
    .onEach { balance -> balanceState.value = balance }
    .launchIn(scope)
```

`accountFlow` wraps the callback form in a `callbackFlow` backed by a channel sized at `flowBufferSize`. When the flow is cancelled, the underlying subscription is closed via `awaitClose`.

## Watch many accounts

`subscribeBatch` subscribes to N `(account, schema)` pairs and calls one callback with the account that changed:

```kotlin
val batch = stream.subscribeBatch(
    accounts = listOf(
        myMintA to CommonSchemas.MintAccount,
        myMintB to CommonSchemas.MintAccount,
        myAtaA  to CommonSchemas.TokenAccount
    )
) { account, accessor ->
    when (account) {
        myMintA, myMintB -> onMintChanged(account, accessor.getU64("supply"))
        myAtaA           -> onBalance(accessor.getU64("amount"))
    }
}

// Tear down all of them at once
batch.unsubscribeAll()
```

## One-off field reads

Sometimes you already have a subscription but want to read a field on demand without waiting for the next update. `getField` reads from the latest buffer:

```kotlin
val latestBalance = stream.getField(tokenAcctBase58, "amount") {
    it.getU64("amount")
}
```

Returns `null` if the account has no active subscription or has not received its first update yet.

## History and diff

Each subscription keeps a ring-buffer of `AccountSnapshot(data, timestamp, slot)` sized by `StreamConfig.historySize`. Pull them out for audit trails or to spot non-monitored field changes:

```kotlin
stream.getHistory(tokenAcctBase58).forEach { snap ->
    println("at slot ${snap.slot} / t=${snap.timestamp} (${snap.data.size} bytes)")
}
```

Compare two states field-by-field against a schema:

```kotlin
val changes: List<FieldChange> = stream.diffStates(
    oldState = oldBuffer,
    newState = newBuffer,
    schema   = CommonSchemas.TokenAccount
)
changes.forEach { c ->
    println("${c.fieldName}: ${c.oldValue} -> ${c.newValue}")
}
```

`FieldChange(fieldName, oldValue, newValue, fieldType)` is produced for every field whose decoded value differs between the two buffers.

## Accessors in detail

`ZeroCopyAccessor` is the accessor interface handed to every callback:

`getU8(name): Int`, `getU16(name): Int`, `getU32(name): Long`, `getU64(name): Long`, `getI64(name): Long`, `getBool(name): Boolean`, `getPubkey(name): String` (base58 encoded), `getBytes(name, length): ByteArray`, and `getRaw(): ByteBuffer` (a `duplicate` of the backing buffer if you need wider access). Two convenience accessors default to SPL token semantics: `getBalance()` returns `getU64("amount")` and `getOwner()` returns `getPubkey("owner")`.

All numeric reads are little-endian. Unknown field names throw `IllegalArgumentException` so typos fail loud at runtime.

## Cleanup

```kotlin
stream.close()
```

Cancels the internal supervisor job, drops all subscriptions, and releases the buffer pool. After `close()`, the instance is no longer usable.

## Status

Listed as `Experimental` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). The API is stable enough to build on, but the surface may grow: planned additions include memory-mapped snapshot persistence and a Compose `collectAsAccessor` helper that avoids boxing. Tests and more schemas will land before `Verified`.

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
