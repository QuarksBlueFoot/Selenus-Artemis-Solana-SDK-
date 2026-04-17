plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

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
}

dependencies {
    api(project(":artemis-core"))
    api(project(":artemis-wallet"))
    api(project(":artemis-wallet-mwa-android"))
    api(project(":artemis-mwa-common-compat"))
    implementation(libs.kotlinx.coroutines.android)
}
