# artemis-universal

Exploratory client for programs you do not have an IDL for. `UniversalProgramClient` samples a program's recent transactions and existing accounts to infer instruction discriminators, account roles, and account-data layouts, then lets you build instructions and decode accounts against the inferred schema.

Source: [../../advanced/artemis-universal/](../../advanced/artemis-universal/). Public entry point is `UniversalProgramClient`.

## When to reach for this module

Pick `artemis-universal` for debugging tools, block explorers, on-chain analyzers, and anywhere you need to interact with a program whose IDL is not published. The inferred schema is not a substitute for the real one: treat outputs as hypotheses and surface confidence values to users before they sign anything.

If you do have the IDL, use [artemis-anchor](ANCHOR_GUIDE.md) instead. It is deterministic and type-checked.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-universal:2.3.0")
}
```

## Create a client

The client needs an `RpcClientAdapter` - a small interface for the three RPC calls it makes (`getSignaturesForAddress`, `getTransaction`, `getProgramAccounts`). Supply your own implementation wrapping Artemis RPC or any other transport:

```kotlin
import com.selenus.artemis.universal.RpcClientAdapter
import com.selenus.artemis.universal.SignatureInfo
import com.selenus.artemis.universal.TransactionData
import com.selenus.artemis.universal.AccountData
import com.selenus.artemis.universal.UniversalProgramClient
import com.selenus.artemis.universal.UniversalConfig

class ArtemisRpcAdapter(private val rpc: RpcApi) : RpcClientAdapter {
    override suspend fun getSignaturesForAddress(
        address: String, limit: Int, before: String?
    ): List<SignatureInfo> { /* ... */ }

    override suspend fun getTransaction(signature: String): TransactionData { /* ... */ }

    override suspend fun getProgramAccounts(
        programId: String, limit: Int
    ): List<AccountData> { /* ... */ }
}

val universal = UniversalProgramClient.create(
    rpcClient = ArtemisRpcAdapter(artemis.rpc),
    config    = UniversalConfig(sampleSize = 100, forceRefresh = false)
)
```

`UniversalConfig` controls how many signatures and accounts are sampled per discovery (`sampleSize`, default 100), whether to bypass the in-memory schema cache (`forceRefresh`, default false), and the poll interval used by `monitorProgram` (`pollingIntervalMs`, default 3,000 ms).

## Discover a program

```kotlin
import com.selenus.artemis.runtime.Pubkey

val programId = Pubkey.fromBase58("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4")

val program = universal.discover(programId)

println("Anchor: ${program.isAnchorProgram}")
println("confidence:      ${"%.2f".format(program.confidence)}")
println("txns sampled:    ${program.transactionsSampled}")
println("accts sampled:   ${program.accountsSampled}")

program.instructions.forEach { ix ->
    println("${ix.name} disc=${ix.discriminator.hex} (${ix.sampleCount} samples, conf=${"%.2f".format(ix.confidence)})")
    ix.accounts.forEach { role ->
        println("  [${role.index}] ${role.inferredName} signer=${role.isSigner} writable=${role.isWritable}")
    }
    ix.dataPattern.fields.forEach { f ->
        println("  field ${f.name}: ${f.type} @${f.offset} size=${f.size}")
    }
}

program.accountTypes.forEach { acct ->
    println("account ${acct.name} disc=${acct.discriminator.hex} size=${acct.size} conf=${"%.2f".format(acct.confidence)}")
}
```

`discover` returns a cached `DiscoveredProgram` on subsequent calls for the same pubkey unless `config.forceRefresh` is true. Swap the cache implementation by passing a different `ProgramSchemaCache` when you construct the client (see `InMemoryProgramSchemaCache` for reference).

### What you get back

`DiscoveredProgram` exposes:

`programId`, `instructions: List<DiscoveredInstruction>` (each with name, 8-byte discriminator, inferred account roles, inferred data layout, sample count, confidence), `accountTypes: List<DiscoveredAccountType>` (discriminator, size, field pattern, confidence), `isAnchorProgram` (heuristic based on discriminator patterns), program-level `confidence`, plus `discoveredAt`, `transactionsSampled`, `accountsSampled` for audit.

`inferredName` is a convenience string (`AnchorProgram_<prefix>` or `Program_<prefix>`) for logs.

## Build an instruction

```kotlin
import com.selenus.artemis.universal.InstructionDataBuilder

val ix = universal.buildInstruction(program, "transfer") {
    account("source", wallet.publicKey)
    account("destination", recipient)
    u64("amount", 1_000_000L)
}
// ix is a UniversalInstruction(programId, keys: List<AccountMeta>, data: ByteArray)
```

The builder resolves the account by name first; if the name is not in the inferred account-role list it falls back to the positional slot. Signer and writable flags are taken from the inferred role; to override, use the three-argument overload:

```kotlin
universal.buildInstruction(program, "transfer") {
    account(wallet.publicKey, isSigner = true, isWritable = true)
    account(recipient, isSigner = false, isWritable = true)
    u64("amount", 1_000_000L)
}
```

Scalar helpers match the supported inferred types: `u8`, `u16`, `u32`, `u64`, `pubkey`, `bytes`. The 8-byte discriminator is inserted automatically at the start of the data buffer.

`UniversalInstruction.keys` is a list of the module's own `AccountMeta(pubkey: String, isSigner, isWritable)`. Convert to Artemis's `com.selenus.artemis.tx.AccountMeta` (which takes a `Pubkey`) when you stitch the result into a `Transaction` or hand it to `TxEngine`:

```kotlin
import com.selenus.artemis.tx.AccountMeta as TxAccountMeta
import com.selenus.artemis.tx.Instruction

fun UniversalInstruction.toArtemisInstruction(): Instruction =
    Instruction(
        programId = programId,
        accounts  = keys.map { TxAccountMeta(Pubkey.fromBase58(it.pubkey), it.isSigner, it.isWritable) },
        data      = data
    )

val result = artemis.session.send(ix.toArtemisInstruction())
```

Because the schema is inferred, always simulate before signing a real transaction.

## Decode accounts

```kotlin
val raw: ByteArray = fetchAccountBytes(pubkey)
val decoded = universal.decodeAccount(program, raw)

println("type:       ${decoded.typeName}")
println("confidence: ${decoded.confidence}")
decoded.fields.forEach { (name, value) -> println("  $name = $value") }
```

If the leading 8 bytes match a known discriminator in `program.accountTypes`, the decoder uses that type's inferred field pattern. If not, it falls back to a heuristic field-inference pass over the rest of the buffer and returns `typeName = "UnknownType_<discHex>"` with confidence `0.3`. Accounts shorter than 8 bytes come back with `discriminator = null` and confidence `0.0`.

## Watch a program

`monitorProgram` polls the program's recent signatures and emits a `DetectedInstruction` every time it sees a new one that matches a discovered pattern. The returned `Flow` runs on `Dispatchers.IO` and is safe to `collect` from a coroutine scope.

```kotlin
universal.monitorProgram(programId) { detected ->
    // lambda runs for each detection (in-band side effects)
    metrics.increment("ix.${detected.discriminator.hex}")
}
    .onEach { Log.i("universal", "saw ${it.signature} sig ${it.discriminator.hex}") }
    .launchIn(scope)
```

`DetectedInstruction` carries the transaction signature, the 8-byte discriminator, the account metas, the raw data, optional block time, and slot. Use `decodeAccount` or your own parser to go deeper.

## Find similar programs

If you have discovered several programs and want to spot ones that share an instruction shape (for example, multiple forks of the same DEX), `findSimilarPrograms` scores every other cached program by instruction overlap:

```kotlin
val similar = universal.findSimilarPrograms(programId)
similar.forEach { s ->
    println("${s.programId} ${s.name ?: "(unnamed)"} similarity=${"%.2f".format(s.similarity)} matches=${s.matchingInstructions}")
}
```

Only programs with at least 30% similarity are returned, sorted highest first.

## Generate a schema snapshot

Dump a discovery result as a serializable `ProgramSchema` (useful for caching to disk, sharing with teammates, or feeding into other tools):

```kotlin
val schema = universal.generateSchema(program)
// schema is @Serializable, encode with kotlinx.serialization as JSON
```

`ProgramSchema` captures `programId`, `name`, `version` (always `"1.0.0-discovered"` for now), instruction and account-type lists. It is explicitly not an Anchor IDL; it only records what was observed on-chain.

## Status

Listed as `Experimental` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). Discovery, instruction building, account decoding, and program monitoring are implemented. The pattern-matching heuristics are best-effort; you should expect false confidence on programs with small on-chain history and on programs whose argument encoding deviates from `borsh`/Anchor norms. Do not use this module as the only validator on a signing path.

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
