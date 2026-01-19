plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-discriminators"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
}
