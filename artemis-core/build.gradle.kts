plugins{ kotlin("jvm") }
dependencies{
  implementation(kotlin("stdlib"))
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
  
  // Coroutines for Flow-based reactive APIs and concurrency (merged from artemis-core)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  
  // Test dependencies
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
