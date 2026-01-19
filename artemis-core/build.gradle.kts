plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            // Coroutines for Flow-based reactive APIs and concurrency
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}
