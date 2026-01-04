# Candy Machine Intelligence Layer

This module removes the last practical pain point for Candy Machine v3 on mobile:

You know the instruction name, but you do not know which accounts, guards, or mint args you need.

The goal is deterministic transaction building from pure on-chain reads.

No indexer. No paid services. No vendor lock-in.

## What you get

### 1. Guard manifest introspection

`CandyGuardManifestReader` reads the Candy Guard account and outputs a machine readable `CandyGuardManifest`.

It includes:

- which guards are enabled
- which mint args are required (allowlist proof, mint limit id, allocation id, gatekeeper token)
- whether payment is SOL or an SPL token

### 2. Guard aware account planning

`CandyGuardAccountPlanner.planMint` derives the standard account set for `mint_v2` and can also serialize MintArgs for common guards.

It does not require an indexer. It only reads:

- Candy Guard account
- Candy Machine account
- collection metadata account

### 3. Safe mint builder

`CandyGuardMintV2Safe.buildSafe` wraps the planner + builder and fails early with human readable messages when a required input is missing.

## Mint in 15 lines

```kotlin
import com.selenus.artemis.candymachine.CandyGuardMintV2Safe
import com.selenus.artemis.candymachine.guards.GuardArgs
import com.selenus.artemis.rpc.JsonRpcClient
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey

val rpc = RpcApi(JsonRpcClient("https://api.mainnet-beta.solana.com"))

val wallet = Pubkey.fromBase58("...")
val candyGuard = Pubkey.fromBase58("...")
val candyMachine = Pubkey.fromBase58("...")
val mint = Pubkey.fromBase58("...")

val result = CandyGuardMintV2Safe.buildSafe(
  rpc = rpc,
  wallet = wallet,
  candyGuard = candyGuard,
  candyMachine = candyMachine,
  mint = mint,
  guardArgs = GuardArgs(),
)

val ix = result.instruction
val warnings = result.warnings
```

## Read state for UI

```kotlin
import com.selenus.artemis.candymachine.CandyMachineIntelligence

val state = CandyMachineIntelligence.readCandyMachineState(
  rpc = rpc,
  candyMachine = candyMachine,
  candyGuard = candyGuard,
)

if (state.isSoldOut) {
  // disable button
}
```

## Migration note

If you already use Candy Machine via JavaScript or KMM, this module replaces most of the glue code:

- it tells you which mint args are required
- it derives the account set for `mint_v2`
- it produces readable errors before you send a transaction

## Safety rules

- No TODOs or stubs.
- No copied Metaplex source files.
- Fail closed when Candy Guard includes unsupported guards.
- No paid dependency assumptions.
