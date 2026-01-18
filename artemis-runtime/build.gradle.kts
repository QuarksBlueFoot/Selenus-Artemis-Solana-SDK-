plugins{ kotlin("jvm") }
dependencies{
  implementation(kotlin("stdlib"))
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
  
  // Test dependencies
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
