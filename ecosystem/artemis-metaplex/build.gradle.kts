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
      implementation(project(":artemis-rpc"))
      implementation(project(":artemis-tx"))
      implementation(project(":artemis-nft-compat"))
      implementation(project(":artemis-candy-machine"))
      implementation(project(":artemis-candy-machine-presets"))
      implementation(project(":artemis-cnft"))
      implementation(project(":artemis-mplcore"))
      implementation(project(":artemis-wallet"))
      implementation(project(":artemis-tx-presets"))
      implementation(libs.kotlinx.serialization.json)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

android {
    namespace = "com.selenus.artemis.metaplex"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
