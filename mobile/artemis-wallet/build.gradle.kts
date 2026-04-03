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
    androidMain {
      kotlin.srcDir("src/jvmMain/kotlin")
    }
  }
}

android {
    namespace = "com.selenus.artemis.wallet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
