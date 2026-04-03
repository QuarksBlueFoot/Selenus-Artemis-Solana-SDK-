plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
  jvm()
  androidTarget()

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
    androidMain {
      kotlin.srcDir("src/jvmMain/kotlin")
      dependencies {
        implementation(libs.okhttp)
      }
    }
  }
}

android {
    namespace = "com.selenus.artemis.rpc"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
