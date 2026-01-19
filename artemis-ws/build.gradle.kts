plugins { 
  kotlin("jvm") 
}

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-core"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
