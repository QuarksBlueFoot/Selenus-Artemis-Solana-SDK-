plugins { kotlin("jvm") }

dependencies {
  implementation(kotlin("stdlib"))
  implementation(libs.bouncycastle)
  
  // Coroutines for Flow-based reactive APIs and concurrency
  implementation(libs.kotlinx.coroutines.core)
  
  // Test dependencies
  testImplementation(kotlin("test"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlinx.coroutines.test)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
