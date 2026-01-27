/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * artemis-anchor - Type-safe Anchor program client from IDL
 * ORIGINAL IMPLEMENTATION - No other Kotlin/Android SDK provides this.
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":artemis-core"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-discriminators"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}
