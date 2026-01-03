package com.selenus.artemis.presets

import com.selenus.artemis.errors.ArtemisError

/**
 * PresetRegistry
 *
 * A lightweight registry to collect optional presets from optional modules.
 *
 * - No reflection
 * - No service loader
 * - No side effects
 */
class PresetRegistry {
  private val byId = linkedMapOf<String, ArtemisPreset>()

  fun register(preset: ArtemisPreset) {
    val existing = byId[preset.id]
    if (existing != null) {
      throw RegistryError("Preset already registered: ${preset.id}")
    }
    byId[preset.id] = preset
  }

  fun get(id: String): ArtemisPreset? = byId[id]

  fun require(id: String): ArtemisPreset = byId[id]
    ?: throw RegistryError("Preset not found: $id")

  fun all(): List<ArtemisPreset> = byId.values.toList()

  companion object {
    fun fromProviders(providers: List<PresetProvider>): PresetRegistry {
      val r = PresetRegistry()
      for (p in providers) p.registerInto(r)
      return r
    }
  }

  class RegistryError(message: String) : ArtemisError(message)
}
