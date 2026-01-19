plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-errors"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
}
