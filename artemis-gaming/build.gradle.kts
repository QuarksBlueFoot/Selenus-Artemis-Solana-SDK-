plugins { kotlin("jvm") }

dependencies {
  testImplementation(kotlin("test"))
  
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-ws"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-programs"))
  implementation(libs.kotlinx.coroutines.core)
}
