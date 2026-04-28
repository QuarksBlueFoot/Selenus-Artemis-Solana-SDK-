// Drop-in source compatibility for `com.solanamobile:seedvault-wallet-sdk`.
// Pinned upstream surface: 0.4.0 (github.com/solana-mobile/seed-vault-sdk tag `v0.4.0`).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extra["upstream.version"] = "0.4.0"
extra["upstream.repo"] = "https://github.com/solana-mobile/seed-vault-sdk"
extra["upstream.artifact"] = "com.solanamobile:seedvault-wallet-sdk"

android {
    namespace = "com.selenus.artemis.interop.seedvault"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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
    api(project(":artemis-seed-vault"))
    api(project(":artemis-core"))
    implementation(libs.androidx.annotation)
}
