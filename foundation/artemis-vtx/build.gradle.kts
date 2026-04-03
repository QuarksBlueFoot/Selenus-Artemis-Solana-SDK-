plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
  jvm()
  androidTarget()

  sourceSets {
    commonMain.dependencies {
      implementation(kotlin("stdlib"))
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(project(":artemis-core"))
      implementation(project(":artemis-tx"))
      implementation(project(":artemis-rpc"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":artemis-programs"))
    }
  }
}

android {
    namespace = "com.selenus.artemis.vtx"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
