package com.selenus.artemis.candymachine.presets

import com.selenus.artemis.presets.ArtemisPreset
import com.selenus.artemis.presets.PresetProvider
import com.selenus.artemis.presets.PresetRegistry

/**
 * v61: Registry metadata for Candy Machine presets.
 */
object CandyMachinePresetDescriptors {

  object MintWithPriorityAndResend : ArtemisPreset {
    override val id: String = "candy_machine.mint_with_priority_and_resend.v60"
    override val name: String = "Candy Machine mint"
    override val description: String = "Plans Candy Guard accounts, validates requirements, and sends with ATA + priority + resend."
  }

  val provider: PresetProvider = PresetProvider { registry: PresetRegistry ->
    registry.register(MintWithPriorityAndResend)
  }
}
