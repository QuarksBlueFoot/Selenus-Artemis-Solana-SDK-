plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.selenus.artemis.wallet.mwa.walletlib"
  compileSdk = 35

  defaultConfig {
    // Brief targeted minSdk 23 but the depended-upon
    // :artemis-wallet-mwa-android module is minSdk 26 (because the
    // protocol layer uses java.util.Base64 / java.util.Optional /
    // android.util.Base64 features available from API 26). The
    // walletlib follows that floor so manifest-merge succeeds.
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
      // `android.net.Uri` resolves through the stub `android.jar` on the
      // unit-test classpath. Returning default values keeps the parser
      // and request-type tests off Robolectric while still exercising
      // every real code path.
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  // Reuse crypto + AES-128-GCM transport primitives from the dApp-side
  // module instead of duplicating P-256 / HKDF / Base64Url. The wallet
  // role is the inverse responder over the same wire protocol.
  implementation(project(":artemis-wallet-mwa-android"))
  // ComponentActivity for the wallet-side scheme entry point.
  implementation(libs.androidx.activity.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  testImplementation("junit:junit:4.13.2")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
}
