plugins { kotlin("multiplatform") }

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      implementation(kotlin("stdlib"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(project(":artemis-errors"))
      implementation(project(":artemis-core"))
      implementation(project(":artemis-tx"))
      implementation(project(":artemis-vtx"))
      implementation(project(":artemis-rpc"))
      implementation(project(":artemis-compute"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    jvmTest.dependencies {
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}
