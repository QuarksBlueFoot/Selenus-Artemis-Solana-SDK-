plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-programs"))
  testImplementation(project(":artemis-tx"))
  testImplementation(project(":artemis-rpc"))
  
  implementation(project(":artemis-runtime"))
}
