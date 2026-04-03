/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * artemis-anchor - Type-safe Anchor program client from IDL
 * ORIGINAL IMPLEMENTATION - No other Kotlin/Android SDK provides this.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-core"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-discriminators"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
        }
    }
}

android {
    namespace = "com.selenus.artemis.anchor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
