plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
}
