# Preset registry

v61 introduces a tiny optional registry contract that lets apps compose multiple optional Artemis
modules without adding new global singletons or touching core.

This is metadata and discovery. It does not change transaction building behavior.

## When to use this

- You ship a mobile app that bundles multiple optional Artemis modules and you want a single place
  to list what is available.
- You want a UI that can show built-in actions such as "Candy Machine mint" or "Send with ATA + priority"
  without hardcoding per-module lists.

## Components

- `artemis-presets`
  - `ArtemisPreset`: id, name, description
  - `PresetProvider`: register presets into a registry
  - `PresetRegistry`: collects presets from providers

Optional modules can expose a provider:

- `artemis-tx-presets`: `TxPresetDescriptors.provider`
- `artemis-candy-machine-presets`: `CandyMachinePresetDescriptors.provider`

## Example

```kotlin
import com.selenus.artemis.presets.PresetRegistry
import com.selenus.artemis.txpresets.TxPresetDescriptors
import com.selenus.artemis.candymachine.presets.CandyMachinePresetDescriptors

val registry = PresetRegistry.fromProviders(
  listOf(
    TxPresetDescriptors.provider,
    CandyMachinePresetDescriptors.provider,
  )
)

for (p in registry.all()) {
  println("${p.id}: ${p.name} - ${p.description}")
}
```

## Notes

- No reflection, no service loader.
- If you do not include an optional module, its presets do not exist.
- Duplicate preset ids are rejected at registration time.
