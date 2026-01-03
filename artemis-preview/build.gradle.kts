plugins{ kotlin("jvm") }
dependencies{
  
  implementation(project(":artemis-logging"))
implementation(kotlin("stdlib"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-metaplex"))
  implementation(project(":artemis-token2022"))
  implementation(project(":artemis-cnft"))
  implementation(project(":artemis-mplcore"))
  implementation(project(":artemis-ws"))
}
