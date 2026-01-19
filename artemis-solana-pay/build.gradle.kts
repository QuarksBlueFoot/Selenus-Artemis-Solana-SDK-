plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":artemis-programs"))
    testImplementation(project(":artemis-rpc"))
    
    implementation(kotlin("stdlib"))
    implementation(project(":artemis-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
