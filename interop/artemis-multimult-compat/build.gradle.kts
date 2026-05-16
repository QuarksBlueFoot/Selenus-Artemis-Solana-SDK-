// Drop-in source compatibility for `io.github.funkatronics:multimult`.
// Pinned upstream surface: multimult 0.2.6 (github.com/Funkatronics/multimult).
// This module covers the Base58 encoder/decoder surface used by current
// Solana Mobile Kotlin examples and delegates to Artemis' Solana-optimized
// Base58 implementation rather than carrying a second encoder.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "0.2.6"
extra["upstream.repo"] = "https://github.com/Funkatronics/multimult"
extra["upstream.artifact"] = "io.github.funkatronics:multimult"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":artemis-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.multimult"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}