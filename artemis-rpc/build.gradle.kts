plugins{ kotlin("jvm") }
dependencies{
  testImplementation(kotlin("test"))
  testImplementation(project(":artemis-programs"))

  implementation(kotlin("stdlib"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-tx"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
