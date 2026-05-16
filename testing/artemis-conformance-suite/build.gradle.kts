plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.selenus.artemis.conformance"
  compileSdk = 35

  defaultConfig {
    minSdk = 26
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

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  testImplementation(project(":artemis-core"))
  testImplementation(project(":artemis-mwa-compat"))
  testImplementation(project(":artemis-seedvault-compat"))
  testImplementation(project(":artemis-sol4k-compat"))
  testImplementation(project(":artemis-solana-kmp-compat"))
  testImplementation(project(":artemis-web3-solana-compat"))
  testImplementation(project(":artemis-rpc-core-compat"))
  testImplementation(project(":artemis-metaplex-android-compat"))
  testImplementation(project(":artemis-multimult-compat"))

  testImplementation("junit:junit:4.13.2")
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.json)
}