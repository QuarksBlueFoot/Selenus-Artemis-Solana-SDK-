plugins {
  kotlin("multiplatform")
  id("com.android.library")
}

kotlin {
  jvm()
  androidTarget()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":artemis-discriminators"))
      implementation(project(":artemis-core"))
      implementation(project(":artemis-tx"))
      implementation(project(":artemis-rpc"))
      implementation(project(":artemis-vtx"))
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":artemis-programs"))
    }
  }
}

android {
    namespace = "com.selenus.artemis.cnft"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
