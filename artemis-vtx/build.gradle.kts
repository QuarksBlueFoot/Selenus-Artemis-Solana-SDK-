plugins{ kotlin("jvm") }
dependencies{
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-programs"))
  
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-rpc"))
}
