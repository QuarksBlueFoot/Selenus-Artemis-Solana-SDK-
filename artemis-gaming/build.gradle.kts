plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-ws"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-programs"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
