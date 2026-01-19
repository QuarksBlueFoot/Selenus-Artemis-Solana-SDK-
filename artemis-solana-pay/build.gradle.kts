plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":artemis-runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
