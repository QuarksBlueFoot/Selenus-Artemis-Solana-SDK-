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

android {
    namespace = "com.selenus.artemis.compute"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
