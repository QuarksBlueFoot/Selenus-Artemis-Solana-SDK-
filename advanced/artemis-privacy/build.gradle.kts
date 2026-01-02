plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
}

dependencies {
    implementation(project(":artemis-core"))
    
    // Cryptography
    implementation(libs.bouncycastle)
    
    // Networking (LightProtocolClient)
    implementation(libs.okhttp)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
