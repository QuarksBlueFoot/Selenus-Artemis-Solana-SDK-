plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    implementation(project(":artemis-core"))
    implementation(libs.kotlinx.coroutines.core)
}
