plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(libs.kotlinx.serialization.json)
}
