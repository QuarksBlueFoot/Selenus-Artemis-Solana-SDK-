/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Integration tests for Artemis SDK on devnet.
 */
plugins {
    kotlin("jvm")
}

dependencies {
    // All Artemis modules (JVM only)
    testImplementation(project(":artemis-core"))
    testImplementation(project(":artemis-tx"))
    testImplementation(project(":artemis-rpc"))
    testImplementation(project(":artemis-wallet"))
    testImplementation(project(":artemis-anchor"))
    testImplementation(project(":artemis-jupiter"))
    testImplementation(project(":artemis-actions"))
    testImplementation(project(":artemis-compute"))
    testImplementation(project(":artemis-ws"))
    // Note: artemis-seed-vault excluded (Android-only module)
    
    // Test framework
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnitPlatform()
}
