# Game compute-budget presets

`ComputeBudgetPresets` in [`artemis-gaming`](../../../advanced/artemis-gaming/) ships three ready-made compute-unit / priority-fee bundles for different categories of game action. Each preset returns a pair of `ComputeBudgetProgram` instructions you can prepend to a transaction.

## Tiers

`ComputeBudgetPresets.Tier` (source: [ComputeBudgetPresets.kt](../../../advanced/artemis-gaming/src/main/kotlin/com/selenus/artemis/gaming/ComputeBudgetPresets.kt)):

| Tier | CU limit | Priority fee (microLamports) | Typical use |
|---|---|---|---|
| `ARCADE` | 200,000 | 100 | Cheap per-frame moves, low-stakes taps |
| `COMPETITIVE` | 400,000 | 500 | PvP, ranked play, ladder progression |
| `BOSS_FIGHT` | 800,000 | 2,000 | End-game encounters, high-traffic bursts |

## Apply a preset

```kotlin
import com.selenus.artemis.gaming.ComputeBudgetPresets

val budget = ComputeBudgetPresets.preset(ComputeBudgetPresets.Tier.COMPETITIVE)
// budget is List<Instruction>: [setComputeUnitLimit, setComputeUnitPrice]

val ixs = budget + listOf(gameIx1, gameIx2)
artemis.session.sendBatch(ixs)
```

## Individual helpers

If you want finer control, call the wrappers directly:

```kotlin
ComputeBudgetPresets.setComputeUnitLimit(units = 350_000)
ComputeBudgetPresets.setComputeUnitPrice(microLamports = 250L)
```

Both return a single `Instruction`. They are thin delegates over [`artemis-compute`](../../../foundation/artemis-compute/)'s `ComputeBudgetProgram`, so any other module (`TxEngine`, `artemis-tx-presets`) can consume them directly.

## Related

`artemis-gaming` also ships `AdaptiveFeeOptimizer` (congestion-aware fee suggestion with `CongestionLevel`, `ActionPriority`, and `GamingTier` enums) and `ArcanaFlowV2` for frame-packed action streaming. See the module source for those surfaces.
