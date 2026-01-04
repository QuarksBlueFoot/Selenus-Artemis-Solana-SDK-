plugins{ 
    kotlin("jvm") 
    application
}

application {
    mainClass.set("com.selenus.artemis.preview.PreviewKt")
}

dependencies{
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation(project(":artemis-logging"))
implementation(kotlin("stdlib"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-tx"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-programs"))
  implementation(project(":artemis-compute"))
  implementation(project(":artemis-metaplex"))
  implementation(project(":artemis-token2022"))
  implementation(project(":artemis-cnft"))
  implementation(project(":artemis-mplcore"))
  implementation(project(":artemis-ws"))
  implementation(project(":artemis-gaming"))
  implementation(project(":artemis-replay"))
  implementation(project(":artemis-nft-compat"))
  implementation(project(":artemis-tx-presets"))
  implementation(project(":artemis-depin"))
  implementation(project(":artemis-solana-pay"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
