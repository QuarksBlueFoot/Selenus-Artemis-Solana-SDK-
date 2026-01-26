/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * artemis-anchor - Type-safe Anchor program client from IDL
 * ORIGINAL IMPLEMENTATION - No other Kotlin/Android SDK provides this.
 */
plugins {
    id("artemis.kotlin-library-conventions")
}

dependencies {
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-discriminators"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okio)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}
