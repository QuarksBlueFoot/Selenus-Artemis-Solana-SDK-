plugins {
    kotlin("jvm")
    application
}

group = "com.selenus.artemis.samples"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Artemis SDK modules
    implementation(project(":artemis-core"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-programs"))
    implementation(project(":artemis-privacy"))
    implementation(project(":artemis-gaming"))
    implementation(project(":artemis-metaplex"))
    implementation(project(":artemis-token2022"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

application {
    mainClass.set("com.selenus.artemis.samples.ArtemisIntegrationTest")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
