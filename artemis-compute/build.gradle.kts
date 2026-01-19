plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-vtx"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
