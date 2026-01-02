plugins { kotlin("multiplatform") }

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":artemis-core"))
      implementation(project(":artemis-tx"))
      implementation(project(":artemis-rpc"))
      implementation(project(":artemis-vtx"))
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":artemis-programs"))
    }
  }
}
