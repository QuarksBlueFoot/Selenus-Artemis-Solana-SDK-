plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-discriminators"))
  implementation(project(":artemis-nft-compat"))
  implementation(project(":artemis-rpc"))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(kotlin("test"))
}
