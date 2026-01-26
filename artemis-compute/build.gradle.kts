plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-programs"))
  
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-vtx"))
  implementation(libs.kotlinx.coroutines.core)
}
