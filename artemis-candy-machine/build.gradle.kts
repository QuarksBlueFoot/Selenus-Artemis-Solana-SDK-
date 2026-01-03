plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-discriminators"))
  implementation(project(":artemis-nft-compat"))
  implementation(project(":artemis-rpc"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  testImplementation(kotlin("test"))
}
