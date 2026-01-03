plugins { kotlin("jvm") }

dependencies {
  implementation(kotlin("stdlib"))
  // Keep this module fully optional and drop-in.
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-errors"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-presets"))
}
