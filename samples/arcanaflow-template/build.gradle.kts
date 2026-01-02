plugins {
  kotlin("jvm")
  application
}

repositories { mavenCentral() }

dependencies {
  
  implementation(project(":artemis-logging"))
implementation(project(":artemis-gaming"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-rpc"))
}

application {
  mainClass.set("com.selenus.samples.arcana.MainKt")
}
