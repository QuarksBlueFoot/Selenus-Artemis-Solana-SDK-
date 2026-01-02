plugins { kotlin("jvm") }

dependencies {
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-discriminators"))
  
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-rpc"))
}
