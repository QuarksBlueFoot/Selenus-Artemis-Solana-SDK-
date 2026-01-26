/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * artemis-jupiter - Jupiter DEX aggregator integration
 * ORIGINAL IMPLEMENTATION - First-class Jupiter support for Kotlin/Android.
 */
plugins {
    id("artemis.kotlin-library-conventions")
}

dependencies {
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-rpc"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okio)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}
