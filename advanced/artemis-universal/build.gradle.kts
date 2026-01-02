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
