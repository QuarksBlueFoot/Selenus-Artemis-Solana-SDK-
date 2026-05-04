# artemis-anchor

Runtime Anchor program client for Kotlin. Load an IDL JSON, construct an `AnchorProgram`, and build type-resolved instructions, fetch and decode state accounts, derive PDAs from IDL seeds, parse events, and decode program errors. No KSP, no codegen step, no extra build configuration.

The module lives at [../../ecosystem/artemis-anchor/](../../ecosystem/artemis-anchor/). The Anchor discriminator helpers live at [../../compatibility/artemis-discriminators/](../../compatibility/artemis-discriminators/).

## When to reach for this module

Pick `artemis-anchor` when you want to talk to an Anchor program without writing manual Borsh serializers or hand-rolling discriminator bytes. If you already have a typed Kotlin client for a specific program (for example a hand-written builder), you do not need this module.

The runtime IDL path lets you ship one client binary that works against many programs or against programs whose IDL changes between versions. The tradeoff is that argument types are validated at call time, not at compile time.

## Install

```kotlin
dependencies {
    implementation("xyz.selenus:artemis-anchor:2.3.1")
}
```

## Load an IDL

`AnchorProgram` is constructed from an `AnchorIdl`. The IDL is a plain JSON string that you load however you like. Two equivalent forms:

```kotlin
import com.selenus.artemis.anchor.AnchorIdl
import com.selenus.artemis.anchor.AnchorProgram
import com.selenus.artemis.runtime.Pubkey

val idlJson: String = loadIdlFromAssets("my_program.json")

// Two-step form
val idl: AnchorIdl = AnchorProgram.parseIdl(idlJson)
val program = AnchorProgram(idl, Pubkey.fromBase58(MY_PROGRAM_ID), rpc)

// One-step form
val program = AnchorProgram.fromIdl(idlJson, Pubkey.fromBase58(MY_PROGRAM_ID), rpc)
```

The third argument (`rpc: RpcApi?`) is optional. It is only needed for the account-fetch helpers that make RPC calls; instruction building, PDA derivation, and event/error decoding do not need it.

The parser ignores unknown keys and accepts lenient JSON, so newer IDL formats with fields Artemis does not yet understand will still load without throwing.

## Build an instruction

```kotlin
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.programs.SystemProgram

val authority = wallet.publicKey
val statePda: Pubkey = /* derive below */

val ix = program.methods
    .instruction("initialize")
    .args(mapOf("name" to "MyToken", "decimals" to 9.toByte()))
    .accounts {
        writable("state", statePda)
        signer("authority", authority)
        program("systemProgram", SystemProgram.PROGRAM_ID)
    }
    .build()
```

The shape mirrors Anchor's TypeScript client (`program.methods.x(args).accounts({}).rpc()`) so anyone coming from TS will read it the same way.

### `args` accepts two forms

Map form, when the argument values come from variables:

```kotlin
.args(mapOf(
    "amount" to 1_000_000L,
    "memo"   to "hello"
))
```

DSL form, when you are spelling them out inline. The `infix to` places each pair into the underlying map:

```kotlin
.args {
    "amount" to 1_000_000L
    "memo"   to "hello"
}
```

Supported argument types are whatever `BorshSerializer` supports for IDL primitive types: `bool`, `u8`/`u16`/`u32`/`u64`, `i8`/`i16`/`i32`/`i64`, `string`, `publicKey` (pass a `Pubkey`), `bytes` (pass a `ByteArray`), plus vectors, options, and defined structs/enums from the IDL's `types` section.

### `accounts { }` helpers

The accounts block maps IDL account names to pubkeys and role flags. The helper names reflect the role:

```kotlin
.accounts {
    account("config", configPda)              // readonly, non-signer
    writable("state", statePda)               // writable, non-signer
    signer("authority", wallet.publicKey)     // readonly signer
    signerWritable("payer", wallet.publicKey) // writable signer
    program("tokenProgram", TokenProgram.PROGRAM_ID)  // readonly, non-signer (semantic hint)
}
```

If the IDL marks an account as `writable: true` or `signer: true`, Artemis ORs that with the role you picked, so you can pass `account(...)` for an account the IDL already marks writable and it will still be writable.

Required accounts that you do not provide raise `IllegalStateException` at `build()` time. Accounts marked `optional: true` in the IDL may be omitted.

### Remaining accounts

Some instructions take a tail of variable-length accounts. Pass them as raw `AccountMeta` entries:

```kotlin
import com.selenus.artemis.tx.AccountMeta

.remainingAccounts(listOf(
    AccountMeta(proofNode0, isSigner = false, isWritable = false),
    AccountMeta(proofNode1, isSigner = false, isWritable = false)
))
```

They are appended after the named accounts in the order given.

## Send the instruction

Instructions built through `program.methods` are ordinary `com.selenus.artemis.tx.Instruction` values. Hand them to `TxEngine`, `WalletSession`, or any other Artemis path.

```kotlin
val result = artemis.session.send(ix)
if (result is TxResult.Success) println("signature: ${result.signature}")
```

Batch multiple anchor calls in one transaction:

```kotlin
val ix1 = program.methods.instruction("initialize").accounts { /* ... */ }.build()
val ix2 = program.methods.instruction("setConfig").args(/* ... */).accounts { /* ... */ }.build()

artemis.session.sendBatch(listOf(ix1, ix2))
```

## Fetch and decode an account

The `account` builder targets a named account type from the IDL and either fetches a single address, fetches many, or scans the program for every account of that type.

```kotlin
val fetcher = program.account.type("TokenState")

// Single
val acct = fetcher.fetch(statePda, rpc)
acct?.get<Long>("totalSupply")?.let { println("supply = $it") }

// Multiple (one `getMultipleAccounts` call)
val results: List<DecodedAccount?> = fetcher.fetchMultiple(listOf(a, b, c), rpc)

// All accounts of this type, with memcmp filter
val mine = fetcher.all()
    .filter(offset = 0, bytes = authority.bytes)
    .fetch(rpc)
```

`DecodedAccount` exposes the decoded struct as `Map<String, Any?>` via `data`, plus a typed accessor helper (`account.get<Long>("totalSupply")`) and the raw `ByteArray`. Use the map form when you iterate; use `get<T>()` when you know the expected type.

The discriminator is verified when decoding. Accounts whose first 8 bytes do not match are returned as `null` rather than decoded into the wrong struct.

### Typed memcmp by field name

`AllAccountsFetcher.filterByField` computes the offset of a struct field from the IDL and applies a memcmp filter at that offset. Today it is wired for `Pubkey` fields:

```kotlin
val mine = program.account.type("TokenState")
    .all()
    .filterByField("authority", wallet.publicKey)
    .fetch(rpc)
```

For types other than `Pubkey`, compute the offset yourself and call `filter(offset, bytes)`.

### Watch an account

`watch` polls an account on an interval and emits decoded updates whenever the raw bytes change. It is a coroutine `Flow`, so it plays nicely with Compose:

```kotlin
program.account.type("TokenState")
    .watch(statePda, rpc, intervalMs = 2_000L)
    .onEach { account -> updateUi(account) }
    .launchIn(scope)
```

For push-based updates use `RealtimeEngine.subscribeAccount(...)` from `artemis-ws` and decode the notification with `fetcher.decode(data)` yourself.

## Derive a PDA from IDL seeds

When the IDL declares a PDA for an account, `program.pda.findForAccount` rebuilds the address from the same recipe:

```kotlin
val (statePda, bump) = program.pda
    .findForAccount(
        instructionName = "initialize",
        accountName     = "state",
        args            = mapOf("name" to "MyToken"),
        accounts        = mapOf("authority" to wallet.publicKey)
    ) ?: error("state account has no PDA definition in the IDL")
```

`args` satisfies `IdlSeed.Arg` entries by name. `accounts` satisfies `IdlSeed.Account` entries by name. Constant seeds are resolved from the IDL directly.

If the account does not declare a `pda` block in the IDL, or a required seed input is missing, `findForAccount` returns `null`.

For anything outside the IDL, use `Pubkey.findProgramAddress(seeds, programId)` directly from `artemis-core`.

## Parse events

Anchor programs emit events as log messages or CPIs. If you have the raw event bytes (for example from a log or a notification), `program.events.parse` walks every `events[]` entry in the IDL and returns the first match:

```kotlin
val parsed = program.events.parse(eventBytes)
parsed?.let {
    println("${it.name} fields=${it.fields}")
}
```

`parsed.fields` is a `Map<String, Any?>` using the same decoder as account fetching.

## Decode errors

Anchor programs emit `Program log: Error Code: NNN ...` lines when an instruction fails. `program.errors.decode` maps a numeric code to the IDL entry, and `decodeFromLogs` scans a list of log lines for the first matching pattern:

```kotlin
when (val r = artemis.session.send(ix)) {
    is TxResult.SimulationFailed -> {
        val decoded = program.errors.decodeFromLogs(r.logs.orEmpty())
        if (decoded != null) {
            toast("${decoded.name}: ${decoded.message ?: "code ${decoded.code}"}")
        } else {
            toast("Simulation failed: ${r.error}")
        }
    }
    is TxResult.Success -> toast("confirmed")
    else -> toast("failed")
}
```

`DecodedError` exposes `code: Int`, `name: String`, and `message: String?`.

## Discriminator helpers

You usually do not need these directly. If you do (for example writing your own raw instruction where Anchor is not involved everywhere), they are in `artemis-discriminators`:

```kotlin
import com.selenus.artemis.disc.AnchorDiscriminators

val ixDisc: ByteArray   = AnchorDiscriminators.global("transfer")    // sha256("global:transfer")[0..8]
val acctDisc: ByteArray = AnchorDiscriminators.account("TokenState") // sha256("account:TokenState")[0..8]
```

`AnchorProgram` precomputes these on construction. If the IDL supplies explicit `discriminator: [...]` bytes, those are used in preference to the derived ones, matching recent Anchor IDL versions.

## Status

Listed as `Partial` in [../PARITY_MATRIX.md](../PARITY_MATRIX.md). The runtime path is exercised by `AnchorModuleTest` in [../../ecosystem/artemis-anchor/src/jvmTest/kotlin/com/selenus/artemis/anchor/AnchorModuleTest.kt](../../ecosystem/artemis-anchor/src/jvmTest/kotlin/com/selenus/artemis/anchor/AnchorModuleTest.kt) covering IDL parsing, discriminator computation, instruction building with map and DSL args, PDA derivation scaffolding, and account-type lookup. Broader coverage (full enum support in args, generics, `bytemuck` layouts) is on the roadmap.

A compile-time client generator is not shipped today. If you want generated Kotlin classes per program, hand-roll the client for now and let the runtime path handle the dynamic cases.

## License

Apache License 2.0. See [../../LICENSE](../../LICENSE).
