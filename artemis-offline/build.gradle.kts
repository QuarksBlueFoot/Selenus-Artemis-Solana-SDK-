/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Internal dependencies
    api(project(":artemis-core"))
    api(project(":artemis-tx"))
    api(project(":artemis-rpc"))
    api(project(":artemis-intent"))
    
    // Coroutines
    api(libs.kotlinx.coroutines.core)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
