plugins { kotlin("multiplatform") }

kotlin {
  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":artemis-core"))
        implementation(project(":artemis-tx"))
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines.core)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(libs.okhttp)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(project(":artemis-programs"))
      }
    }
  }
}
