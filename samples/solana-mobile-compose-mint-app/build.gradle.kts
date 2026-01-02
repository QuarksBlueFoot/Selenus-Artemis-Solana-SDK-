plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.selenus.artemis.samples.mint"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.selenus.artemis.samples.mint"
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
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-core"))
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-wallet-mwa-android"))
  implementation(project(":artemis-candy-machine-presets"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.core:core-ktx:1.13.1")
}
