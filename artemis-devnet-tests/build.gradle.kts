plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "xyz.selenus"
version = project.findProperty("version") as String? ?: "2.0.0"

dependencies {
    // Core modules
    implementation(project(":artemis-core"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-wallet"))
    
    // New revolutionary modules
    implementation(project(":artemis-anchor"))
    implementation(project(":artemis-jupiter"))
    implementation(project(":artemis-actions"))
    implementation(project(":artemis-universal"))
    implementation(project(":artemis-nlp"))
    implementation(project(":artemis-streaming"))
    
    // Supporting modules
    implementation(project(":artemis-compute"))
    implementation(project(":artemis-ws"))
    implementation(project(":artemis-presets"))
    
    // Dependencies
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    
    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(17)
}
