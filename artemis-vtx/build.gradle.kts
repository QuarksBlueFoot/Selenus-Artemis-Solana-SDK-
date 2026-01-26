plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-programs"))
  
  implementation(kotlin("stdlib"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-rpc"))
}
