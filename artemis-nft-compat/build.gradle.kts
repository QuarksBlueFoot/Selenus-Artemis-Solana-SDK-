plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(kotlin("test"))
  implementation(libs.kotlinx.serialization.json)
  implementation(project(":artemis-core"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-tx"))
}

kotlin {
  jvmToolchain(17)
}
