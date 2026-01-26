plugins { 
  kotlin("jvm") 
}

dependencies {
  testImplementation(kotlin("test"))
  implementation(project(":artemis-core"))
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.jdk8)
  implementation(libs.kotlinx.serialization.json)
}
