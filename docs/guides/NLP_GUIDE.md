# artemis-nlp

Deterministic natural-language intent parser for Solana transactions. Takes a plain-English input like "send 1 SOL to alice.sol" and returns a typed `TransactionIntent` plus a `TransactionBuilder` that can describe, simulate, or execute the operation. Pattern matching is fully on-device; no LLM, no external inference calls, no network round-trips for the parse step itself.

Source: [../../advanced/artemis-nlp/](../../advanced/artemis-nlp/). Public entry points are `NaturalLanguageBuilder`, `RpcEntityResolver`, and `NlpExecutor`.

## What it does

Two pieces:

1. **`NaturalLanguageBuilder.parse(input)`** runs the string through a tokenizer, matches it against a registry of patterns (transfer, swap, stake, wrap, airdrop, memo, account management, authority management, etc.), extracts entities (amount, token, recipient, validator), and resolves them against an `EntityResolver` you supply (SNS domains, token symbols, program aliases, validator names, optional wallet aliases and `.skr` SeedVault references).

2. **`NlpExecutor(rpc, wallet)`** takes the resulting `TransactionIntent` and turns it into real Solana instructions, builds and signs the transaction, and submits it. `simulate(intent)` runs the same pipeline but calls RPC `simulateTransaction` instead of sending.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-nlp:2.3.0")
}
```

## Parse an input

```kotlin
import com.selenus.artemis.nlp.NaturalLanguageBuilder
import com.selenus.artemis.nlp.RpcEntityResolver
import com.selenus.artemis.nlp.ParseResult

val resolver = RpcEntityResolver(rpc)
val nlp      = NaturalLanguageBuilder.create(resolver)

when (val result = nlp.parse("send 1 SOL to alice.sol")) {
    is ParseResult.Success -> {
        println(result.intent.summary)             // "Transfer 1 SOL to alice.sol"
        println("confidence: ${result.confidence}")
        // result.builder is a TransactionBuilder (see below)
    }

    is ParseResult.Ambiguous -> {
        println("Primary:       ${result.primary.summary}")
        println("Alternatives:")
        result.alternatives.forEach { alt -> println("  - ${alt.summary}") }
    }

    is ParseResult.NeedsInfo -> {
        // `missing` is a list of field names the pattern still needs
        println("Missing: ${result.missing.joinToString()}")
        println(result.suggestion)
    }

    is ParseResult.Unknown -> {
        println("Didn't understand. Try one of:")
        result.suggestions.forEach { s ->
            println("  ${s.template}   (${s.description})")
        }
    }
}
```

`parse` is a `suspend` function because entity resolution may hit RPC (SNS domain lookup, validator lookups, token registry). Non-network resolutions (amount, known token symbol) stay in process.

## Supported intents

The registry lives in `NaturalLanguageBuilder.buildPatternRegistry()`. As of 2.3.0:

Transfer and send ("send N SOL to X", "transfer N USDC to X", "pay X N USDT"). Swap family ("swap N USDC for SOL", "exchange", "convert", "buy N worth of Y", "sell N X for Y"). Token operations (create, mint, burn). NFT transfer and burn. Staking (stake, unstake, delegate). Account management (close account, create ATA). Authority management (approve / revoke). SOL wrap / unwrap. Devnet helpers (airdrop, check balance). Memo. The full enum is `IntentType` in [../../advanced/artemis-nlp/src/main/kotlin/com/selenus/artemis/nlp/NaturalLanguageBuilder.kt](../../advanced/artemis-nlp/src/main/kotlin/com/selenus/artemis/nlp/NaturalLanguageBuilder.kt).

`nlp.getSupportedTypes()` returns a list of `TransactionTypeInfo(type, description, template, examples, requiredEntities)`, which is useful if you want to render a hint UI listing what the parser accepts.

## Entity resolution

`RpcEntityResolver` covers the common cases:

- `.sol` domains are resolved via the on-chain SNS program (derives the hashed-name account, reads the owner bytes at offset 32).
- Token symbols map to mint addresses via the Jupiter token registry (`token.jup.ag/all`), cached in memory after the first hit.
- Program aliases (`"system"`, `"token"`, `"jupiter"`, `"marinade"`, `"orca"`, ...) map to program IDs.
- A small validator table maps names like `"marinade"`, `"jito"`, `"figment"`, `"everstake"`, `"solflare"` to vote accounts.

Optional resolvers:

```kotlin
import com.selenus.artemis.nlp.WalletAliasStore
import com.selenus.artemis.nlp.SkrResolver

val aliases = WalletAliasStore().apply {
    // your app persists alias -> base58 mappings here
}

val resolver = RpcEntityResolver(
    rpc              = rpc,
    walletAliases    = aliases,
    skrResolver      = SkrResolver() // resolves "<name>.skr" SeedVault references
)
```

Anything that does not resolve comes back as the raw input with confidence `0.0`, so you can gate UI on `entity.confidence > threshold`.

### Implement a custom resolver

If you want a different data source, implement `EntityResolver` directly:

```kotlin
import com.selenus.artemis.nlp.EntityResolver

class MyResolver(private val myDirectory: MyDirectory) : EntityResolver {
    override suspend fun resolveDomain(domain: String): String? = null
    override suspend fun resolveTokenSymbol(symbol: String): String? = myDirectory.mintFor(symbol)
    override suspend fun resolveProgramName(name: String): String? = null
    override suspend fun resolveValidatorName(name: String): String? = null
    override suspend fun resolveSkrKey(skrRef: String): String? = null
    override suspend fun resolveWalletAlias(alias: String): String? = myDirectory.aliasFor(alias)
}
```

Every method has a safe default (`null`) on the interface, so you only override what you use.

## What `TransactionBuilder` exposes

```kotlin
val result = nlp.parse("send 1 SOL to alice.sol") as ParseResult.Success

val builder = result.builder
println(builder.getSummary())        // "Transfer 1 SOL to alice.sol"
println(builder.getDescription())    // longer description
val intent = builder.getIntent()     // back-reference to the TransactionIntent

// Declarative breakdown for previews/confirmations
val templates = builder.getInstructionTemplates()
templates.forEach { t ->
    println("${t.program}.${t.action}: ${t.description}")
    t.params.forEach { (k, v) -> println("  $k = $v") }
}
```

`InstructionTemplate` is an app-facing description (program name, action name, parameter map, human string). It is not yet a compiled `Instruction`; the compile step happens inside `NlpExecutor`, which builds real instructions using `SystemProgram`, `TokenProgram`, `AssociatedTokenProgram`, `StakeProgram`, `MemoProgram`, and the Jupiter client where applicable.

## Execute the intent

```kotlin
import com.selenus.artemis.nlp.NlpExecutor
import com.selenus.artemis.nlp.NlpExecutorConfig
import com.selenus.artemis.nlp.ExecutionResult

val executor = NlpExecutor(
    rpc    = artemis.rpc,
    wallet = artemis.wallet,
    config = NlpExecutorConfig(cluster = "mainnet-beta")
)

when (val r = executor.execute(intent)) {
    is ExecutionResult.Success -> {
        println("signed: ${r.signature}")
        println("explorer: ${r.explorerUrl}")
    }
    is ExecutionResult.Failed -> {
        log("nlp failed: ${r.error} (recoverable=${r.recoverable})")
    }
    is ExecutionResult.BalanceResult -> {
        // Emitted for CHECK_BALANCE intents; no transaction was sent
        println("${r.address}: ${r.balance} ${r.token}")
    }
}
```

`NlpExecutorConfig` defaults are `cluster = "devnet"`, `explorerBaseUrl = "https://explorer.solana.com"`, `skipPreflight = false`, `maxRetries = 3`, `confirmationTimeout = 30_000L`. Override the cluster and explorer URL for mainnet builds.

### Simulate first

For a preview/confirmation UI, call `simulate(intent)` before `execute` so the user sees compute units, the expected fee, and any simulation error:

```kotlin
when (val s = executor.simulate(intent)) {
    is SimulationResult.Success -> {
        Text("units: ${s.unitsConsumed}")
        Text("fee:   ${s.estimatedFee} lamports")
        // show logs in a collapsible if you want debug detail
    }
    is SimulationResult.Failed -> {
        Text("simulation failed: ${s.error}", color = Error)
    }
}
```

`SimulationResult.Success.unitsConsumed` is a `Long` from `simulateTransaction.value.unitsConsumed`, and `estimatedFee` is computed from the compute units by the executor.

## End-to-end example

```kotlin
suspend fun runUserText(input: String): String {
    val result = nlp.parse(input)

    if (result !is ParseResult.Success) {
        return "Could not parse: $input"
    }
    if (result.confidence < 0.7) {
        return "Not confident: ${result.intent.summary}"
    }

    // Show a preview
    val sim = executor.simulate(result.intent)
    if (sim is SimulationResult.Failed) return "Would fail: ${sim.error}"

    // Confirm with user, then execute
    if (!askUserToConfirm(result.intent, sim as SimulationResult.Success)) {
        return "Cancelled"
    }

    return when (val exec = executor.execute(result.intent)) {
        is ExecutionResult.Success      -> "Confirmed: ${exec.signature}"
        is ExecutionResult.Failed       -> "Failed: ${exec.error}"
        is ExecutionResult.BalanceResult -> "${exec.balance} ${exec.token}"
    }
}
```

## Hooking into suggestions

Two helpers for building a hint UI:

```kotlin
// While the user is typing, suggest pattern templates that partially match
val hints: List<Suggestion> = nlp.getSuggestions("send 1")
hints.forEach { s -> Text(s.template) }

// For an onboarding screen, list every supported operation
nlp.getSupportedTypes().forEach { info ->
    Section(info.description, info.examples)
}
```

`Suggestion(template, description, examples)` is what `ParseResult.Unknown.suggestions` returns as well, so the same renderer works for both paths.

## Status

Listed as `Experimental` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). The parser and entity resolver ship with tests (`NlpDevnetTest`, `WalletAliasAndSkrTest` under [../../advanced/artemis-nlp/src/test/kotlin/com/selenus/artemis/nlp/](../../advanced/artemis-nlp/src/test/kotlin/com/selenus/artemis/nlp/)). Treat this module as a UX accelerator rather than a safety boundary: always surface the decoded `TransactionIntent` and a simulation preview to the user before executing.

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
