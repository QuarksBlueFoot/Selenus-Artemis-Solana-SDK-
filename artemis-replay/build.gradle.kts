plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
