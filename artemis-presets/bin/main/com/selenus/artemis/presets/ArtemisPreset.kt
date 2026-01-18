package com.selenus.artemis.presets

/**
 * ArtemisPreset
 *
 * v61 goal: a tiny, optional registry contract that lets apps compose multiple optional Artemis
 * modules (mint presets, transaction presets, etc.) without polluting core.
 */
interface ArtemisPreset {
  /** Stable id suitable for metrics and feature flags. Example: "candy_machine.mint.v60" */
  val id: String

  /** Short human name. */
  val name: String

  /** One-line, factual description. */
  val description: String
}
