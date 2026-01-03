plugins{ kotlin("jvm") }

dependencies{
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-tx"))

  // v65: provide a "Metaplex in one module" facade without requiring another SDK.
  // These are internal composition dependencies; consumers can still depend on sub-modules directly.
  implementation(project(":artemis-nft-compat"))
  implementation(project(":artemis-candy-machine"))
  implementation(project(":artemis-candy-machine-presets"))
  implementation(project(":artemis-cnft"))
  implementation(project(":artemis-mplcore"))
}
