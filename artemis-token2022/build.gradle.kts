plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))

  testImplementation(kotlin("test"))
}
