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

  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-runtime"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("androidx.datastore:datastore-preferences:1.1.1")
  // Artemis native Mobile Wallet Adapter client. No dependency on Solana Mobile clientlib.
  // No OkHttp dependency needed (native socket implementation).
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
