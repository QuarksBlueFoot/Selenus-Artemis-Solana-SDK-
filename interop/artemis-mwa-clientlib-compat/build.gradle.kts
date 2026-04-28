// Drop-in source compatibility for `com.solana.mobilewalletadapter:clientlib`.
// Pinned upstream surface: 1.4.3 (github.com/solana-mobile/mobile-wallet-adapter
// tag `v1.4.3`).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extra["upstream.version"] = "1.4.3"
extra["upstream.repo"] = "https://github.com/solana-mobile/mobile-wallet-adapter"
extra["upstream.artifact"] = "com.solana.mobilewalletadapter:clientlib"

android {
    namespace = "com.selenus.artemis.interop.mwaclientlib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = 26 }

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
            // `android.net.Uri` is resolved by the stub `android.jar` on the
            // unit-test classpath. Compat parity tests drive Uri through
            // the association flow; returning default values keeps the
            // stub navigable without Robolectric.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":artemis-core"))
    api(project(":artemis-wallet"))
    api(project(":artemis-wallet-mwa-android"))
    api(project(":artemis-mwa-common-compat"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
