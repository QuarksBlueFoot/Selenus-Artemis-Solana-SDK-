# artemis-jupiter

Native Kotlin client for Jupiter's v6 quote and swap APIs. Quote a trade, build a swap transaction, stream prices, and inspect routes, from one type-safe DSL. Mobile-friendly defaults, no JS runtime, no extra process.

Source: [../../ecosystem/artemis-jupiter/](../../ecosystem/artemis-jupiter/). The public API is in `JupiterClient.kt`.

## What it does

Every call hits the Jupiter v6 endpoints (`quote-api.jup.ag/v6` and `token.jup.ag` for the token registry). You get back strongly typed `QuoteResponse` / `SwapResponse` / `TokenInfo` values. Helpers on top: `calculatePriceImpact`, `visualizeRoute`, `streamQuotes`, and a mobile-optimization preset.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-jupiter:2.3.1")
}
```

## Create a client

```kotlin
import com.selenus.artemis.jupiter.JupiterClient

val jupiter = JupiterClient.create()
```

`JupiterClient.create()` uses the platform default HTTP client (OkHttp on JVM/Android) with 10s connect / 30s read/write timeouts. Override via `JupiterConfig`:

```kotlin
import com.selenus.artemis.jupiter.JupiterConfig

val jupiter = JupiterClient.create(
    JupiterConfig(
        connectTimeoutMs = 5_000,
        readTimeoutMs    = 15_000,
        writeTimeoutMs   = 15_000
    )
)
```

If you already manage an HTTP client, pass it in with `JupiterClient.create(httpClient, config)`.

## Quote a swap

The DSL uses method calls, not property assignments.

```kotlin
import com.selenus.artemis.jupiter.JupiterClient

val quote = jupiter.quote {
    inputMint(JupiterClient.USDC_MINT)
    outputMint(JupiterClient.SOL_MINT)
    amount(100_000_000L)      // 100 USDC (6 decimals)
    slippageBps(50)           // 0.5%
}

println("in:  ${quote.getInputAmountDecimal(decimals = 6)}")
println("out: ${quote.getOutputAmountDecimal(decimals = 9)}")
println("price impact: ${quote.priceImpactPct ?: "n/a"}")
println("hops:         ${quote.routePlan.size}")
```

Well-known mints are exposed as constants on the client companion:

```kotlin
JupiterClient.SOL_MINT
JupiterClient.USDC_MINT
JupiterClient.USDT_MINT
JupiterClient.BONK_MINT
JupiterClient.JUP_MINT
```

`inputMint`/`outputMint` also accept a `Pubkey` (the base58 conversion is done for you).

### Slippage

Two equivalent forms:

```kotlin
slippageBps(50)          // 50 basis points = 0.5%
slippagePercent(0.5)     // same
```

### Exact-out quotes

Jupiter supports both directions. Ask for "exactly N output tokens, spend whatever is needed" by switching the `swapMode`:

```kotlin
import com.selenus.artemis.jupiter.SwapMode

val quote = jupiter.quote {
    inputMint(JupiterClient.USDC_MINT)
    outputMint(JupiterClient.SOL_MINT)
    amount(1_000_000_000L)   // exactly 1 SOL out
    swapMode(SwapMode.ExactOut)
    slippageBps(100)
}
```

`amount` is interpreted as the output amount when `SwapMode.ExactOut` is set, and as the input amount when it is `SwapMode.ExactIn` (the default).

### Route shaping

```kotlin
val quote = jupiter.quote {
    inputMint(JupiterClient.USDC_MINT)
    outputMint(JupiterClient.SOL_MINT)
    amount(100_000_000L)

    onlyDirectRoutes(false)            // allow multi-hop
    maxAccounts(64)                    // cap transaction size
    restrictIntermediateTokens(true)   // only route through major tokens

    excludeDexes("GooseFX", "Cropper")  // or:
    // dexes("Orca", "Raydium")         // restrict to these

    platformFeeBps(20)                  // 0.2% platform fee if enabled
    asLegacyTransaction(false)          // v0 transactions by default
}
```

### Mobile-optimized preset

Bundled preset that caps accounts and restricts routing so the resulting transaction fits comfortably under MWA size limits:

```kotlin
val quote = jupiter.quote {
    inputMint(JupiterClient.USDC_MINT)
    outputMint(JupiterClient.SOL_MINT)
    amount(100_000_000L)
    slippageBps(50)
    mobileOptimized()   // maxAccounts(32) + restrictIntermediateTokens(true)
}
```

## Build a swap transaction

`swap { ... }` posts the quote to Jupiter's `/swap` endpoint and returns a base64-encoded, ready-to-sign transaction.

```kotlin
import com.selenus.artemis.jupiter.PriorityLevel

val swap = jupiter.swap {
    quote(quote)                            // pass the quote response
    userPublicKey(wallet.publicKey)         // Pubkey or String
    wrapAndUnwrapSol(true)
    priorityFee(PriorityLevel.HIGH)         // 100_000 microLamports
    dynamicSlippage(minBps = 50, maxBps = 300)
}

val txBytes: ByteArray = swap.getTransactionBytes()
```

### Priority fee

Two ways to set it:

```kotlin
priorityFee(PriorityLevel.HIGH)                         // preset (NONE/LOW/MEDIUM/HIGH/VERY_HIGH)
computeUnitPrice(microLamports = 50_000L)               // exact value
prioritizationFee(lamports = 1_000L)                    // or a fixed lamport amount
```

Preset values map as follows: `NONE` clears the price, `LOW` 1,000, `MEDIUM` 10,000, `HIGH` 100,000, `VERY_HIGH` 1,000,000 microLamports per CU.

### Dynamic slippage

`dynamicSlippage(minBps, maxBps)` tells Jupiter to pick slippage between those bounds based on simulation. The response carries a `dynamicSlippageReport` you can surface to the user.

### Other swap knobs

```kotlin
useSharedAccounts(true)          // default
useTokenLedger(false)
destinationTokenAccount("...")   // override destination ATA
trackingAccount("...")           // analytics pubkey
feeAccount("...")                // referral fee destination (Jupiter-side)
skipUserAccountsRpcCalls(false)
dynamicComputeUnitLimit(true)    // let Jupiter size CU limit from the route
asLegacyTransaction(false)       // v0 by default
```

## Sign and send

Once you have the base64 transaction, sign it through Artemis like any other transaction. `WalletAdapter.signMessage` takes the serialized transaction bytes and returns the fully signed wire bytes that you submit via RPC.

```kotlin
import com.selenus.artemis.wallet.SignTxRequest

val txBytes = swap.getTransactionBytes()

val signed = artemis.wallet.signMessage(
    txBytes,
    SignTxRequest(purpose = "jupiter-swap")
)
val signature = artemis.rpc.sendRawTransaction(signed, skipPreflight = false)
```

Or if you have a local signer and a prebuilt `VersionedTransaction`, the MWA path is not needed; sign locally and call `sendRawTransaction` the same way.

`getSwapInstructions(request)` is available when you want the instruction list instead of a built transaction, so you can splice your own ixs into the same transaction.

## Price impact

`calculatePriceImpact(quote)` returns a severity label and a human-readable recommendation, useful for surfacing warnings before a user confirms a swap:

```kotlin
val analysis = jupiter.calculatePriceImpact(quote)

when (analysis.severity) {
    PriceImpactSeverity.NEGLIGIBLE, PriceImpactSeverity.LOW -> { /* quiet */ }
    PriceImpactSeverity.MEDIUM -> toast(analysis.recommendation)
    PriceImpactSeverity.HIGH, PriceImpactSeverity.VERY_HIGH -> {
        showWarning(
            title = "Price impact ${"%.2f".format(analysis.percentageImpact)}%",
            body  = analysis.recommendation,
            suggested = analysis.suggestedMaxAmount
        )
    }
}
```

Thresholds: `< 0.1%` NEGLIGIBLE, `< 1%` LOW, `< 3%` MEDIUM, `< 5%` HIGH, otherwise VERY_HIGH.

## Route visualization

`visualizeRoute(quote)` flattens the `routePlan` into a list of `RouteStep`s, plus a one-line description you can show in the UI:

```kotlin
val viz = jupiter.visualizeRoute(quote)

Text(viz.description)              // "Multi-hop route via Orca → Raydium"
viz.steps.forEachIndexed { _, step ->
    Text("${step.label ?: step.ammKey} (${step.percent}%)")
}
```

## Streaming quotes

`streamQuotes` polls at a fixed interval and emits fresh `QuoteResponse`s on a `Flow`. Transient errors are swallowed so a flaky connection does not kill the flow; cancellation propagates normally.

```kotlin
jupiter.streamQuotes(
    inputMint  = JupiterClient.USDC_MINT,
    outputMint = JupiterClient.SOL_MINT,
    amount     = 100_000_000L,
    slippageBps = 50,
    interval    = 3.seconds
)
    .onEach { q -> currentRate.value = q.getExchangeRate(6, 9) }
    .launchIn(scope)
```

## Token metadata

```kotlin
val sol = jupiter.getTokenInfo(JupiterClient.SOL_MINT)
sol?.let { println("${it.symbol} (${it.decimals}) ${it.logoURI ?: ""}") }

val results = jupiter.searchTokens("bonk")
```

Both calls go against `token.jup.ag` and return `null`/`emptyList()` on failure rather than throwing.

## Error handling

Non-2xx responses raise `JupiterException(code, message, details)`. Empty bodies also raise it with `code = 0`. Wrap calls and surface the `details` payload when you want to show the upstream reason:

```kotlin
val quote = try {
    jupiter.quote { /* ... */ }
} catch (e: JupiterException) {
    Log.w("Jupiter", "quote failed ${e.code}: ${e.details}")
    return
}
```

## Under the hood

`quote`, `swap`, and `getSwapInstructions` each resolve to a single HTTP call against `quote-api.jup.ag/v6`. Responses are decoded with a strict-ish `Json { ignoreUnknownKeys = true; isLenient = true }` so Jupiter additions do not break older clients. Everything runs on `Dispatchers.IO`.

Tokens (`getTokenInfo`, `searchTokens`) hit `token.jup.ag`.

## Status

Listed as `Partial` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). Quote, swap, swap-instructions, program-id-to-label, token info, token search, price-impact analysis, and route visualization are covered. Areas not yet exercised by tests (not currently on the `Verified` path): long-lived session pooling, websocket-based quotes, and Jupiter's limit-order APIs.

Tests: [../../ecosystem/artemis-jupiter/src/jvmTest/kotlin/com/selenus/artemis/jupiter/JupiterModuleTest.kt](../../ecosystem/artemis-jupiter/src/jvmTest/kotlin/com/selenus/artemis/jupiter/JupiterModuleTest.kt).

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
