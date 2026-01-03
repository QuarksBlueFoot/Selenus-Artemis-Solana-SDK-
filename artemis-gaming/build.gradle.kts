plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-ws"))
}
