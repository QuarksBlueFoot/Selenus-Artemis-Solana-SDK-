plugins{ kotlin("jvm") }
dependencies{
  implementation(kotlin("stdlib"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-rpc"))
}
