plugins {
  kotlin("jvm")
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-tx"))
}

kotlin {
  jvmToolchain(17)
}
