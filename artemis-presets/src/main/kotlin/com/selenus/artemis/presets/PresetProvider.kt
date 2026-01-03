package com.selenus.artemis.presets

/**
 * PresetProvider
 *
 * Optional modules can implement this and apps can pass a list of providers to
 * [PresetRegistry.fromProviders].
 */
fun interface PresetProvider {
  fun registerInto(registry: PresetRegistry)
}
