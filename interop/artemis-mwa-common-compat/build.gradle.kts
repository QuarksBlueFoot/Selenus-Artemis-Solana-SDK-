// Drop-in source compatibility for `com.solana.mobilewalletadapter:common`.
// Pinned upstream surface: 1.4.3 (github.com/solana-mobile/mobile-wallet-adapter
// tag `v1.4.3`). Carries the protocol contract, association contract, and
// shared SIWS / NotifyOnComplete future helpers.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extra["upstream.version"] = "1.4.3"
extra["upstream.repo"] = "https://github.com/solana-mobile/mobile-wallet-adapter"
extra["upstream.artifact"] = "com.solana.mobilewalletadapter:common"

android {
    namespace = "com.selenus.artemis.interop.mwacommon"
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
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":artemis-core"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
}
