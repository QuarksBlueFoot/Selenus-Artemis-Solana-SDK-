plugins {
  kotlin("multiplatform")
  id("com.android.library")
}

kotlin {
  jvm()
  androidTarget()

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
    androidMain {
      kotlin.srcDir("src/jvmMain/kotlin")
      dependencies {
        implementation(libs.okhttp)
        implementation(libs.kotlinx.coroutines.jdk8)
      }
    }
  }
}

android {
    namespace = "com.selenus.artemis.ws"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
