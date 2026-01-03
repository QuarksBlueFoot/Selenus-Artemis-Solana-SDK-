# artemis-gaming

Solana gaming utilities for Kotlin and Android.

Full ArcanaFlow guide: see docs/arcanaflow.md

# artemis-gaming

Solana gaming utilities for Kotlin and Android.

This module is opinionated. It focuses on what real mobile games need:
- low friction transaction building
- predictable performance defaults
- easy batching
- session keys

## Compute presets

```kotlin
val ixs = ComputeBudgetPresets.preset(ComputeBudgetPresets.Tier.COMPETITIVE)
```

## Session keys

```kotlin
val session = SessionKeys.new()
val sessionPubkey = session.pubkey
```

## ArcanaFlow

A batching and frame emission helper. You can sign and send each frame as a v0 tx.

```kotlin
val lane = ArcanaFlow(scope)
lane.start()

lane.enqueue(gameActionIx)

scope.launch {
  lane.frames.collect { frame ->
    // build v0 tx with ALT and send
  }
}
```


## ALT session builder

Build stable ALT proposals from ArcanaFlow frames.

```kotlin
val cache = AltSessionCache()
val builder = AltSessionBuilder(maxAddresses = 256)

scope.launch {
  lane.frames.collect { frame ->
    val proposal = ArcanaFlowTxHelper.collectForAltPlanning(frame, cache, builder)
    // proposal.addresses is a deterministic list you can feed into your ALT creation flow
  }
}
```


## ALT execution bundles

Build create and extend instruction bundles for lookup tables.

```kotlin
val proposal = builder.propose()
val plan = AltSessionExecutor.createAndExtend(
  authority = authorityPubkey,
  payer = payerPubkey,
  recentSlot = recentSlot,
  addresses = proposal.addresses,
  extendChunk = 20
)
// plan.instructions can be sent as one or more transactions
```


## Frame transaction planning

Compose compute budget, priority fees, and lookup table hints for each ArcanaFlow frame.

```kotlin
val composer = ArcanaFlowFrameComposer(
  programId = gameProgramId,
  tier = ComputeBudgetPresets.Tier.COMPETITIVE,
  oracle = oracle
)

val txPlan = composer.compose(
  frame = frame,
  knownLookupTables = listOf(gameLutAddress)
)

// Use AltToolkit (or your pipeline) to compile and sign v0 with txPlan.lookupTableAddresses
```


## Lookup table tx scheduling

Split create and extend plans into multiple transactions.

```kotlin
val plan = AltSessionExecutor.createAndExtend(
  authority = authorityPubkey,
  payer = payerPubkey,
  recentSlot = recentSlot,
  addresses = proposal.addresses
)

val batches = AltTxScheduler.scheduleCreatePlan(plan, maxExtendIxsPerTx = 2)

for (b in batches) {
  // sign + send tx using b.instructions
}
```


## Compile v0 transactions for frames

Turn a FrameTxPlan into a signed v0 VersionedTransaction using auto fetched lookup tables.

```kotlin
val tx = ArcanaFlowV0Compiler.compileFrame(
  rpc = rpc,
  feePayer = feePayer,
  additionalSigners = listOf(sessionSigner),
  recentBlockhash = blockhash,
  plan = txPlan
)
```
