package com.selenus.artemis.txpresets

import com.selenus.artemis.presets.ArtemisPreset
import com.selenus.artemis.presets.PresetProvider
import com.selenus.artemis.presets.PresetRegistry

/**
 * v61: Registry metadata for this module.
 */
object TxPresetDescriptors {

  object SendWithAtaAndPriority : ArtemisPreset {
    override val id: String = "tx.send_with_ata_and_priority.v59"
    override val name: String = "Send with ATA + priority"
    override val description: String = "Composes optional ATA creation, compute budget, priority fees, and resend/confirm."
  }

  val provider: PresetProvider = PresetProvider { registry: PresetRegistry ->
    registry.register(SendWithAtaAndPriority)
  }
}
