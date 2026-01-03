plugins {
  kotlin("jvm")
  application
}

repositories { mavenCentral() }

dependencies {
  
  implementation(project(":artemis-logging"))
implementation(project(":artemis-gaming"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-runtime"))
  implementation(project(":artemis-rpc"))
}

application {
  mainClass.set("com.selenus.samples.arcana.MainKt")
}
