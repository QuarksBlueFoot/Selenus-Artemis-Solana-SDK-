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

  testOptions {
    unitTests {
      // `android.net.Uri` and related framework types are resolved by the
      // stub `android.jar` on the unit-test classpath. Returning default
      // values lets the MwaWalletAdapter construct against an Activity
      // stub without Robolectric; behavior tests that drive every MWA 2.0
      // verb run in this plain unit-test ring.
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  implementation(project(":artemis-wallet"))
  implementation(project(":artemis-rpc"))
  implementation(project(":artemis-vtx"))
  implementation(project(":artemis-ws"))
  implementation(project(":artemis-cnft"))
  implementation(project(":artemis-core"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.security.crypto)
  // Artemis native Mobile Wallet Adapter client. No dependency on Solana Mobile clientlib.
  // No OkHttp dependency needed (native socket implementation).
  implementation(libs.kotlinx.serialization.json)
  // BouncyCastle for X25519 Diffie-Hellman in MwaSessionCrypto. Transport
  // layer concern — deliberately separated from the Seed Vault custody
  // module so the two stay independent.
  implementation(libs.bouncycastle)

  testImplementation("junit:junit:4.13.2")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
}
