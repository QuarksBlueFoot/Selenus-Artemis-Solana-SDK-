plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))

  testImplementation(kotlin("test"))
}
