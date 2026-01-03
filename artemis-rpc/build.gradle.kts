plugins{ kotlin("jvm") }
dependencies{
  testImplementation(kotlin("test"))

  implementation(kotlin("stdlib"))
  implementation(project(":artemis-runtime"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
