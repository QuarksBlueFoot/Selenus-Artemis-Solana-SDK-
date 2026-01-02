plugins { 
  kotlin("multiplatform") 
}

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":artemis-core"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    val jvmMain by getting {
      dependencies {
        implementation(libs.okhttp)
        implementation(libs.kotlinx.coroutines.jdk8)
      }
    }
  }
}
