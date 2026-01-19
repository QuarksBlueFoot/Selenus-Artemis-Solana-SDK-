plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    implementation(project(":artemis-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
