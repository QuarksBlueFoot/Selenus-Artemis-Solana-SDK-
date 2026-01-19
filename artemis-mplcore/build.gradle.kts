plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-discriminators"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
}
