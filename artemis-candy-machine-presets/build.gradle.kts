plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  implementation(kotlin("stdlib"))

  // Optional, drop-in: depends only on other Artemis modules.
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-errors"))

  implementation(project(":artemis-candy-machine"))
  implementation(project(":artemis-tx-presets"))
  implementation(project(":artemis-presets"))
  implementation(project(":artemis-core"))
}
