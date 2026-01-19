plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-errors"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-core"))
}
