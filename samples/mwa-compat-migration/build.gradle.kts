plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.selenus.artemis.samples.mwa"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.selenus.artemis.samples.mwa"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }
}

dependencies {
  implementation(project(":artemis-core"))
  implementation(project(":artemis-wallet-mwa-android"))
  implementation(project(":artemis-mwa-compat"))
  implementation(project(":artemis-web3-solana-compat"))
  implementation(project(":artemis-rpc-core-compat"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}