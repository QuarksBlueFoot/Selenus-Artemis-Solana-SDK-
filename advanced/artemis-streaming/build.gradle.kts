plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.selenus.artemis.streaming.MwaReflectorMainKt")
}

dependencies {
    implementation(project(":artemis-core"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-ws"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp)
}
