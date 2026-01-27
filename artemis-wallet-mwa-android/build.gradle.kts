plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.selenus.artemis.wallet.mwa"
  compileSdk = 35

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")
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
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-core"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.datastore)
  // Artemis native Mobile Wallet Adapter client. No dependency on Solana Mobile clientlib.
  // No OkHttp dependency needed (native socket implementation).
  implementation(libs.kotlinx.serialization.json)
}
